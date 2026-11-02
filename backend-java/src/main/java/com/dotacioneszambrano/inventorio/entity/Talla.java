package com.dotacioneszambrano.inventorio.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tallas")
public class Talla {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_talla")
    private Integer idTalla;

    @Column(name = "valor")
    private String valor;

    @Column(name = "id_categoria")
    private Integer idCategoria;

    @Column(name = "id_genero")
    private Integer idGenero;

    @Column(name = "id_estado")
    private Integer idEstado;

    protected Talla() {}

    public Talla(String valor, Integer idCategoria, Integer idGenero, Integer idEstado) {
        this.valor = valor;
        this.idCategoria = idCategoria;
        this.idGenero = idGenero;
        this.idEstado = idEstado;
    }

    public Integer getIdTalla() { return idTalla; }
    public String getValor() { return valor; }
    public Integer getIdCategoria() { return idCategoria; }
    public Integer getIdGenero() { return idGenero; }
    public Integer getIdEstado() { return idEstado; }
}
