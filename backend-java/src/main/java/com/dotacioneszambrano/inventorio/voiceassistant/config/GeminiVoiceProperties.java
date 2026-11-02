package com.dotacioneszambrano.inventorio.voiceassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "voice-assistant.gemini")
public record GeminiVoiceProperties(
        String apiKey,
        String baseUrl,
        String model,
        String liveModel
) {
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
