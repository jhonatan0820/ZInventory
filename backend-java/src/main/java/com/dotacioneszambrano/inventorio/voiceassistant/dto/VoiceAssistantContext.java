package com.dotacioneszambrano.inventorio.voiceassistant.dto;

import java.util.Map;

public record VoiceAssistantContext(
        String transcript,
        Map<String, Object> draft,
        Map<String, Object> catalogs
) {}
