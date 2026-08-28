package com.dotacioneszambrano.inventorio.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);
    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Equivalente al @app.route("/") del Python (movido a /health para no
    // colisionar con el index.html servido desde /static/).
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "message", "API funcionando correctamente");
    }

    // Equivalente al @app.route("/ping")
    @RequestMapping(value = "/ping", method = {RequestMethod.GET, RequestMethod.HEAD})
    public String ping() {
        return "OK";
    }

    // Equivalente al @app.route("/activador") - despierta la BD
    @GetMapping("/activador")
    public Map<String, Object> activador() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
        } catch (Exception e) {
            log.warn("DB dormida o error: {}", e.getMessage());
        }
        return Map.of("ok", true);
    }
}
