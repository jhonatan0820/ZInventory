package com.dotacioneszambrano.inventorio.service;

import com.dotacioneszambrano.inventorio.entity.Categoria;
import com.dotacioneszambrano.inventorio.entity.Color;
import com.dotacioneszambrano.inventorio.entity.Estilo;
import com.dotacioneszambrano.inventorio.entity.Marca;
import com.dotacioneszambrano.inventorio.entity.Talla;
import com.dotacioneszambrano.inventorio.exception.ApiException;
import com.dotacioneszambrano.inventorio.repository.CategoriaRepository;
import com.dotacioneszambrano.inventorio.repository.ColorRepository;
import com.dotacioneszambrano.inventorio.repository.EstiloRepository;
import com.dotacioneszambrano.inventorio.repository.GeneroRepository;
import com.dotacioneszambrano.inventorio.repository.MarcaRepository;
import com.dotacioneszambrano.inventorio.repository.ProductoRepository;
import com.dotacioneszambrano.inventorio.repository.TallaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class CatalogoService {

    private final CategoriaRepository categoriaRepository;
    private final GeneroRepository generoRepository;
    private final ColorRepository colorRepository;
    private final TallaRepository tallaRepository;
    private final EstiloRepository estiloRepository;
    private final ProductoRepository productoRepository;
    private final MarcaRepository marcaRepository;

    public CatalogoService(CategoriaRepository categoriaRepository,
                           GeneroRepository generoRepository,
                           ColorRepository colorRepository,
                           TallaRepository tallaRepository,
                           EstiloRepository estiloRepository,
                           ProductoRepository productoRepository,
                           MarcaRepository marcaRepository) {
        this.categoriaRepository = categoriaRepository;
        this.generoRepository = generoRepository;
        this.colorRepository = colorRepository;
        this.tallaRepository = tallaRepository;
        this.estiloRepository = estiloRepository;
        this.productoRepository = productoRepository;
        this.marcaRepository = marcaRepository;
    }

    public List<Map<String, Object>> getCategorias() {
        return categoriaRepository.findByIdEstadoOrderByNombreAsc(1).stream()
                .map(c -> row("id_categoria", c.getIdCategoria(), "nombre", c.getNombre()))
                .toList();
    }

    public void addCategoria(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Nombre requerido");
        }
        String normalizedName = nombre.trim();
        if (categoriaRepository.existsByNombreIgnoreCase(normalizedName)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "La categoría “" + normalizedName + "” ya existe; no se creó un duplicado");
        }
        categoriaRepository.save(new Categoria(normalizedName.toUpperCase(Locale.ROOT), 1));
    }

    public boolean categoriaExists(String nombre) {
        return nombre != null && !nombre.isBlank()
                && categoriaRepository.existsByNombreIgnoreCase(nombre.trim());
    }

    public List<Map<String, Object>> getGeneros() {
        return generoRepository.findByIdEstado(1).stream()
                .map(g -> row("id_genero", g.getIdGenero(), "nombre", g.getNombre()))
                .toList();
    }

    public List<Map<String, Object>> getColores() {
        return colorRepository.findByIdEstadoOrderByNombreAsc(1).stream()
                .map(c -> row("id_color", c.getIdColor(), "nombre", c.getNombre()))
                .toList();
    }

    public Long addColor(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Nombre requerido");
        }
        String normalizedName = nombre.trim();
        if (colorRepository.existsByNombreIgnoreCase(normalizedName)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "El color “" + normalizedName + "” ya existe; no se creó un duplicado");
        }
        return colorRepository.save(new Color(normalizedName.toUpperCase(Locale.ROOT), 1))
                .getIdColor().longValue();
    }

    public boolean colorExists(String nombre) {
        return nombre != null && !nombre.isBlank()
                && colorRepository.existsByNombreIgnoreCase(nombre.trim());
    }

    @Transactional
    public void addMarca(String nombre, String nombreCategoria) {
        String normalizedName = requireText(nombre, "El nombre de la marca es obligatorio");
        Integer idCategoria = activeCatalogId(getCategorias(), nombreCategoria,
                "id_categoria", "categoría");
        if (marcaRepository.findFirstByNombreIgnoreCase(normalizedName).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "La marca “" + normalizedName + "” ya existe; no se creó un duplicado");
        }
        marcaRepository.save(new Marca(normalizedName, idCategoria, 1));
    }

    public boolean marcaExists(String nombre) {
        return nombre != null && !nombre.isBlank()
                && marcaRepository.findFirstByNombreIgnoreCase(nombre.trim()).isPresent();
    }

    @Transactional
    public void addEstilo(String nombre, String nombreMarca) {
        String normalizedName = requireText(nombre, "El nombre del estilo es obligatorio");
        Long idMarca = null;
        if (nombreMarca != null && !nombreMarca.isBlank()) {
            String normalizedBrand = nombreMarca.trim();
            idMarca = marcaRepository.findFirstByNombreIgnoreCaseAndIdEstado(normalizedBrand, 1)
                    .map(Marca::getIdMarca)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                            "La marca “" + normalizedBrand + "” no existe o no está activa"));
        }
        if (estiloRepository.findFirstByNombreIgnoreCase(normalizedName).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "El estilo “" + normalizedName + "” ya existe; no se creó un duplicado");
        }
        estiloRepository.save(new Estilo(normalizedName, idMarca, 1));
    }

    public boolean estiloExists(String nombre) {
        return nombre != null && !nombre.isBlank()
                && estiloRepository.findFirstByNombreIgnoreCase(nombre.trim()).isPresent();
    }

    @Transactional
    public void addTalla(String valor, String nombreCategoria, String nombreGenero) {
        String normalizedValue = requireText(valor, "La talla es obligatoria");
        Integer idCategoria = activeCatalogId(getCategorias(), nombreCategoria,
                "id_categoria", "categoría");
        Integer idGenero = activeCatalogId(getGeneros(), nombreGenero,
                "id_genero", "género");
        if (!tallaRepository.findByValorIgnoreCaseAndIdCategoriaAndIdGenero(
                normalizedValue, idCategoria, idGenero).isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "La talla “" + normalizedValue + "” ya existe para la categoría “"
                            + nombreCategoria.trim() + "” y el género “"
                            + nombreGenero.trim() + "”; no se creó un duplicado");
        }
        tallaRepository.save(new Talla(normalizedValue, idCategoria, idGenero, 1));
    }

    public boolean tallaExists(String valor, String nombreCategoria, String nombreGenero) {
        String normalizedValue = requireText(valor, "La talla es obligatoria");
        Integer idCategoria = activeCatalogId(getCategorias(), nombreCategoria,
                "id_categoria", "categoría");
        Integer idGenero = activeCatalogId(getGeneros(), nombreGenero,
                "id_genero", "género");
        return !tallaRepository.findByValorIgnoreCaseAndIdCategoriaAndIdGenero(
                normalizedValue, idCategoria, idGenero).isEmpty();
    }

    public List<Map<String, Object>> getTallas() {
        return tallaRepository.findActiveTallasWithCategoryAndGender().stream()
                .map(t -> row("id_talla", t.getIdTalla(), "valor", t.getValor(),
                        "id_genero", t.getIdGenero(), "genero", t.getGenero(),
                        "id_categoria", t.getIdCategoria(), "categoria", t.getCategoria()))
                .toList();
    }

    public List<Map<String, Object>> getTallasPorCategoriaGenero(Integer idCategoria, Integer idGenero) {
        if (idCategoria == null || idGenero == null) return List.of();
        return tallaRepository
                .findDistinctByIdCategoriaAndIdGeneroAndIdEstadoOrderByIdTallaAsc(
                        idCategoria, idGenero, 1)
                .stream()
                .map(t -> row("valor", t.getValor(), "id_talla", t.getIdTalla()))
                .toList();
    }

    public List<Map<String, Object>> getTallasPorCategoria(Integer idCategoria) {
        if (idCategoria == null) return List.of();
        return tallaRepository.findDistinctActiveValuesByCategory(idCategoria).stream()
                .map(value -> row("talla", value))
                .toList();
    }

    public List<Map<String, Object>> getEstilosUnicos() {
        return estiloRepository.findByIdEstadoOrderByNombreAsc(1).stream()
                .map(Estilo::getNombre)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .map(String::trim)
                .distinct()
                .map(value -> row("nombre", value))
                .toList();
    }

    public List<Map<String, Object>> getMarcas() {
        return marcaRepository.findByIdEstadoOrderByNombreAsc(1).stream()
                .filter(m -> m.getNombre() != null && !m.getNombre().isBlank())
                .map(Marca::getNombre)
                .map(String::trim)
                .distinct()
                .map(nombre -> row("nombre", nombre))
                .toList();
    }

    public List<Map<String, Object>> getMarcasConCategoria() {
        return marcaRepository.findByIdEstadoOrderByNombreAsc(1).stream()
                .filter(m -> m.getNombre() != null && !m.getNombre().isBlank())
                .map(marca -> {
                    String categoria = marca.getIdCategoria() == null ? null
                            : categoriaRepository.findById(marca.getIdCategoria())
                                    .filter(c -> Integer.valueOf(1).equals(c.getIdEstado()))
                                    .map(Categoria::getNombre)
                                    .orElse(null);
                    return row("nombre", marca.getNombre().trim(), "categoria", categoria);
                })
                .toList();
    }

    public String getCategoriaParaMarca(String nombreMarca) {
        if (nombreMarca == null || nombreMarca.isBlank()) return null;
        return marcaRepository.findFirstByNombreIgnoreCaseAndIdEstado(nombreMarca.trim(), 1)
                .map(Marca::getIdCategoria)
                .filter(idCategoria -> idCategoria != null)
                .flatMap(categoriaRepository::findById)
                .filter(categoria -> Integer.valueOf(1).equals(categoria.getIdEstado()))
                .map(Categoria::getNombre)
                .orElse(null);
    }

    public List<Map<String, Object>> getTallasValidas(Integer idCategoria) {
        return tallaRepository.findDistinctActiveValuesForActiveVariants(idCategoria).stream()
                .map(value -> row("talla", value))
                .toList();
    }

    public List<Map<String, Object>> getNombresProductos() {
        return productoRepository.findDistinctActiveNamesNormalized().stream()
                .map(value -> row("nombre", value))
                .toList();
    }

    private static Map<String, Object> row(Object... fields) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < fields.length; i += 2) {
            result.put((String) fields[i], fields[i + 1]);
        }
        return result;
    }

    private static Integer activeCatalogId(List<Map<String, Object>> catalog, String name,
                                           String idKey, String label) {
        String normalizedName = requireText(name, "El " + label + " es obligatorio");
        String expected = normalizedName.trim().toLowerCase(Locale.ROOT);
        return catalog.stream()
                .filter(row -> expected.equals(String.valueOf(row.get("nombre"))
                        .trim().toLowerCase(Locale.ROOT)))
                .map(row -> ((Number) row.get(idKey)).intValue())
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                        "El " + label + " “" + normalizedName.trim()
                                + "” no existe o no está activo en el catálogo"));
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }
}
