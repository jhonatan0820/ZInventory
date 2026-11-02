package com.dotacioneszambrano.inventorio.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "categorias")
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_categoria")
    private Integer idCategoria;

    @Column(name = "nombre")
    private String nombre;

    @Column(name = "id_estado")
    private Integer idEstado;

    protected Categoria() {}

    public Categoria(String nombre, Integer idEstado) {
        this.nombre = nombre;
        this.idEstado = idEstado;
    }

    public Integer getIdCategoria() { return idCategoria; }
    public String getNombre() { return nombre; }
    public Integer getIdEstado() { return idEstado; }
}
