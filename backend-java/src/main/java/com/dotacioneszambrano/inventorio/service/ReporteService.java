package com.dotacioneszambrano.inventorio.service;

import com.dotacioneszambrano.inventorio.repository.ReporteRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReporteService {

    private final ReporteRepository reporteRepository;

    public ReporteService(ReporteRepository reporteRepository) {
        this.reporteRepository = reporteRepository;
    }

    public Map<String, Object> obtenerReporteGeneralData(
            String categoria, String genero, String producto, String talla, String estilo) {
        List<Map<String, Object>> rows = reporteRepository.ejecutarReporteGeneral(
                cleanParam(categoria), cleanParam(genero), cleanParam(producto),
                cleanParam(talla), cleanParam(estilo));
        List<String> columns = rows.isEmpty()
                ? List.of()
                : List.copyOf(rows.get(0).keySet());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("columns", columns);
        result.put("rows", rows);
        return result;
    }

    public static String cleanParam(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed)
                || "undefined".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return value;
    }
}
