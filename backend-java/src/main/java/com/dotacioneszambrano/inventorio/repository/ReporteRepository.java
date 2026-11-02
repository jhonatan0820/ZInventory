package com.dotacioneszambrano.inventorio.repository;

import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class ReporteRepository {

    private final DataSource dataSource;

    public ReporteRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Map<String, Object>> ejecutarReporteGeneral(
            String categoria, String genero, String producto, String talla, String estilo) {
        SimpleJdbcCall call = new SimpleJdbcCall(dataSource)
                .withProcedureName("InformationGeneral")
                .withoutProcedureColumnMetaDataAccess()
                .useInParameterNames("categoria", "genero", "producto", "talla", "estilo")
                .declareParameters(
                        new SqlParameter("categoria", Types.VARCHAR),
                        new SqlParameter("genero", Types.VARCHAR),
                        new SqlParameter("producto", Types.VARCHAR),
                        new SqlParameter("talla", Types.VARCHAR),
                        new SqlParameter("estilo", Types.VARCHAR)
                );

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("categoria", categoria);
        params.put("genero", genero);
        params.put("producto", producto);
        params.put("talla", talla);
        params.put("estilo", estilo);

        Map<String, Object> result = call.execute(params);
        Object rows = result.get("#result-set-1");
        if (rows == null) return List.of();
        if (!(rows instanceof List<?> resultRows)) {
            throw new IllegalStateException("El procedimiento InformationGeneral devolvió un resultado inválido");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> typedRows = (List<Map<String, Object>>) resultRows;
        return typedRows;
    }
}
