package com.dotacioneszambrano.inventorio.service;

import com.dotacioneszambrano.inventorio.config.ResendProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Envio de correos via API HTTP de Resend, equivalente al enviar_correo() del backend Python.
 * Nunca registra la API key ni el token completo en logs.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final String RESEND_URL = "https://api.resend.com/emails";

    private final RestClient restClient;
    private final ResendProperties resendProperties;
    private final org.springframework.core.env.Environment env;

    public MailService(RestClient restClient, ResendProperties resendProperties,
                       org.springframework.core.env.Environment env) {
        this.restClient = restClient;
        this.resendProperties = resendProperties;
        this.env = env;
    }

    public void enviarCorreoRecuperacion(String email, String token) {
        String baseUrl = env.getProperty("app.reset-base-url", "http://localhost:8081");
        String html = """
                <h2>Recuperación de contraseña</h2>
                <p>Haz clic en el siguiente enlace para cambiar su contraseña:</p>
                <a href="%s/reset.html?token=%s">
                    Recuperar contraseña
                </a>
                <p>Este enlace expira en 5 minutos.</p>
                """.formatted(baseUrl, token);

        Map<String, Object> payload = Map.of(
                "from", "Inventario <onboarding@resend.dev>",
                "to", List.of(email),
                "subject", "Recuperar contraseña Inventario Dotaciones Zambrano",
                "html", html
        );

        String apiKey = resendProperties.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("RESEND_API_KEY no configurada");
        }

        try {
            restClient.post()
                    .uri(RESEND_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Error enviando correo a {}: {}", email, e.getMessage());
            throw new RuntimeException("Error enviando correo", e);
        }
    }
}
