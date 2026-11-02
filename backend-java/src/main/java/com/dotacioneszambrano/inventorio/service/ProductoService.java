package com.dotacioneszambrano.inventorio.service;

import com.dotacioneszambrano.inventorio.entity.Estilo;
import com.dotacioneszambrano.inventorio.entity.Marca;
import com.dotacioneszambrano.inventorio.entity.Producto;
import com.dotacioneszambrano.inventorio.entity.Talla;
import com.dotacioneszambrano.inventorio.entity.Variante;
import com.dotacioneszambrano.inventorio.dto.AddProductoRequest;
import com.dotacioneszambrano.inventorio.exception.ApiException;
import com.dotacioneszambrano.inventorio.repository.EstiloRepository;
import com.dotacioneszambrano.inventorio.repository.MarcaRepository;
import com.dotacioneszambrano.inventorio.repository.ProductoRepository;
import com.dotacioneszambrano.inventorio.repository.TallaRepository;
import com.dotacioneszambrano.inventorio.repository.VarianteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final VarianteRepository varianteRepository;
    private final MarcaRepository marcaRepository;
    private final EstiloRepository estiloRepository;
    private final TallaRepository tallaRepository;
    private final StockService stockService;

    public ProductoService(ProductoRepository productoRepository,
                           VarianteRepository varianteRepository,
                           MarcaRepository marcaRepository,
                           EstiloRepository estiloRepository,
                           TallaRepository tallaRepository,
                           StockService stockService) {
        this.productoRepository = productoRepository;
        this.varianteRepository = varianteRepository;
        this.marcaRepository = marcaRepository;
        this.estiloRepository = estiloRepository;
        this.tallaRepository = tallaRepository;
        this.stockService = stockService;
    }

    public List<Map<String, Object>> getProductos() {
        return mapRows(productoRepository.findActiveProductRows());
    }

    public InventorySearchResult searchInventory(InventoryFilters filters) {
        String query = cleanOptional(filters.query());
        String productName = cleanOptional(filters.productName());
        String brand = cleanOptional(filters.brand());
        String style = cleanOptional(filters.style());
        String color = cleanOptional(filters.color());
        String size = cleanOptional(filters.size());
        String category = cleanOptional(filters.category());
        String gender = cleanOptional(filters.gender());
        Double priceGreaterThan = filters.priceGreaterThan();
        Double priceLessThan = filters.priceLessThan();
        Double priceEquals = filters.priceEquals();
        validatePriceFilters(priceGreaterThan, priceLessThan, priceEquals);
        if (query == null && productName == null && brand == null && style == null
                && color == null && size == null && category == null && gender == null
                && priceGreaterThan == null && priceLessThan == null && priceEquals == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Indica al menos un criterio para consultar el inventario");
        }

        List<Object[]> rows = productoRepository.searchActiveInventory(query, productName,
                brand, style, color, size, category, gender, priceGreaterThan, priceLessThan,
                priceEquals, PageRequest.of(0, 20));
        long matchingVariants = productoRepository.countActiveInventory(query, productName,
                brand, style, color, size, category, gender, priceGreaterThan, priceLessThan,
                priceEquals);
        long totalStock = productoRepository.sumActiveInventoryStock(query, productName,
                brand, style, color, size, category, gender, priceGreaterThan, priceLessThan,
                priceEquals);
        return new InventorySearchResult(mapRows(rows), matchingVariants, totalStock);
    }

    private static void validatePriceFilters(Double greaterThan, Double lessThan, Double equals) {
        for (Double price : new Double[]{greaterThan, lessThan, equals}) {
            if (price != null && (!Double.isFinite(price) || price < 0)) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Los filtros de precio deben ser números válidos no negativos");
            }
        }
        if (equals != null && (greaterThan != null || lessThan != null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "No combines un precio exacto con un límite de precio");
        }
        if (greaterThan != null && lessThan != null && greaterThan >= lessThan) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "El límite de precio mayor debe ser inferior al límite menor");
        }
    }

    public String describeVariant(Long idVariante) {
        if (idVariante == null || idVariante <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Variante inválida");
        }
        ProductoRepository.ActiveVariantDescription variant = productoRepository
                .findActiveProductRowByVariantId(idVariante)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Variante activa no encontrada"));
        return "producto " + variant.getProductName() + ", categoría " + variant.getCategory()
                + ", marca " + variant.getBrand()
                + ", estilo " + variant.getStyle() + ", color " + variant.getColor()
                + ", talla " + variant.getSize() + ", precio actual $" + variant.getPrice()
                + ", stock actual " + variant.getStock();
    }

    public String describeProduct(Long idProducto) {
        Producto producto = findActiveProduct(idProducto);
        return producto.getNombre() + " (ID " + producto.getIdProducto() + ")";
    }

    public boolean productExists(AddProductoRequest request) {
        if (request == null || request.id_categoria() == null || request.id_genero() == null
                || request.nombre() == null || request.nombre().isBlank()) {
            return false;
        }
        String brandName = cleanOptional(request.marca());
        String styleName = cleanOptional(request.estilo());
        Long brandId = brandName == null ? null : marcaRepository
                .findFirstByNombreIgnoreCaseAndIdEstado(brandName, 1)
                .map(Marca::getIdMarca)
                .orElse(null);
        Long styleId = styleName == null ? null : estiloRepository
                .findFirstByNombreIgnoreCase(styleName)
                .map(Estilo::getIdEstilo)
                .orElse(null);
        if ((brandName != null && brandId == null) || (styleName != null && styleId == null)) {
            return false;
        }
        Producto producto = productoRepository
                .findFirstByNombreIgnoreCaseAndIdCategoriaAndIdGeneroAndIdMarcaAndIdEstilo(
                        request.nombre().trim(), request.id_categoria(), request.id_genero(),
                        brandId, styleId)
                .orElse(null);
        if (producto == null || !Integer.valueOf(1).equals(producto.getIdEstado())
                || request.variantes() == null || request.variantes().isEmpty()) {
            return false;
        }
        return request.variantes().stream().allMatch(input -> {
            if (input == null || input.talla() == null || input.precio() == null
                    || !Double.isFinite(input.precio())) return false;
            return tallaRepository.findByValorIgnoreCaseAndIdCategoriaAndIdGenero(
                            input.talla().trim(), request.id_categoria(), request.id_genero())
                    .stream()
                    .anyMatch(talla -> varianteRepository
                            .findAllByIdProductoAndIdColorAndIdTalla(
                                    producto.getIdProducto(),
                                    request.id_color() == null ? 0 : request.id_color(),
                                    talla.getIdTalla())
                            .stream()
                            .anyMatch(variante -> Integer.valueOf(1).equals(variante.getIdEstado())
                                    && variante.getPrecio() != null
                                    && BigDecimal.valueOf(variante.getPrecio())
                                            .compareTo(BigDecimal.valueOf(input.precio())) == 0));
        });
    }

    private List<Map<String, Object>> mapRows(List<Object[]> rows) {
        String[] keys = {"id_producto", "nomproducto", "categoria", "id_variante", "marca",
                "estilo", "color", "talla", "precio", "stock"};
        return rows.stream().map(values -> {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 0; i < keys.length; i++) {
                row.put(keys[i], values[i]);
            }
            return row;
        }).toList();
    }

    @Transactional
    public void addProducto(AddProductoRequest req) {
        if (req.id_genero() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Género requerido");
        }
        if (req.id_categoria() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Categoría requerida");
        }
        String submittedName = req.nombre() == null ? "" : req.nombre().trim();
        if (submittedName.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Nombre requerido");
        }
        String nombre = submittedName;
        if (req.variantes() == null || req.variantes().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe tener variantes");
        }

        String marcaNombre = cleanOptional(req.marca());
        String estiloNombre = cleanOptional(req.estilo());
        Integer idCategoria = req.id_categoria();
        Integer idGenero = req.id_genero();
        Integer idColor = req.id_color() == null ? 0 : req.id_color();

        Marca existingBrand = marcaNombre == null ? null : marcaRepository
                .findFirstByNombreIgnoreCase(marcaNombre).orElse(null);
        if (existingBrand != null && !Integer.valueOf(1).equals(existingBrand.getIdEstado())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "La marca “" + marcaNombre + "” ya existe inactiva; no se duplicó");
        }
        Long idMarca = existingBrand == null
                ? (marcaNombre == null ? null : marcaRepository.save(
                        new Marca(marcaNombre, idCategoria, 1)).getIdMarca())
                : existingBrand.getIdMarca();

        Estilo existingStyle = estiloNombre == null ? null : estiloRepository
                .findFirstByNombreIgnoreCase(estiloNombre).orElse(null);
        if (existingStyle != null && !Integer.valueOf(1).equals(existingStyle.getIdEstado())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "El estilo “" + estiloNombre + "” ya existe inactivo; no se duplicó");
        }
        Long idEstilo = existingStyle == null
                ? (estiloNombre == null ? null : estiloRepository.save(
                        new Estilo(estiloNombre, idMarca, 1)).getIdEstilo())
                : existingStyle.getIdEstilo();

        Producto producto = productoRepository
                .findFirstByNombreIgnoreCaseAndIdCategoriaAndIdGeneroAndIdMarcaAndIdEstilo(
                        nombre, idCategoria, idGenero, idMarca, idEstilo)
                .orElse(null);
        if (producto == null) {
            producto = productoRepository.save(
                    new Producto(nombre, idMarca, idEstilo, idCategoria, idGenero, 1));
        } else if (!Integer.valueOf(1).equals(producto.getIdEstado())) {
            producto.setIdEstado(1);
            producto = productoRepository.save(producto);
        }

        Long idProducto = producto.getIdProducto();
        for (AddProductoRequest.VarianteInput input : req.variantes()) {
            if (input == null || input.talla() == null || input.talla().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Talla requerida");
            }
            if (input.precio() == null || !Double.isFinite(input.precio()) || input.precio() <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Precio inválido");
            }
            if (input.stock() == null || input.stock() <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "La cantidad debe ser mayor a cero");
            }

            List<Talla> matchingSizes = tallaRepository
                    .findByValorIgnoreCaseAndIdCategoriaAndIdGenero(
                            input.talla().trim(), idCategoria, idGenero);
            Talla talla = matchingSizes.stream()
                    .filter(existing -> Integer.valueOf(1).equals(existing.getIdEstado()))
                    .findFirst()
                    .orElse(null);
            if (talla == null && !matchingSizes.isEmpty()) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "La talla “" + input.talla() + "” ya existe, pero está inactiva; no se duplicó");
            }
            if (talla == null) {
                talla = tallaRepository.save(new Talla(
                        input.talla().trim().toUpperCase(Locale.ROOT), idCategoria, idGenero, 1));
            }

            BigDecimal precioEntrada = BigDecimal.valueOf(input.precio());
            Variante existente = varianteRepository
                    .findAllByIdProductoAndIdColorAndIdTalla(
                    idProducto, idColor, talla.getIdTalla())
                    .stream()
                    .filter(variante -> variante.getPrecio() != null
                    && BigDecimal.valueOf(variante.getPrecio()).compareTo(precioEntrada) == 0)
                    .sorted(Comparator.comparing(
                    variante -> !Integer.valueOf(1).equals(variante.getIdEstado())))
                    .findFirst()
                    .orElse(null);
            if (existente != null) {
                Variante variante = existente;
                variante.setIdEstado(1);
                varianteRepository.save(variante);
                stockService.entradaStock(variante.getIdVariante(), input.stock());
            } else {
                Variante variante = varianteRepository.save(new Variante(
                        idProducto, idColor, talla.getIdTalla(),
                        input.precio(), 0, 1));
                stockService.entradaStock(variante.getIdVariante(), input.stock());
            }
        }
    }

    @Transactional
    public int deleteProductos(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDs inválidos");
        }
        return hideVariants(ids.stream().map(Integer::longValue).toList());
    }

    @Transactional
    public int hideVariant(Long idVariante) {
        if (idVariante == null || idVariante <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Variante inválida");
        }
        varianteRepository.findByIdVarianteAndIdEstado(idVariante, 1)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Variante activa no encontrada"));
        int hidden = hideVariants(List.of(idVariante));
        if (hidden != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Variante activa no encontrada");
        }
        return hidden;
    }

    @Transactional
    public void hideProduct(Long idProducto) {
        if (idProducto == null || idProducto <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Producto inválido");
        }
        Producto producto = productoRepository.findById(idProducto)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        if (!Integer.valueOf(1).equals(producto.getIdEstado())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Producto activo no encontrado");
        }
        List<Long> variantIds = varianteRepository
                .findAllByIdProductoAndIdEstado(idProducto, 1).stream()
                .map(Variante::getIdVariante)
                .toList();
        if (!variantIds.isEmpty()) {
            hideVariants(variantIds);
        }
        producto.setIdEstado(2);
        productoRepository.save(producto);
    }

    @Transactional
    public void renameProduct(Long idProducto, String nombre) {
        if (idProducto == null || idProducto <= 0 || nombre == null || nombre.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Producto y nombre son requeridos");
        }
        Producto producto = findActiveProduct(idProducto);
        producto.setNombre(nombre.trim());
        productoRepository.save(producto);
    }

    @Transactional
    public void updateVariantPrice(Long idVariante, Double precio) {
        if (idVariante == null || idVariante <= 0 || precio == null
                || !Double.isFinite(precio) || precio <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Variante y precio deben ser válidos");
        }
        Variante variante = varianteRepository.findByIdVarianteAndIdEstado(idVariante, 1)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Variante no encontrada"));
        variante.setPrecio(precio);
        varianteRepository.save(variante);
    }

    private Producto findActiveProduct(Long idProducto) {
        if (idProducto == null || idProducto <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Producto inválido");
        }
        Producto producto = productoRepository.findById(idProducto)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        if (!Integer.valueOf(1).equals(producto.getIdEstado())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Producto activo no encontrado");
        }
        return producto;
    }

    private int hideVariants(List<Long> variantIds) {
        List<Long> productIds = productoRepository.findIdsWithSelectedVariants(variantIds);
        int deleted = varianteRepository.updateStateByIds(variantIds, 2);
        if (!productIds.isEmpty()) {
            List<Long> productIdsWithoutActiveVariants =
                    productoRepository.findIdsWithoutActiveVariants(productIds);
            if (!productIdsWithoutActiveVariants.isEmpty()) {
                List<Producto> products = productoRepository.findAllById(productIdsWithoutActiveVariants);
                products.forEach(product -> product.setIdEstado(2));
                productoRepository.saveAll(products);
            }
        }
        return deleted;
    }

    private static String cleanOptional(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    public record InventoryFilters(
            String query,
            String productName,
            String brand,
            String style,
            String color,
            String size,
            String category,
            String gender,
            Double priceGreaterThan,
            Double priceLessThan,
            Double priceEquals
    ) {}

    public record InventorySearchResult(
            List<Map<String, Object>> rows,
            long matchingVariants,
            long totalStock
    ) {}
}
