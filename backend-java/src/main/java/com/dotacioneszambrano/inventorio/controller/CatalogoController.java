package com.dotacioneszambrano.inventorio.controller;

import com.dotacioneszambrano.inventorio.service.CatalogoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class CatalogoController {

    private final CatalogoService service;

    public CatalogoController(CatalogoService service) {
        this.service = service;
    }

    @GetMapping("/GetCategorias")
    public List<Map<String, Object>> getCategorias() { return service.getCategorias(); }

    @PostMapping("/AddCategoria")
    public Map<String, Object> addCategoria(@RequestBody Map<String, String> body) {
        service.addCategoria(body.getOrDefault("nombre", "").trim());
        return Map.of("ok", true);
    }

    @GetMapping("/GetGeneros")
    public List<Map<String, Object>> getGeneros() { return service.getGeneros(); }

    @GetMapping("/GetColores")
    public List<Map<String, Object>> getColores() { return service.getColores(); }

    @PostMapping("/AddColor")
    public Map<String, Object> addColor(@RequestBody Map<String, String> body) {
        Long id = service.addColor(body.getOrDefault("nombre", "").trim());
        Map<String, Object> resp = new HashMap<>();
        resp.put("ok", true);
        resp.put("id_color", id);
        return resp;
    }

    @GetMapping("/GetTallas")
    public List<Map<String, Object>> getTallas() { return service.getTallas(); }

    @GetMapping("/GetTallasPorCategoriaGenero")
    public List<Map<String, Object>> getTallasPorCategoriaGenero(
            @RequestParam(value = "id_categoria", required = false) Integer idCategoria,
            @RequestParam(value = "id_genero", required = false) Integer idGenero) {
        return service.getTallasPorCategoriaGenero(idCategoria, idGenero);
    }

    @GetMapping("/GetTallasPorCategoria")
    public List<Map<String, Object>> getTallasPorCategoria(
            @RequestParam(value = "id_categoria", required = false) Integer idCategoria) {
        return service.getTallasPorCategoria(idCategoria);
    }

    @GetMapping("/GetEstilosUnicos")
    public List<Map<String, Object>> getEstilosUnicos() { return service.getEstilosUnicos(); }

    @GetMapping("/GetTallasValidas")
    public List<Map<String, Object>> getTallasValidas(
            @RequestParam(value = "id_categoria", required = false) Integer idCategoria) {
        return service.getTallasValidas(idCategoria);
    }

    @GetMapping("/GetNombresProductos")
    public List<Map<String, Object>> getNombresProductos() { return service.getNombresProductos(); }
}
