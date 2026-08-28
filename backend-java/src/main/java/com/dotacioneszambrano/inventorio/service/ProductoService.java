package com.dotacioneszambrano.inventorio.service;

import com.dotacioneszambrano.inventorio.dto.AddProductoRequest;
import com.dotacioneszambrano.inventorio.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Migracion 1:1 de la logica de productos del backend Python.
 * Mantiene los mismos SQL, incluyendo upsert por combinacion NULL-safe con <=>.
 */
@Service
public class ProductoService {

    private static final Logger log = LoggerFactory.getLogger(ProductoService.class);
    private final JdbcTemplate jdbc;

    public ProductoService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // GET /GetProductos
    public List<Map<String, Object>> getProductos() {
        return jdbc.queryForList("""
                SELECT
                    p.id_producto AS id_producto,
                    p.nombre AS nomproducto,
                    cat.nombre AS categoria,
                    v.id_variante AS id_variante,
                    m.nombre AS marca,
                    e.nombre AS estilo,
                    c.nombre AS color,
                    t.valor AS talla,
                    v.precio AS precio,
                    v.stock AS stock
                FROM variantes v
                JOIN productos p ON v.id_producto = p.id_producto
                LEFT JOIN marcas m ON p.id_marca = m.id_marca
                JOIN categorias cat ON p.id_categoria = cat.id_categoria
                LEFT JOIN estilos e ON p.id_estilo = e.id_estilo
                LEFT JOIN colores c ON v.id_color = c.id_color
                LEFT JOIN tallas t ON v.id_talla = t.id_talla
                WHERE p.id_estado = 1 and v.id_estado = 1
                """);
    }

    // POST /AddProducto
    @Transactional
    public void addProducto(AddProductoRequest req) {
        // Validaciones (identicas al Python)
        if (req.id_genero() == null)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Género requerido");
        if (req.id_categoria() == null)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Categoría requerida");
        String nombre = req.nombre() == null ? "" : req.nombre().trim();
        if (nombre.isBlank())
            throw new ApiException(HttpStatus.BAD_REQUEST, "Nombre requerido");
        if (req.variantes() == null || req.variantes().isEmpty())
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe tener variantes");

        String marca = req.marca() == null ? null : req.marca().trim();
        if (marca != null && marca.isEmpty()) marca = null;
        String estilo = req.estilo() == null ? null : req.estilo().trim();
        if (estilo != null && estilo.isEmpty()) estilo = null;

        int idCategoria = req.id_categoria();
        int idGenero = req.id_genero();
        int idColor = req.id_color() == null ? 0 : req.id_color();

        // MARCA
        Long idMarca = null;
        if (marca != null) {
            List<Long> m = jdbc.query(
                    "SELECT id_marca FROM marcas WHERE nombre = ? and id_estado = 1",
                    (rs, i) -> rs.getLong(1), marca);
            if (!m.isEmpty()) {
                idMarca = m.get(0);
            } else {
                idMarca = insertReturningId(
                        "INSERT INTO marcas (nombre, id_categoria, id_estado) VALUES (?, ?, 1)",
                        marca, idCategoria);
            }
        }

        // ESTILO
        Long idEstilo = null;
        if (estilo != null) {
            List<Long> e = jdbc.query(
                    "SELECT id_estilo FROM estilos WHERE nombre = ?",
                    (rs, i) -> rs.getLong(1), estilo);
            if (!e.isEmpty()) {
                idEstilo = e.get(0);
            } else {
                idEstilo = insertReturningId(
                        "INSERT INTO estilos (nombre, id_marca, id_estado) VALUES (?, ?, 1)",
                        estilo, idMarca);
            }
        }

        // PRODUCTO (reutilizar si existe, con NULL-safe <=>)
        Long idProducto;
        List<Map<String, Object>> existente = jdbc.queryForList("""
                SELECT id_producto, id_estado
                FROM productos
                WHERE nombre = ?
                  AND id_categoria = ?
                  AND id_genero = ?
                  AND (id_marca <=> ?)
                  AND (id_estilo <=> ?)
                LIMIT 1
                """, nombre, idCategoria, idGenero, idMarca, idEstilo);

        if (!existente.isEmpty()) {
            Map<String, Object> ex = existente.get(0);
            idProducto = ((Number) ex.get("id_producto")).longValue();
            if (((Number) ex.get("id_estado")).intValue() != 1) {
                jdbc.update("UPDATE productos SET id_estado = 1 WHERE id_producto = ?", idProducto);
            }
        } else {
            idProducto = insertReturningId("""
                    INSERT INTO productos
                    (nombre, id_marca, id_estilo, id_categoria, id_genero, id_estado)
                    VALUES (?, ?, ?, ?, ?, 1)
                    """, nombre, idMarca, idEstilo, idCategoria, idGenero);
        }

        // VARIANTES
        for (AddProductoRequest.VarianteInput v : req.variantes()) {
            String talla = v.talla();
            Double precio = v.precio();
            Integer stock = v.stock();

            Long idTalla;
            List<Long> t = jdbc.query("""
                    SELECT id_talla
                    FROM tallas
                    WHERE valor = ? AND id_categoria = ? AND id_genero = ? AND id_estado = 1
                    """, (rs, i) -> rs.getLong(1), talla, idCategoria, idGenero);
            if (!t.isEmpty()) {
                idTalla = t.get(0);
            } else {
                idTalla = insertReturningId("""
                        INSERT INTO tallas (valor, id_categoria, id_genero, id_estado)
                        VALUES (?, ?, ?, 1)
                        """, talla, idCategoria, idGenero);
            }

            List<Long> vExist = jdbc.query("""
                    SELECT id_variante
                    FROM variantes
                    WHERE id_producto = ? AND id_color = ? AND id_talla = ?
                    LIMIT 1
                    """, (rs, i) -> rs.getLong(1), idProducto, idColor, idTalla);

            if (!vExist.isEmpty()) {
                jdbc.update("""
                        UPDATE variantes
                        SET id_estado = 1, precio = ?, stock = ?
                        WHERE id_variante = ?
                        """, precio, stock, vExist.get(0));
            } else {
                jdbc.update("""
                        INSERT INTO variantes
                        (id_producto, id_color, id_talla, precio, stock, id_estado)
                        VALUES (?, ?, ?, ?, ?, 1)
                        """, idProducto, idColor, idTalla, precio, stock);
            }
        }
    }

