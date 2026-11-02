package com.dotacioneszambrano.inventorio.service;

import com.dotacioneszambrano.inventorio.config.ReportProperties;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the report context and renders the PDF with Thymeleaf and OpenHTMLtoPDF.
 */
@Service
public class PdfReportService {

    private final ReporteService reporteService;
    private final ReportProperties reportProperties;
    private final TemplateEngine templateEngine;

    public PdfReportService(ReporteService reporteService,
                            ReportProperties reportProperties,
                            TemplateEngine templateEngine) {
        this.reporteService = reporteService;
        this.reportProperties = reportProperties;
        this.templateEngine = templateEngine;
    }

    public String renderHtml(Map<String, String> filtrosRequest) {
        Context ctx = buildContext(filtrosRequest);
        return templateEngine.process("information_general_pdf", ctx);
    }

    public byte[] renderPdf(Map<String, String> filtrosRequest) throws Exception {
        String html = renderHtml(filtrosRequest);
        // openhtmltopdf requiere XHTML valido: pasamos por jsoup para sanear.
        Document doc = Jsoup.parse(html);
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
        org.w3c.dom.Document w3c = new W3CDom().fromJsoup(doc);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withW3cDocument(w3c, "/");
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        }
    }

    private Context buildContext(Map<String, String> req) {
        Map<String, Object> data = reporteService.obtenerReporteGeneralData(
                req.get("categoria"), req.get("genero"), req.get("producto"),
                req.get("talla"), req.get("estilo"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) data.get("rows");
        @SuppressWarnings("unchecked")
        List<String> columns = (List<String>) data.get("columns");

        // Totales
        long cantidadTotal = 0;
        long valorTotal = 0;
        for (Map<String, Object> row : rows) {
            cantidadTotal += parseReportNumber(row.get("Cantidad"));
            valorTotal += parseReportNumber(row.get("Total"));
        }

        // Filtros
        Map<String, String> filtros = new LinkedHashMap<>();
        filtros.put("Categoria", orTodos(ReporteService.cleanParam(req.get("categoria"))));
        filtros.put("Genero", orTodos(ReporteService.cleanParam(req.get("genero"))));
        filtros.put("Producto", orTodos(ReporteService.cleanParam(req.get("producto"))));
        filtros.put("Talla", orTodos(ReporteService.cleanParam(req.get("talla"))));
        filtros.put("Estilo", orTodos(ReporteService.cleanParam(req.get("estilo"))));

        Context ctx = new Context(new Locale("es"));
        ctx.setVariable("empresa_nombre", reportProperties.companyName());
        ctx.setVariable("empresa_logo", reportProperties.companyLogo() == null ? "" : reportProperties.companyLogo());
        ctx.setVariable("empresa_direccion", reportProperties.companyAddress());
        ctx.setVariable("empresa_celular", reportProperties.companyPhone());
        ctx.setVariable("fecha_generacion",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        ctx.setVariable("filtros", filtros);
        ctx.setVariable("columnas", columns);
        ctx.setVariable("filas", rows);
        ctx.setVariable("cantidad_total", formatNumberEs(cantidadTotal));
        ctx.setVariable("valor_total", "$ " + formatNumberEs(valorTotal));
        return ctx;
    }

    private static String orTodos(String v) {
        return (v == null || v.isBlank()) ? "Todos" : v;
    }

    static long parseReportNumber(Object value) {
        if (value == null) return 0;
        if (value instanceof Number n) return Math.round(n.doubleValue());
        String s = String.valueOf(value).trim();
        if (s.isEmpty()) return 0;
        StringBuilder digits = new StringBuilder();
        for (char c : s.toCharArray()) if (Character.isDigit(c)) digits.append(c);
        return digits.length() == 0 ? 0 : Long.parseLong(digits.toString());
    }

    static String formatNumberEs(long value) {
        // Formato "1.234.567" (miles con punto)
        return String.format(Locale.GERMAN, "%,d", value);
    }
}
