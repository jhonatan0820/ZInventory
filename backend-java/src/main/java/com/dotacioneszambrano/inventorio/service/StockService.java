package com.dotacioneszambrano.inventorio.service;

import com.dotacioneszambrano.inventorio.exception.ApiException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockService {

    private final JdbcTemplate jdbc;

    public StockService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // POST /ActualizarStock  (SALIDA con precio de venta)
    @Transactional
    public void actualizarStock(Long idVariante, Integer cantidad, Double precioVenta) {
        if (idVariante == null || cantidad == null || precioVenta == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Datos incompletos");
        }
        Integer stockAnterior;
        try {
            stockAnterior = jdbc.queryForObject(
                    "SELECT stock FROM variantes WHERE id_variante = ? AND id_estado = 1",
                    Integer.class, idVariante
            );
        } catch (EmptyResultDataAccessException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Variante no encontrada");
        }
        if (stockAnterior == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Variante no encontrada");
        }
        if (cantidad <= 0 || cantidad > stockAnterior) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cantidad inválida");
        }

        int stockNuevo = stockAnterior - cantidad;
        double totalVenta = cantidad * precioVenta;

        jdbc.update("UPDATE variantes SET stock = ? WHERE id_variante = ?", stockNuevo, idVariante);

        jdbc.update("""
                INSERT INTO movimientos_inventario
                (id_variante, tipo, cantidad, stock_anterior, stock_nuevo,
                 fecha, precio_venta, fecha_venta, total_venta)
                VALUES (?, 'SALIDA', ?, ?, ?, NOW(), ?, NOW(), ?)
                """, idVariante, cantidad, stockAnterior, stockNuevo, precioVenta, totalVenta);
    }

    // POST /EntradaStock
    @Transactional
    public void entradaStock(Long idVariante, Integer cantidad) {
        if (idVariante == null || cantidad == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Datos incompletos");
        }
        Integer stockAnterior = jdbc.queryForObject(
                "SELECT stock FROM variantes WHERE id_variante = ?",
                Integer.class, idVariante
        );
        int stockNuevo = stockAnterior + cantidad;
        jdbc.update("UPDATE variantes SET stock = ? WHERE id_variante = ?", stockNuevo, idVariante);
        jdbc.update("""
                INSERT INTO movimientos_inventario
                (id_variante, tipo, cantidad, stock_anterior, stock_nuevo, fecha)
                VALUES (?, 'ENTRADA', ?, ?, ?, NOW())
                """, idVariante, cantidad, stockAnterior, stockNuevo);
    }
}
