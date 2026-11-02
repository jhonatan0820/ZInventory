package com.dotacioneszambrano.inventorio.service;

import com.dotacioneszambrano.inventorio.entity.PasswordReset;
import com.dotacioneszambrano.inventorio.entity.Usuario;
import com.dotacioneszambrano.inventorio.exception.ApiException;
import com.dotacioneszambrano.inventorio.repository.PasswordResetRepository;
import com.dotacioneszambrano.inventorio.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordResetRepository passwordResetRepository;
    private final PasswordHashService passwordHashService;
    private final MailService mailService;

    public AuthService(UsuarioRepository usuarioRepository,
                       PasswordResetRepository passwordResetRepository,
                       PasswordHashService passwordHashService,
                       MailService mailService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordResetRepository = passwordResetRepository;
        this.passwordHashService = passwordHashService;
        this.mailService = mailService;
    }

    @Transactional
    public Long login(String usuario, String password) {
        if (usuario == null || usuario.isBlank() || password == null || password.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Completa todos los campos");
        }
        Usuario account = usuarioRepository.findByUsuario(usuario)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas"));
        if (!passwordHashService.matches(password, account.getPassword())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
        }
        return account.getIdUsuario();
    }

    @Transactional
    public void solicitarReset(String email) {
        if (email == null || email.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Email requerido");
        }
        Usuario account = usuarioRepository.findByEmailAndIdEstado(email, 1).orElse(null);
        if (account == null) return;

        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5);
        PasswordReset reset = passwordResetRepository.findFirstByIdUsuario(account.getIdUsuario())
                .orElseGet(() -> new PasswordReset(account.getIdUsuario(), token, expiresAt, 4));
        reset.setToken(token);
        reset.setExpira(expiresAt);
        reset.setIdEstado(4);
        passwordResetRepository.save(reset);

        try {
            mailService.enviarCorreoRecuperacion(email, token);
        } catch (Exception mailError) {
            log.error("ERROR enviando correo: {}", mailError.getMessage());
        }
    }

    @Transactional
    public void resetPassword(String token, String password) {
        if (token == null || token.isBlank() || password == null || password.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Datos incompletos");
        }
        PasswordReset reset = passwordResetRepository.findValidToken(token)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST, "Token inválido o expirado"));
        Usuario account = usuarioRepository.findById(reset.getIdUsuario())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST, "Token inválido o expirado"));
        account.setPassword(passwordHashService.encode(password));
        usuarioRepository.save(account);
        reset.setIdEstado(3);
        passwordResetRepository.save(reset);
    }
}
