package com.dotacioneszambrano.inventorio.service;

import com.dotacioneszambrano.inventorio.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder encoder;
    private final MailService mailService;

    public AuthService(JdbcTemplate jdbc, BCryptPasswordEncoder encoder, MailService mailService) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.mailService = mailService;
    }

    /** Devuelve el idUsuario si las credenciales son validas. */
    public Long login(String usuario, String password) {
        if (usuario == null || usuario.isBlank() || password == null || password.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Completa todos los campos");
        }
        try {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT idUsuario, password FROM usuarios WHERE usuario = ?",
                    usuario
            );
            String hash = (String) row.get("password");
            // flask-bcrypt genera hashes con prefijo $2b$. BCryptPasswordEncoder los acepta.
            if (!encoder.matches(password, hash)) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
            }
            return ((Number) row.get("idUsuario")).longValue();
        } catch (EmptyResultDataAccessException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
        }
    }

    /** Solicita reset. No revela si el email existe (comportamiento identico al Python). */
    @Transactional
    public void solicitarReset(String email) {
        if (email == null || email.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Email requerido");
        }
        Long idUsuario;
        try {
            idUsuario = jdbc.queryForObject(
                    "SELECT idUsuario FROM usuarios WHERE email = ? AND id_estado = 1",
                    Long.class, email
            );
        } catch (EmptyResultDataAccessException e) {
            // No revelamos si existe
            return;
        }
        if (idUsuario == null) return;

        String token = UUID.randomUUID().toString();
        // Python usa datetime.utcnow(). Usamos UTC para que coincida con NOW() del servidor MySQL en UTC.
        LocalDateTime expira = LocalDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(5);

        jdbc.update("""
                INSERT INTO password_resets (idUsuario, token, expira, id_estado)
                VALUES (?, ?, ?, 4)
                ON DUPLICATE KEY UPDATE
                    token = VALUES(token),
                    expira = VALUES(expira),
                    id_estado = 4
                """, idUsuario, token, expira);

        try {
            mailService.enviarCorreoRecuperacion(email, token);
        } catch (Exception mailError) {
            log.error("ERROR enviando correo: {}", mailError.getMessage());
            // Igual que Python: se ignora el error de envio para no romper el flujo
        }
    }

    @Transactional
    public void resetPassword(String token, String password) {
        if (token == null || token.isBlank() || password == null || password.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Datos incompletos");
        }
        Map<String, Object> reset;
        try {
            reset = jdbc.queryForMap("""
                    SELECT idPasswordResets, idUsuario FROM password_resets
                    WHERE token = ? AND id_estado = 4 AND expira > NOW()
                    """, token);
        } catch (EmptyResultDataAccessException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Token inválido o expirado");
        }

        String hashed = encoder.encode(password);
        jdbc.update("UPDATE usuarios SET password = ? WHERE idUsuario = ?",
                hashed, reset.get("idUsuario"));
        jdbc.update("UPDATE password_resets SET id_estado = 3 WHERE idPasswordResets = ?",
                reset.get("idPasswordResets"));
    }
}
