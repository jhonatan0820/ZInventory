package com.dotacioneszambrano.inventorio.controller;

import com.dotacioneszambrano.inventorio.exception.ApiException;
import com.dotacioneszambrano.inventorio.service.PdfReportService;
import com.dotacioneszambrano.inventorio.service.ReporteService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
public class ReporteController {

    private final ReporteService reporteService;
    private final PdfReportService pdfReportService;

    public ReporteController(ReporteService reporteService, PdfReportService pdfReportService) {
        this.reporteService = reporteService;
        this.pdfReportService = pdfReportService;
    }

    @GetMapping("/InformationGeneral")
    public Map<String, Object> reporteGeneral(
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String genero,
            @RequestParam(required = false) String producto,
            @RequestParam(required = false) String talla,
            @RequestParam(required = false) String estilo) {
        return reporteService.obtenerReporteGeneralData(categoria, genero, producto, talla, estilo);
    }

    @GetMapping("/InformationGeneralPdf")
    public ResponseEntity<?> reporteGeneralPdf(
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String genero,
            @RequestParam(required = false) String producto,
            @RequestParam(required = false) String talla,
            @RequestParam(required = false) String estilo,
            @RequestParam(value = "format", defaultValue = "pdf") String format) throws Exception {

        Map<String, String> req = new HashMap<>();
        req.put("categoria", categoria);
        req.put("genero", genero);
        req.put("producto", producto);
        req.put("talla", talla);
        req.put("estilo", estilo);

        String output = format == null ? "pdf" : format.trim().toLowerCase();
        if ("html".equals(output)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(pdfReportService.renderHtml(req));
        }
        if (!"pdf".equals(output)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Formato no soportado. Usa html o pdf");
        }

        byte[] pdf = pdfReportService.renderPdf(req);
        String filename = "resumen_general_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(pdf);
    }
}
