package com.dotacioneszambrano.inventorio.service;

import com.dotacioneszambrano.inventorio.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

/**
 * Migracion 1:1 de las rutas de catalogos del backend Python:
 * categorias, generos, colores, tallas y estilos.
 */
@Service
public class CatalogoService {

    private final JdbcTemplate jdbc;

    public CatalogoService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> getCategorias() {
        return jdbc.queryForList("""
                SELECT id_categoria, nombre
                FROM categorias
                WHERE id_estado = 1
                ORDER BY nombre
                """);
    }

    public void addCategoria(String nombre) {
        if (nombre == null || nombre.isBlank())
            throw new ApiException(HttpStatus.BAD_REQUEST, "Nombre requerido");
        jdbc.update("INSERT INTO categorias (nombre, id_estado) VALUES (?, 1)", nombre.trim());
    }

    public List<Map<String, Object>> getGeneros() {
        return jdbc.queryForList("SELECT id_genero, nombre FROM generos WHERE id_estado = 1");
    }

    public List<Map<String, Object>> getColores() {
        return jdbc.queryForList(
                "SELECT id_color, nombre FROM colores WHERE id_estado = 1 ORDER BY nombre");
    }

    public Long addColor(String nombre) {
        if (nombre == null || nombre.isBlank())
            throw new ApiException(HttpStatus.BAD_REQUEST, "Nombre requerido");
        String value = nombre.trim();
        KeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO colores (nombre, id_estado) VALUES (?, 1)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, value);
            return ps;
        }, holder);
        Number key = holder.getKey();
        return key == null ? null : key.longValue();
    }

    public List<Map<String, Object>> getTallas() {
        return jdbc.queryForList("SELECT id_talla, valor, id_genero FROM tallas ORDER BY valor");
    }

    public List<Map<String, Object>> getTallasPorCategoriaGenero(Integer idCategoria, Integer idGenero) {
        if (idCategoria == null || idGenero == null) return List.of();
        return jdbc.queryForList("""
                SELECT DISTINCT valor, id_talla
                FROM tallas
                WHERE id_categoria = ?
                  AND id_genero = ?
                  AND id_estado = 1
                ORDER BY id_talla
                """, idCategoria, idGenero);
    }

    public List<Map<String, Object>> getTallasPorCategoria(Integer idCategoria) {
        if (idCategoria == null) return List.of();
        return jdbc.queryForList("""
                SELECT DISTINCT valor AS talla
                FROM tallas
                WHERE id_categoria = ?
                  AND id_estado = 1
                ORDER BY valor
                """, idCategoria);
    }

    public List<Map<String, Object>> getEstilosUnicos() {
        return jdbc.queryForList("""
                SELECT DISTINCT
                    TRIM(LOWER(nombre)) AS nombre
                FROM estilos
                WHERE id_estado = 1
                ORDER BY nombre
                """);
    }

    public List<Map<String, Object>> getTallasValidas(Integer idCategoria) {
        StringBuilder q = new StringBuilder("""
                SELECT DISTINCT t.valor AS talla
                FROM variantes v
                INNER JOIN tallas t ON v.id_talla = t.id_talla
                WHERE v.id_estado = 1 and t.id_estado = 1
                """);
        List<Object> params = new java.util.ArrayList<>();
        if (idCategoria != null) {
            q.append(" AND t.id_categoria = ?");
            params.add(idCategoria);
        }
        q.append(" ORDER BY t.valor");
        return jdbc.queryForList(q.toString(), params.toArray());
    }

    public List<Map<String, Object>> getNombresProductos() {
        return jdbc.queryForList("""
                SELECT
                    TRIM(LOWER(nombre)) AS nombre
                FROM productos
                WHERE id_estado = 1
                GROUP BY TRIM(LOWER(nombre))
                ORDER BY nombre
                """);
    }
}
