package com.dotacioneszambrano.inventorio.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "colores")
public class Color {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_color")
    private Integer idColor;

    @Column(name = "nombre")
    private String nombre;

    @Column(name = "id_estado")
    private Integer idEstado;

    protected Color() {}

    public Color(String nombre, Integer idEstado) {
        this.nombre = nombre;
        this.idEstado = idEstado;
    }

    public Integer getIdColor() { return idColor; }
    public String getNombre() { return nombre; }
    public Integer getIdEstado() { return idEstado; }
}
