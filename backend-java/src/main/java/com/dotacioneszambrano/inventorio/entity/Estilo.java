package com.dotacioneszambrano.inventorio.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "estilos")
public class Estilo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_estilo")
    private Long idEstilo;

    @Column(name = "nombre")
    private String nombre;

    @Column(name = "id_marca")
    private Long idMarca;

    @Column(name = "id_estado")
    private Integer idEstado;

    protected Estilo() {}

    public Estilo(String nombre, Long idMarca, Integer idEstado) {
        this.nombre = nombre;
        this.idMarca = idMarca;
        this.idEstado = idEstado;
    }

    public Long getIdEstilo() { return idEstilo; }
    public String getNombre() { return nombre; }
    public Long getIdMarca() { return idMarca; }
    public Integer getIdEstado() { return idEstado; }
}
