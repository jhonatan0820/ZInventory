package com.dotacioneszambrano.inventorio.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class MovimientoInventarioRepository {

    private final JdbcTemplate jdbc;

    public MovimientoInventarioRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void registrarSalida(Long idVariante, Integer cantidad, Integer stockAnterior,
                                Integer stockNuevo, Double precioVenta, Double totalVenta) {
        jdbc.update("""
                INSERT INTO movimientos_inventario
                (id_variante, tipo, cantidad, stock_anterior, stock_nuevo,
                 fecha, precio_venta, fecha_venta, total_venta)
                VALUES (?, 'SALIDA', ?, ?, ?, NOW(), ?, NOW(), ?)
                """, idVariante, cantidad, stockAnterior, stockNuevo, precioVenta, totalVenta);
    }

    public void registrarEntrada(Long idVariante, Integer cantidad,
                                 Integer stockAnterior, Integer stockNuevo) {
        jdbc.update("""
                INSERT INTO movimientos_inventario
                (id_variante, tipo, cantidad, stock_anterior, stock_nuevo, fecha)
                VALUES (?, 'ENTRADA', ?, ?, ?, NOW())
                """, idVariante, cantidad, stockAnterior, stockNuevo);
    }

    public List<Map<String, Object>> findRecentMovements() {
        return jdbc.queryForList("""
                SELECT m.id_movimiento, m.tipo, m.cantidad, m.stock_anterior, m.stock_nuevo,
                       DATE_FORMAT(
                           CONVERT_TZ(
                               CASE WHEN m.tipo = 'SALIDA' THEN COALESCE(m.fecha_venta, m.fecha)
                                    ELSE m.fecha END,
                               @@session.time_zone, '-05:00'),
                           '%Y-%m-%d %H:%i:%s') AS fecha,
                       m.precio_venta, m.total_venta, p.nombre AS producto,
                       c.nombre AS color, t.valor AS talla
                FROM movimientos_inventario m
                JOIN variantes v ON v.id_variante = m.id_variante
                JOIN productos p ON p.id_producto = v.id_producto
                LEFT JOIN colores c ON c.id_color = v.id_color
                LEFT JOIN tallas t ON t.id_talla = v.id_talla
                ORDER BY m.id_movimiento DESC
                LIMIT 10
                """);
    }

    public List<Map<String, Object>> findLatestSale(Long productId, String productName) {
        String productFilter;
        Object filterValue;
        if (productId != null) {
            productFilter = "p.id_producto = ?";
            filterValue = productId;
        } else {
            productFilter = "LOWER(TRIM(p.nombre)) = LOWER(TRIM(?))";
            filterValue = productName;
        }

        String query = """
                SELECT m.id_movimiento, m.id_variante,
                       UNIX_TIMESTAMP(COALESCE(m.fecha_venta, m.fecha))
                           AS fecha_venta_epoch,
                       m.cantidad, m.stock_anterior, m.stock_nuevo,
                       m.precio_venta, m.total_venta, p.id_producto,
                       p.nombre AS producto, c.nombre AS color, t.valor AS talla
                FROM movimientos_inventario m
                JOIN variantes v ON v.id_variante = m.id_variante
                JOIN productos p ON p.id_producto = v.id_producto
                LEFT JOIN colores c ON c.id_color = v.id_color
                LEFT JOIN tallas t ON t.id_talla = v.id_talla
                WHERE m.tipo = 'SALIDA' AND
                """ + productFilter + " ORDER BY m.id_movimiento DESC LIMIT 1";
        return jdbc.queryForList(query, filterValue);
    }
}
