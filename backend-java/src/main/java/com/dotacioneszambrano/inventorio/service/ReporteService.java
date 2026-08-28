package com.dotacioneszambrano.inventorio.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ejecuta el stored procedure InformationGeneral(categoria, genero, producto, talla, estilo).
 * Devuelve columns y rows en el mismo formato que el backend Python.
 */
@Service
public class ReporteService {

    private final DataSource dataSource;

    public ReporteService(DataSource dataSource, JdbcTemplate jdbc) {
        this.dataSource = dataSource;
    }

    public Map<String, Object> obtenerReporteGeneralData(
            String categoria, String genero, String producto, String talla, String estilo) {

        SimpleJdbcCall call = new SimpleJdbcCall(dataSource)
                .withProcedureName("InformationGeneral")
                .withoutProcedureColumnMetaDataAccess()
                .useInParameterNames("categoria", "genero", "producto", "talla", "estilo")
                .declareParameters(
                        new org.springframework.jdbc.core.SqlParameter("categoria", java.sql.Types.VARCHAR),
                        new org.springframework.jdbc.core.SqlParameter("genero", java.sql.Types.VARCHAR),
                        new org.springframework.jdbc.core.SqlParameter("producto", java.sql.Types.VARCHAR),
                        new org.springframework.jdbc.core.SqlParameter("talla", java.sql.Types.VARCHAR),
                        new org.springframework.jdbc.core.SqlParameter("estilo", java.sql.Types.VARCHAR)
                );

        Map<String, Object> inParams = new LinkedHashMap<>();
        inParams.put("categoria", cleanParam(categoria));
        inParams.put("genero", cleanParam(genero));
        inParams.put("producto", cleanParam(producto));
        inParams.put("talla", cleanParam(talla));
        inParams.put("estilo", cleanParam(estilo));

        Map<String, Object> out = call.execute(inParams);

        // SimpleJdbcCall coloca los result sets bajo la clave "#result-set-1"
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) out.getOrDefault(
                "#result-set-1", List.of());

        List<String> columns = rows.isEmpty()
                ? List.of()
                : List.copyOf(rows.get(0).keySet());

        return Map.of("columns", columns, "rows", rows);
    }

    public static String cleanParam(String val) {
        if (val == null) return null;
        String t = val.trim();
        if (t.isEmpty() || "null".equalsIgnoreCase(t) || "undefined".equalsIgnoreCase(t)) return null;
        return val;
    }
}
