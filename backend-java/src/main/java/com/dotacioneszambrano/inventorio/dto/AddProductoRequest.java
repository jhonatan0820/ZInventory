package com.dotacioneszambrano.inventorio.dto;

import java.util.List;

public record AddProductoRequest(
        Integer id_genero,
        Integer id_categoria,
        String nombre,
        String marca,
        String estilo,
        Integer id_color,
        List<VarianteInput> variantes
) {
    public record VarianteInput(String talla, Double precio, Integer stock) {}
}
