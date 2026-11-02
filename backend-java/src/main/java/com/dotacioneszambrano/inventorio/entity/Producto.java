package com.dotacioneszambrano.inventorio.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "productos")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_producto")
    private Long idProducto;

    @Column(name = "nombre")
    private String nombre;

    @Column(name = "id_marca")
    private Long idMarca;

    @Column(name = "id_estilo")
    private Long idEstilo;

    @Column(name = "id_categoria")
    private Integer idCategoria;

    @Column(name = "id_genero")
    private Integer idGenero;

    @Column(name = "id_estado")
    private Integer idEstado;

    protected Producto() {}

    public Producto(String nombre, Long idMarca, Long idEstilo, Integer idCategoria,
                    Integer idGenero, Integer idEstado) {
        this.nombre = nombre;
        this.idMarca = idMarca;
        this.idEstilo = idEstilo;
        this.idCategoria = idCategoria;
        this.idGenero = idGenero;
        this.idEstado = idEstado;
    }

    public Long getIdProducto() { return idProducto; }
    public String getNombre() { return nombre; }
    public Long getIdMarca() { return idMarca; }
    public Long getIdEstilo() { return idEstilo; }
    public Integer getIdCategoria() { return idCategoria; }
    public Integer getIdGenero() { return idGenero; }
    public Integer getIdEstado() { return idEstado; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public void setIdEstado(Integer idEstado) { this.idEstado = idEstado; }
}
