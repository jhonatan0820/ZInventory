package com.dotacioneszambrano.inventorio.voiceassistant.controller;

import com.dotacioneszambrano.inventorio.exception.ApiException;
import com.dotacioneszambrano.inventorio.voiceassistant.dto.VoiceAssistantContext;
import com.dotacioneszambrano.inventorio.voiceassistant.service.VoiceAssistantActionService;
import com.dotacioneszambrano.inventorio.voiceassistant.service.GeminiLiveSessionService;
import com.dotacioneszambrano.inventorio.voiceassistant.service.VoiceAssistantService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class VoiceAssistantController {

    private static final long MAX_AUDIO_BYTES = 20L * 1024 * 1024;
    private final VoiceAssistantService service;
    private final VoiceAssistantActionService actionService;
    private final GeminiLiveSessionService liveSessionService;
    private final ObjectMapper objectMapper;

    public VoiceAssistantController(VoiceAssistantService service,
                                    VoiceAssistantActionService actionService,
                                    GeminiLiveSessionService liveSessionService,
                                    ObjectMapper objectMapper) {
        this.service = service;
        this.actionService = actionService;
        this.liveSessionService = liveSessionService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/api/voice-assistant/live-session")
    public Map<String, Object> liveSession(HttpSession session) {
        requireAuthenticated(session);
        return liveSessionService.createSession();
    }

    @PostMapping("/api/voice-assistant/tools")
    public Map<String, Object> executeTool(@RequestBody JsonNode body, HttpSession session) {
        requireAuthenticated(session);
        if (body == null || !body.isObject() || !body.path("name").isTextual()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La llamada de herramienta no es válida");
        }
        String functionName = body.path("name").asText();
        VoiceAssistantActionService.TurnResult result =
                actionService.handleToolCall(functionName, body.path("arguments"), session);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("message", result.message());
        response.put("actionId", result.actionId());
        response.put("actionSummary", result.actionSummary());
        response.put("needsDuplicateDecision", result.needsDuplicateDecision());
        return response;
    }

    @PostMapping(value = "/api/voice-assistant/turn", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> turn(
            @RequestPart("audio") MultipartFile audio,
            @RequestPart("context") String contextJson,
            HttpSession session) {
        requireAuthenticated(session);
        actionService.ensureNoPendingAction(session);
        if (audio.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Graba un mensaje antes de enviarlo");
        }
        if (audio.getSize() > MAX_AUDIO_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "El audio no puede superar 20 MB");
        }
        String contentType = audio.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("audio/")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El archivo enviado no es un audio válido");
        }

        try {
            VoiceAssistantContext context = objectMapper.readValue(contextJson, VoiceAssistantContext.class);
            if (context == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "El contexto del asistente no es válido");
            }
            String audioType = contentType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
            JsonNode assistantResponse = service.processTurn(
                    audio.getBytes(), audioType, context.transcript(), context.draft(), context.catalogs());
            VoiceAssistantActionService.TurnResult action =
                    actionService.handleTurn(assistantResponse, session);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("transcript", assistantResponse.path("transcript").asText());
            result.put("message", action.message());
            result.put("draft", assistantResponse.path("draft"));
            result.put("readyToConfirm", action.actionId() != null);
            result.put("actionId", action.actionId());
            result.put("actionSummary", action.actionSummary());
            result.put("needsDuplicateDecision", action.needsDuplicateDecision());
            return result;
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No se pudo leer el audio o su contexto");
        }
    }

    @PostMapping("/api/voice-assistant/confirm")
    public Map<String, Object> confirm(@RequestBody Map<String, String> body, HttpSession session) {
        requireAuthenticated(session);
        String summary = actionService.confirm(body.get("actionId"), session);
        return Map.of("ok", true, "message", "Cambio aplicado: " + summary);
    }

    @PostMapping("/api/voice-assistant/cancel")
    public Map<String, Object> cancel(@RequestBody Map<String, String> body, HttpSession session) {
        requireAuthenticated(session);
        actionService.cancel(body.get("actionId"), session);
        return Map.of("ok", true, "message", "Cambio cancelado.");
    }

    @GetMapping("/api/voice-assistant/pending")
    public Map<String, Object> pending(HttpSession session) {
        requireAuthenticated(session);
        return actionService.pendingAction(session);
    }

    private static void requireAuthenticated(HttpSession session) {
        if (session.getAttribute("idUsuario") == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Inicia sesión para usar el asistente de voz");
        }
    }
}
