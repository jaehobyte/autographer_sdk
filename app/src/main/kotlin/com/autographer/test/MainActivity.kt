package com.autographer.test

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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
import com.autographer.test.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var agentClient: AgentClient? = null
    private var currentHandle: RequestHandle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rgProvider.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbOpenAi -> binding.etModel.hint = "Model (e.g. gpt-4o-mini)"
                R.id.rbGemini -> binding.etModel.hint = "Model (e.g. gemini-2.0-flash)"
            }
        }

        binding.btnSend.setOnClickListener { sendRequest() }
        binding.btnCancel.setOnClickListener { cancelRequest() }
    }

    private fun buildClient(apiKey: String, model: String): AgentClient {
        val provider = when (binding.rgProvider.checkedRadioButtonId) {
            R.id.rbGemini -> GeminiProvider(apiKey = apiKey)
            else -> OpenAiProvider(apiKey = apiKey)
        }
        return AgentClient.Builder()
            .provider(provider)
            .llmConfig(LlmConfig(model = model))
            .systemPrompt("You are a helpful assistant.")
            .enableLogging(true)
            .context(this)
            .build()
    }

    private fun sendRequest() {
        val apiKey = binding.etApiKey.text.toString().trim()
        val prompt = binding.etPrompt.text.toString().trim()
        val modelInput = binding.etModel.text.toString().trim()
        val model = if (modelInput.isEmpty()) {
            if (binding.rgProvider.checkedRadioButtonId == R.id.rbGemini) "gemini-2.0-flash"
            else "gpt-4o-mini"
        } else {
            modelInput
        }

        if (apiKey.isEmpty()) {
            binding.tvResponse.text = "Error: API key is required."
            return
        }
        if (prompt.isEmpty()) {
            binding.tvResponse.text = "Error: Prompt is required."
            return
        }

        agentClient?.destroy()
        agentClient = buildClient(apiKey, model)

        binding.tvResponse.text = "Sending..."
        binding.btnSend.isEnabled = false
        binding.btnCancel.isEnabled = true

        val callback = object : AgentCallback {
            private var responseStarted = false

            override fun onStateChanged(state: AgentState) {
                runOnUiThread {
                    if (!responseStarted) {
                        binding.tvResponse.text = "[$state]"
                    }
                }
            }

            override fun onPartialResponse(chunk: String) {
                runOnUiThread {
                    if (!responseStarted) {
                        responseStarted = true
                        binding.tvResponse.text = chunk
                    } else {
                        binding.tvResponse.append(chunk)
                    }
                }
            }

            override fun onComplete(response: AgentResponse) {
                runOnUiThread {
                    val text = response.message.parts
                        .filterIsInstance<MessagePart.Text>()
                        .joinToString("") { it.text }
                    binding.tvResponse.text = text
                    resetButtons()
                }
            }

            override fun onError(error: AgentException) {
                runOnUiThread {
                    binding.tvResponse.text = "Error: ${error.message}"
                    resetButtons()
                }
            }

            override fun onCancelled() {
                runOnUiThread {
                    binding.tvResponse.append("\n[Cancelled]")
                    resetButtons()
                }
            }
        }

        currentHandle = agentClient?.request(prompt, callback)
    }

    private fun cancelRequest() {
        currentHandle?.cancel()
    }

    private fun resetButtons() {
        binding.btnSend.isEnabled = true
        binding.btnCancel.isEnabled = false
    }

    override fun onDestroy() {
        super.onDestroy()
        agentClient?.destroy()
    }
}
