package com.dotacioneszambrano.inventorio.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "marcas")
public class Marca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_marca")
    private Long idMarca;

    @Column(name = "nombre")
    private String nombre;

    @Column(name = "id_categoria")
    private Integer idCategoria;

    @Column(name = "id_estado")
    private Integer idEstado;

    protected Marca() {}

    public Marca(String nombre, Integer idCategoria, Integer idEstado) {
        this.nombre = nombre;
        this.idCategoria = idCategoria;
        this.idEstado = idEstado;
    }

    public Long getIdMarca() { return idMarca; }
    public String getNombre() { return nombre; }
    public Integer getIdCategoria() { return idCategoria; }
    public Integer getIdEstado() { return idEstado; }
}
