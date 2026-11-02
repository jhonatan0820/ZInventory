package com.dotacioneszambrano.inventorio.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @Column(name = "idUsuario")
    private Long idUsuario;

    @Column(name = "Usuario")
    private String usuario;

    @Column(name = "password", length = 255)
    private String password;

    @Column(name = "email")
    private String email;

    @Column(name = "id_estado")
    private Integer idEstado;

    protected Usuario() {}

    public Long getIdUsuario() { return idUsuario; }
    public String getUsuario() { return usuario; }
    public String getPassword() { return password; }
    public String getEmail() { return email; }
    public Integer getIdEstado() { return idEstado; }
    public void setPassword(String password) { this.password = password; }
}
