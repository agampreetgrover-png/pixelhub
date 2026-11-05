package com.agam.pixelhub.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class AssistantAiTest {
    @Test fun geminiRateLimitFallsBack() =
        assertTrue(AssistantAi.isLimitError(GeminiException("Resource has been exhausted (e.g. check quota).", 429)))

    @Test fun geminiOverloadFallsBack() =
        assertTrue(AssistantAi.isLimitError(GeminiException("The model is overloaded.", 503)))

    @Test fun quotaWordingWithoutCodeFallsBack() =
        assertTrue(AssistantAi.isLimitError(GeminiException("RESOURCE_EXHAUSTED: quota exceeded")))

    @Test fun grokRateLimitFallsBack() =
        assertTrue(AssistantAi.isLimitError(GrokException("Too many requests", 429)))

    @Test fun badKeyDoesNotFallBack() =
        assertFalse(AssistantAi.isLimitError(GeminiException("API key not valid. Please pass a valid API key.", 400)))

    @Test fun forbiddenDoesNotFallBack() =
        assertFalse(AssistantAi.isLimitError(GrokException("Incorrect API key provided", 401)))

    @Test fun offlineDoesNotFallBack() =
        assertFalse(AssistantAi.isLimitError(IOException("Unable to resolve host")))

    @Test fun timeoutFallsBack() =
        assertTrue(AssistantAi.isLimitError(SocketTimeoutException("timeout")))

    @Test fun overloadedGeminiTriesFlashLite() = assertEquals(
        listOf("gemini-flash-latest", "gemini-flash-lite-latest"),
        AssistantAi.geminiAttempts("gemini-flash-latest"),
    )

    @Test fun flashLiteIsNotTriedTwiceWhenAlreadyChosen() = assertEquals(
        listOf("gemini-flash-lite-latest"),
        AssistantAi.geminiAttempts(" gemini-flash-lite-latest "),
    )

    @Test fun groqKeyGoesToGroq() = assertEquals(ChatService.GROQ, ChatService.forKey(" gsk_abc"))

    @Test fun xaiKeyGoesToXai() = assertEquals(ChatService.XAI, ChatService.forKey("xai-abc"))

    @Test fun grokModelWithGroqKeyUsesGroqDefault() =
        assertEquals("openai/gpt-oss-120b", ChatService.resolveModel("gsk_abc", "grok-4.7"))

    @Test fun groqModelIsKept() =
        assertEquals("qwen/qwen3.8-27b", ChatService.resolveModel("gsk_abc", " qwen/qwen3.8-27b "))

    @Test fun speechModelsAreHiddenFromPicker() {
        assertFalse(ChatService.isChatModel("whisper-large-v3"))
        assertFalse(ChatService.isChatModel("meta-llama/llama-prompt-guard-2-22m"))
        assertTrue(ChatService.isChatModel("openai/gpt-oss-20b"))
    }

    @Test fun blankModelUsesDefault() =
        assertEquals("gemini-flash-latest", AssistantAi.geminiAttempts("").first())
}
