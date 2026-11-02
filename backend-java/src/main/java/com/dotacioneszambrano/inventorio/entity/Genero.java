package com.dotacioneszambrano.inventorio.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "generos")
public class Genero {

    @Id
    @Column(name = "id_genero")
    private Integer idGenero;

    @Column(name = "nombre")
    private String nombre;

    @Column(name = "id_estado")
    private Integer idEstado;

    protected Genero() {}

    public Integer getIdGenero() { return idGenero; }
    public String getNombre() { return nombre; }
    public Integer getIdEstado() { return idEstado; }
}