    // POST /DeleteProductos
    @Transactional
    public int deleteProductos(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDs inválidos");
        }
        String placeholders = ids.stream().map(x -> "?").collect(Collectors.joining(","));

        List<Long> productoIds = jdbc.query(
                "SELECT DISTINCT p.id_producto FROM variantes v " +
                        "JOIN productos p ON v.id_producto = p.id_producto " +
                        "WHERE v.id_variante IN (" + placeholders + ")",
                (rs, i) -> rs.getLong(1), ids.toArray());

        int eliminados = jdbc.update(
                "UPDATE variantes SET id_estado = 2 WHERE id_variante IN (" + placeholders + ")",
                ids.toArray());

        if (!productoIds.isEmpty()) {
            String phProd = productoIds.stream().map(x -> "?").collect(Collectors.joining(","));
            List<Long> sinVariantes = jdbc.query("""
                    SELECT p.id_producto
                    FROM productos p
                    LEFT JOIN variantes v
                      ON v.id_producto = p.id_producto AND v.id_estado = 1
                    WHERE p.id_producto IN (%s)
                    GROUP BY p.id_producto
                    HAVING COUNT(v.id_variante) = 0
                    """.formatted(phProd), (rs, i) -> rs.getLong(1), productoIds.toArray());

            if (!sinVariantes.isEmpty()) {
                String phDis = sinVariantes.stream().map(x -> "?").collect(Collectors.joining(","));
                jdbc.update("UPDATE productos SET id_estado = 2 WHERE id_producto IN (" + phDis + ")",
                        sinVariantes.toArray());
            }
        }
        return eliminados;
    }

    private Long insertReturningId(String sql, Object... params) {
        KeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            return ps;
        }, holder);
        Number key = holder.getKey();
        return key == null ? null : key.longValue();
    }
}
