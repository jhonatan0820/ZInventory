package com.dotacioneszambrano.inventorio.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "password_resets")
public class PasswordReset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idPasswordResets")
    private Long idPasswordResets;

    @Column(name = "idUsuario")
    private Long idUsuario;

    @Column(name = "token")
    private String token;

    @Column(name = "expira")
    private LocalDateTime expira;

    @Column(name = "id_estado")
    private Integer idEstado;

    protected PasswordReset() {}

    public PasswordReset(Long idUsuario, String token, LocalDateTime expira, Integer idEstado) {
        this.idUsuario = idUsuario;
        this.token = token;
        this.expira = expira;
        this.idEstado = idEstado;
    }

    public Long getIdPasswordResets() { return idPasswordResets; }
    public Long getIdUsuario() { return idUsuario; }
    public String getToken() { return token; }
    public LocalDateTime getExpira() { return expira; }
    public Integer getIdEstado() { return idEstado; }
    public void setToken(String token) { this.token = token; }
    public void setExpira(LocalDateTime expira) { this.expira = expira; }
    public void setIdEstado(Integer idEstado) { this.idEstado = idEstado; }
}
