package com.agam.pixelhub.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

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
}
