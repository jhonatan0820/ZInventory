package com.dotacioneszambrano.inventorio.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "variantes")
public class Variante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_variante")
    private Long idVariante;

    @Column(name = "id_producto")
    private Long idProducto;

    @Column(name = "id_color")
    private Integer idColor;

    @Column(name = "id_talla")
    private Integer idTalla;

    @Column(name = "precio")
    private Double precio;

    @Column(name = "stock")
    private Integer stock;

    @Column(name = "id_estado")
    private Integer idEstado;

    protected Variante() {}

    public Variante(Long idProducto, Integer idColor, Integer idTalla, Double precio,
                    Integer stock, Integer idEstado) {
        this.idProducto = idProducto;
        this.idColor = idColor;
        this.idTalla = idTalla;
        this.precio = precio;
        this.stock = stock;
        this.idEstado = idEstado;
    }

    public Long getIdVariante() { return idVariante; }
    public Long getIdProducto() { return idProducto; }
    public Integer getIdColor() { return idColor; }
    public Integer getIdTalla() { return idTalla; }
    public Double getPrecio() { return precio; }
    public Integer getStock() { return stock; }
    public Integer getIdEstado() { return idEstado; }
    public void setPrecio(Double precio) { this.precio = precio; }
    public void setStock(Integer stock) { this.stock = stock; }
    public void setIdEstado(Integer idEstado) { this.idEstado = idEstado; }
}
