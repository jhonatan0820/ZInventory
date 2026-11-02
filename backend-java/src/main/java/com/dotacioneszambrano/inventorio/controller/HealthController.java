package com.dotacioneszambrano.inventorio.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.dotacioneszambrano.inventorio.repository.DatabaseRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);
    private final DatabaseRepository databaseRepository;

    public HealthController(DatabaseRepository databaseRepository) {
        this.databaseRepository = databaseRepository;
    }

    // Kept separate from "/" so the static index page remains the welcome page.
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "message", "API funcionando correctamente");
    }

    // Lightweight uptime probe.
    @RequestMapping(value = "/ping", method = {RequestMethod.GET, RequestMethod.HEAD})
    public String ping() {
        return "OK";
    }

    // Wakes the database connection when the provider idles it.
    @GetMapping("/activador")
    public Map<String, Object> activador() {
        try {
            databaseRepository.ping();
        } catch (Exception e) {
            log.warn("DB dormida o error: {}", e.getMessage());
        }
        return Map.of("ok", true);
    }
}
