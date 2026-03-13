package com.autographer.test

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.autographer.agent.AgentCallback
import com.autographer.agent.AgentClient
import com.autographer.agent.AgentResponse
import com.autographer.agent.RequestHandle
import com.autographer.agent.core.AgentException
import com.autographer.agent.core.AgentState
import com.autographer.agent.llm.LlmConfig
import com.autographer.agent.llm.gemini.GeminiProvider
import com.autographer.agent.llm.openai.OpenAiProvider
import com.autographer.agent.model.MessagePart
import com.autographer.agent.model.ToolCall
import com.autographer.agent.tool.Tool
import com.autographer.agent.tool.ToolResult
import com.autographer.agent.tool.ToolSchema
import com.autographer.test.databinding.ActivityMainBinding
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.pow

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var agentClient: AgentClient? = null
    private var currentHandle: RequestHandle? = null
    private var currentSessionId: String? = null

    // Tool instances
    private val weatherTool = WeatherTool()
    private val calculatorTool = CalculatorTool()
    private val dateTimeTool = DateTimeTool()
    private val unitConvertTool = UnitConvertTool()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTabs()
        setupProviderToggle()
        setupChatTab()
        setupToolsTab()
        setupHistoryTab()
    }

    // ===== Tab Navigation =====

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                binding.tabChat.visibility = if (tab.position == 0) View.VISIBLE else View.GONE
                binding.tabTools.visibility = if (tab.position == 1) View.VISIBLE else View.GONE
                binding.tabHistory.visibility = if (tab.position == 2) View.VISIBLE else View.GONE
                if (tab.position == 2) refreshSessions()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    // ===== Provider Config =====

    private fun setupProviderToggle() {
        binding.rgProvider.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbOpenAi -> binding.etModel.hint = "e.g. gpt-4o-mini"
                R.id.rbGemini -> binding.etModel.hint = "e.g. gemini-2.0-flash"
            }
        }
        // trigger initial hint
        binding.etModel.hint = "e.g. gemini-2.0-flash"
    }

    private fun getOrBuildClient(): AgentClient {
        agentClient?.let { return it }

        val apiKey = binding.etApiKey.text.toString().trim()
        if (apiKey.isEmpty()) throw IllegalStateException("API key is required")

        val modelInput = binding.etModel.text.toString().trim()
        val isGemini = binding.rgProvider.checkedRadioButtonId == R.id.rbGemini
        val model = modelInput.ifEmpty {
            if (isGemini) "gemini-2.0-flash" else "gpt-4o-mini"
        }

        val provider = if (isGemini) GeminiProvider(apiKey = apiKey)
                       else OpenAiProvider(apiKey = apiKey)

        val selectedTools = getSelectedTools()

        val client = AgentClient.Builder()
            .provider(provider)
            .llmConfig(LlmConfig(model = model))
            .tools(selectedTools)
            .systemPrompt("You are a helpful assistant. When a tool is available and relevant, use it to answer questions accurately.")
            .maxIterations(10)
            .enableLogging(true)
            .context(this)
            .build()

        agentClient = client
        return client
    }

    private fun rebuildClient() {
        agentClient?.destroy()
        agentClient = null
        currentSessionId = null
    }

    // ===== Chat Tab =====

    private fun setupChatTab() {
        binding.btnSend.setOnClickListener { sendMessage() }
        binding.btnCancel.setOnClickListener { cancelRequest() }
    }

    private fun sendMessage() {
        val prompt = binding.etPrompt.text.toString().trim()
        if (prompt.isEmpty()) return

        val client: AgentClient
        try {
            client = getOrBuildClient()
        } catch (e: Exception) {
            appendResponse("Error: ${e.message}\n")
            return
        }

        binding.etPrompt.text?.clear()
        appendResponse("\n--- You ---\n$prompt\n\n--- Assistant ---\n")
        binding.tvState.text = "State: Sending..."
        binding.btnSend.isEnabled = false
        binding.btnCancel.isEnabled = true
        binding.tvToolLog.visibility = View.GONE

        val callback = object : AgentCallback {
            private var responseStarted = false

            override fun onStateChanged(state: AgentState) {
                runOnUiThread {
                    binding.tvState.text = "State: $state"
                }
            }

            override fun onPartialResponse(chunk: String) {
                runOnUiThread {
                    responseStarted = true
                    appendResponse(chunk)
                }
            }

            override fun onToolCall(toolCall: ToolCall) {
                runOnUiThread {
                    binding.tvToolLog.visibility = View.VISIBLE
                    binding.tvToolLog.append("Tool: ${toolCall.name}(${formatArgs(toolCall.arguments)})\n")
                }
            }

            override fun onToolResult(toolCall: ToolCall, result: ToolResult) {
                runOnUiThread {
                    val output = when (result) {
                        is ToolResult.Success -> result.output
                        is ToolResult.Error -> "ERROR: ${result.message}"
                    }
                    binding.tvToolLog.append("  -> $output\n")
                }
            }

            override fun onComplete(response: AgentResponse) {
                runOnUiThread {
                    if (!responseStarted) {
                        val text = response.message.parts
                            .filterIsInstance<MessagePart.Text>()
                            .joinToString("") { it.text }
                        appendResponse(text)
                    }

                    currentSessionId = response.sessionId
                    val usage = response.usage
                    if (usage != null) {
                        appendResponse("\n\n[tokens: ${usage.promptTokens}+${usage.completionTokens}=${usage.totalTokens}]")
                    }
                    if (response.toolCallHistory.isNotEmpty()) {
                        appendResponse("\n[tools used: ${response.toolCallHistory.joinToString { it.name }}]")
                    }

                    binding.tvState.text = "State: Completed (session: ${currentSessionId?.take(8)}...)"
                    resetButtons()
                }
            }

            override fun onError(error: AgentException) {
                runOnUiThread {
                    appendResponse("\n\nError: ${error.message}")
                    binding.tvState.text = "State: Failed"
                    resetButtons()
                }
            }

            override fun onCancelled() {
                runOnUiThread {
                    appendResponse("\n[Cancelled]")
                    binding.tvState.text = "State: Cancelled"
                    resetButtons()
                }
            }
        }

        currentHandle = client.request(prompt, callback)
    }

    private fun cancelRequest() {
        currentHandle?.cancel()
    }

    private fun appendResponse(text: String) {
        binding.tvResponse.append(text)
        binding.scrollResponse.post {
            binding.scrollResponse.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun resetButtons() {
        binding.btnSend.isEnabled = true
        binding.btnCancel.isEnabled = false
    }

    private fun formatArgs(args: Map<String, Any?>): String {
        return args.entries.joinToString(", ") { "${it.key}=${it.value}" }
    }

    // ===== Tools Tab =====

    private fun setupToolsTab() {
        updateToolDisplay()
        binding.btnApplyTools.setOnClickListener {
            rebuildClient()
            updateToolDisplay()
            binding.tabLayout.selectTab(binding.tabLayout.getTabAt(0))
        }
    }

    private fun getSelectedTools(): List<Tool> {
        val tools = mutableListOf<Tool>()
        if (binding.chipWeather.isChecked) tools.add(weatherTool)
        if (binding.chipCalculator.isChecked) tools.add(calculatorTool)
        if (binding.chipDateTime.isChecked) tools.add(dateTimeTool)
        if (binding.chipUnitConvert.isChecked) tools.add(unitConvertTool)
        return tools
    }

    private fun updateToolDisplay() {
        val tools = getSelectedTools()
        if (tools.isEmpty()) {
            binding.tvToolList.text = "No tools selected.\n\nSelect tools below and tap 'Apply' to register them."
        } else {
            val sb = StringBuilder()
            tools.forEach { tool ->
                sb.append("${tool.name}\n")
                sb.append("  ${tool.description}\n")
                sb.append("  params: ${tool.parameterSchema.parameters}\n")
                sb.append("  required: ${tool.parameterSchema.required}\n\n")
            }
            binding.tvToolList.text = sb.toString()
        }
    }

    // ===== History Tab =====

    private fun setupHistoryTab() {
        binding.btnNewSession.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val client = getOrBuildClient()
                    val sessionId = client.newSession()
                    currentSessionId = sessionId
                    binding.tvResponse.text = ""
                    binding.tvToolLog.visibility = View.GONE
                    refreshSessions()
                } catch (e: Exception) {
                    binding.tvSessionList.text = "Error: ${e.message}"
                }
            }
        }

        binding.btnRefreshSessions.setOnClickListener { refreshSessions() }

        binding.btnDeleteSession.setOnClickListener {
            val sessionId = currentSessionId ?: return@setOnClickListener
            lifecycleScope.launch {
                try {
                    agentClient?.deleteSession(sessionId)
                    currentSessionId = null
                    binding.tvResponse.text = ""
                    refreshSessions()
                } catch (e: Exception) {
                    binding.tvSessionList.text = "Error: ${e.message}"
                }
            }
        }
    }

    private fun refreshSessions() {
        binding.tvCurrentSession.text = "Current: ${currentSessionId?.take(8) ?: "(none)"}..."
        lifecycleScope.launch {
            try {
                val sessions = agentClient?.listSessions() ?: emptyList()
                if (sessions.isEmpty()) {
                    binding.tvSessionList.text = "No sessions found."
                    return@launch
                }
                val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                val sb = StringBuilder()
                sessions.forEach { meta ->
                    val marker = if (meta.id == currentSessionId) " <-- active" else ""
                    sb.append("${meta.id.take(8)}... | msgs: ${meta.messageCount}")
                    sb.append(" | ${dateFormat.format(Date(meta.updatedAt))}$marker\n")
                    meta.title?.let { sb.append("  \"${it}\"\n") }
                    sb.append("\n")
                }
                binding.tvSessionList.text = sb.toString()
            } catch (e: Exception) {
                binding.tvSessionList.text = "Error: ${e.message}"
            }
        }
    }

    // ===== Lifecycle =====

    override fun onDestroy() {
        super.onDestroy()
        agentClient?.destroy()
    }

    // ===== Sample Tools =====

    class WeatherTool : Tool {
        override val name = "get_weather"
        override val description = "Get current weather for a given city"
        override val parameterSchema = ToolSchema(
            name = "get_weather",
            description = "Get current weather for a given city",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "city" to mapOf("type" to "string", "description" to "City name"),
                    "unit" to mapOf(
                        "type" to "string",
                        "enum" to listOf("celsius", "fahrenheit"),
                        "description" to "Temperature unit"
                    )
                )
            ),
            required = listOf("city"),
        )

        override suspend fun execute(args: Map<String, Any?>): ToolResult {
            val city = args["city"] as? String ?: return ToolResult.Error("", "Missing city parameter")
            val unit = args["unit"] as? String ?: "celsius"
            val temp = (15..35).random()
            val conditions = listOf("sunny", "cloudy", "rainy", "partly cloudy", "windy").random()
            val humidity = (30..90).random()
            val tempDisplay = if (unit == "fahrenheit") "${temp * 9 / 5 + 32}°F" else "${temp}°C"
            return ToolResult.Success(
                callId = "",
                output = """{"city":"$city","temperature":"$tempDisplay","condition":"$conditions","humidity":"${humidity}%"}"""
            )
        }
    }

    class CalculatorTool : Tool {
        override val name = "calculate"
        override val description = "Perform a math calculation. Supports: add, subtract, multiply, divide, power, sqrt"
        override val parameterSchema = ToolSchema(
            name = "calculate",
            description = "Perform a math calculation",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "operation" to mapOf(
                        "type" to "string",
                        "enum" to listOf("add", "subtract", "multiply", "divide", "power", "sqrt"),
                        "description" to "The math operation to perform"
                    ),
                    "a" to mapOf("type" to "number", "description" to "First number"),
                    "b" to mapOf("type" to "number", "description" to "Second number (not needed for sqrt)")
                )
            ),
            required = listOf("operation", "a"),
        )

        override suspend fun execute(args: Map<String, Any?>): ToolResult {
            val op = args["operation"] as? String ?: return ToolResult.Error("", "Missing operation")
            val a = (args["a"] as? Number)?.toDouble() ?: return ToolResult.Error("", "Missing number a")
            val b = (args["b"] as? Number)?.toDouble()

            val result = when (op) {
                "add" -> a + (b ?: 0.0)
                "subtract" -> a - (b ?: 0.0)
                "multiply" -> a * (b ?: 1.0)
                "divide" -> {
                    if (b == null || b == 0.0) return ToolResult.Error("", "Division by zero")
                    a / b
                }
                "power" -> a.pow(b ?: 2.0)
                "sqrt" -> kotlin.math.sqrt(a)
                else -> return ToolResult.Error("", "Unknown operation: $op")
            }

            return ToolResult.Success(callId = "", output = """{"result":$result}""")
        }
    }

    class DateTimeTool : Tool {
        override val name = "get_datetime"
        override val description = "Get the current date and time, optionally in a specific timezone"
        override val parameterSchema = ToolSchema(
            name = "get_datetime",
            description = "Get the current date and time",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "timezone" to mapOf(
                        "type" to "string",
                        "description" to "Timezone (e.g. 'Asia/Seoul', 'America/New_York', 'UTC')"
                    ),
                    "format" to mapOf(
                        "type" to "string",
                        "description" to "Date format pattern (e.g. 'yyyy-MM-dd HH:mm:ss')"
                    )
                )
            ),
            required = emptyList(),
        )

        override suspend fun execute(args: Map<String, Any?>): ToolResult {
            val tz = args["timezone"] as? String
            val fmt = args["format"] as? String ?: "yyyy-MM-dd HH:mm:ss z"
            return try {
                val sdf = SimpleDateFormat(fmt, Locale.getDefault())
                if (tz != null) {
                    sdf.timeZone = java.util.TimeZone.getTimeZone(tz)
                }
                val now = sdf.format(Date())
                ToolResult.Success(callId = "", output = """{"datetime":"$now","timezone":"${sdf.timeZone.id}"}""")
            } catch (e: Exception) {
                ToolResult.Error("", "Failed: ${e.message}")
            }
        }
    }

    class UnitConvertTool : Tool {
        override val name = "convert_unit"
        override val description = "Convert between units: km/miles, kg/lbs, celsius/fahrenheit, meters/feet"
        override val parameterSchema = ToolSchema(
            name = "convert_unit",
            description = "Convert between units",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "value" to mapOf("type" to "number", "description" to "The value to convert"),
                    "from" to mapOf(
                        "type" to "string",
                        "enum" to listOf("km", "miles", "kg", "lbs", "celsius", "fahrenheit", "meters", "feet"),
                        "description" to "Source unit"
                    ),
                    "to" to mapOf(
                        "type" to "string",
                        "enum" to listOf("km", "miles", "kg", "lbs", "celsius", "fahrenheit", "meters", "feet"),
                        "description" to "Target unit"
                    )
                )
            ),
            required = listOf("value", "from", "to"),
        )

        override suspend fun execute(args: Map<String, Any?>): ToolResult {
            val value = (args["value"] as? Number)?.toDouble()
                ?: return ToolResult.Error("", "Missing value")
            val from = args["from"] as? String ?: return ToolResult.Error("", "Missing from unit")
            val to = args["to"] as? String ?: return ToolResult.Error("", "Missing to unit")

            val result = when ("$from->$to") {
                "km->miles" -> value * 0.621371
                "miles->km" -> value * 1.60934
                "kg->lbs" -> value * 2.20462
                "lbs->kg" -> value * 0.453592
                "celsius->fahrenheit" -> value * 9.0 / 5.0 + 32
                "fahrenheit->celsius" -> (value - 32) * 5.0 / 9.0
                "meters->feet" -> value * 3.28084
                "feet->meters" -> value * 0.3048
                else -> return ToolResult.Error("", "Unsupported conversion: $from -> $to")
            }

            return ToolResult.Success(
                callId = "",
                output = """{"result":${"%.4f".format(result)},"from":"$from","to":"$to"}"""
            )
        }
    }
}
