package com.dotacioneszambrano.inventorio.controller;

import com.dotacioneszambrano.inventorio.dto.AddProductoRequest;
import com.dotacioneszambrano.inventorio.service.ProductoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class ProductoController {

    private final ProductoService service;

    public ProductoController(ProductoService service) {
        this.service = service;
    }

    @GetMapping("/GetProductos")
    public List<Map<String, Object>> getProductos() {
        return service.getProductos();
    }

    @PostMapping("/AddProducto")
    public Map<String, Object> addProducto(@RequestBody AddProductoRequest req) {
        service.addProducto(req);
        return Map.of("ok", true);
    }

    @PostMapping("/DeleteProductos")
    public Map<String, Object> deleteProductos(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> rawIds = (List<Object>) body.getOrDefault("ids", List.of());
        List<Integer> ids = rawIds.stream()
                .map(x -> x instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(x)))
                .toList();
        int eliminados = service.deleteProductos(ids);
        return Map.of("ok", true, "eliminados", eliminados);
    }
}
