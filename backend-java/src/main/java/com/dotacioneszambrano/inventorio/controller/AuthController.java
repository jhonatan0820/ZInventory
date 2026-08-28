package com.dotacioneszambrano.inventorio.controller;

import com.dotacioneszambrano.inventorio.service.AuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // POST /Login  { usuario, password }
    @PostMapping("/Login")
    public Map<String, Object> login(@RequestBody Map<String, String> body, HttpSession session) {
        String usuario = body.getOrDefault("usuario", "").trim();
        String password = body.getOrDefault("password", "").trim();
        Long idUsuario = authService.login(usuario, password);
        session.setAttribute("idUsuario", idUsuario);
        session.setAttribute("usuario", usuario);
        return Map.of("ok", true);
    }

    // GET /CheckSession
    @GetMapping("/CheckSession")
    public ResponseEntity<Map<String, Object>> checkSession(HttpSession session) {
        Object idUsuario = session.getAttribute("idUsuario");
        if (idUsuario == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false));
        }
        Object usuario = session.getAttribute("usuario");
        return ResponseEntity.ok(Map.of("ok", true, "usuario", usuario == null ? "" : usuario));
    }

    // POST /Logout
    @PostMapping("/Logout")
    public Map<String, Object> logout(HttpSession session) {
        session.invalidate();
        return Map.of("ok", true);
    }

    // POST /RecuperarPassword  { email }
    @PostMapping("/RecuperarPassword")
    public Map<String, Object> recuperar(@RequestBody Map<String, String> body) {
        authService.solicitarReset(body.getOrDefault("email", "").trim());
        return Map.of("ok", true);
    }

    // POST /ResetPassword  { token, password }
    @PostMapping("/ResetPassword")
    public Map<String, Object> reset(@RequestBody Map<String, String> body) {
        authService.resetPassword(body.get("token"), body.get("password"));
        return Map.of("ok", true);
    }
}
