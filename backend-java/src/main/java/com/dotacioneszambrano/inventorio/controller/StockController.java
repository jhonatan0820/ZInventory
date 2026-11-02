package com.dotacioneszambrano.inventorio.controller;

import com.dotacioneszambrano.inventorio.service.StockService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class StockController {

    private final StockService service;

    public StockController(StockService service) {
        this.service = service;
    }

    @PostMapping("/ActualizarStock")
    public Map<String, Object> actualizar(@RequestBody Map<String, Object> body) {
        Long idVariante = num(body.get("id_variante"));
        Integer cantidad = intOf(body.get("cantidad"));
        Double precioVenta = doubleOf(body.get("precio_venta"));
        service.actualizarStock(idVariante, cantidad, precioVenta);
        return Map.of("ok", true);
    }

    @PostMapping("/EntradaStockConPrecio")
    public Map<String, Object> entrada(@RequestBody Map<String, Object> body) {
        Long idVariante = num(body.get("id_variante"));
        Integer cantidad = intOf(body.get("cantidad"));
        Double precio = doubleOf(body.get("precio"));
        boolean varianteCreada = service.entradaStock(idVariante, cantidad, precio);
        return Map.of("ok", true, "variante_creada", varianteCreada);
    }

    private static Long num(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        return Long.parseLong(String.valueOf(o));
    }

    private static Integer intOf(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(o));
    }

    private static Double doubleOf(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(o));
    }
}
