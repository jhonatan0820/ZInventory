package com.dotacioneszambrano.inventorio.voiceassistant.service;

import com.dotacioneszambrano.inventorio.dto.AddProductoRequest;
import com.dotacioneszambrano.inventorio.exception.ApiException;
import com.dotacioneszambrano.inventorio.repository.MovimientoInventarioRepository;
import com.dotacioneszambrano.inventorio.service.CatalogoService;
import com.dotacioneszambrano.inventorio.service.ProductoService;
import com.dotacioneszambrano.inventorio.service.StockService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
public class VoiceAssistantActionService {

    private static final String PENDING_ACTION = VoiceAssistantActionService.class.getName() + ".pending";
    private static final String PENDING_PRODUCT_CATALOGS =
            VoiceAssistantActionService.class.getName() + ".pending-product-catalogs";
    private static final String PENDING_BULK_DUPLICATES =
            VoiceAssistantActionService.class.getName() + ".pending-bulk-duplicates";
    private static final List<String> READ_ONLY_TOOLS = List.of(
            "search_products", "get_variant", "list_catalog", "recent_movements", "last_sale");

    private final CatalogoService catalogoService;
    private final ProductoService productoService;
    private final StockService stockService;
    private final MovimientoInventarioRepository movimientoRepository;

    public VoiceAssistantActionService(CatalogoService catalogoService,
                                       ProductoService productoService,
                                       StockService stockService,
                                       MovimientoInventarioRepository movimientoRepository) {
        this.catalogoService = catalogoService;
        this.productoService = productoService;
        this.stockService = stockService;
        this.movimientoRepository = movimientoRepository;
    }

    private void executeBulk(BulkRegistration batch) {
        batch.colors().forEach(catalogoService::addColor);
        batch.sizes().forEach(size ->
                catalogoService.addTalla(size.size(), size.category(), size.gender()));
        batch.brands().forEach(brand ->
                catalogoService.addMarca(brand.name(), brand.category()));
        batch.styles().forEach(style ->
                catalogoService.addEstilo(style.name(), style.brand()));
        batch.products().forEach(this::createProduct);
    }

    private TurnResult addCategory(JsonNode arguments, HttpSession session) {
        String name = nameArgument(arguments);
        if (catalogoService.categoriaExists(name)) {
            return TurnResult.message("La categoría “" + name
                    + "” ya existe; no crearé un duplicado.");
        }
        return propose(session, "ADD_CATEGORY", name);
    }

    private TurnResult addColor(JsonNode arguments, HttpSession session) {
        String name = nameArgument(arguments);
        if (catalogoService.colorExists(name)) {
            return TurnResult.message("El color “" + name
                    + "” ya existe; no crearé un duplicado.");
        }
        return propose(session, "ADD_COLOR", name);
    }

    private TurnResult addSize(JsonNode arguments, HttpSession session) {
        String size = requiredText(arguments, "size");
        String category = requiredText(arguments, "category");
        String gender = requiredText(arguments, "gender");
        if (catalogoService.tallaExists(size, category, gender)) {
            return TurnResult.message("La talla “" + size + "” ya existe para la categoría “"
                    + category + "” y el género “" + gender + "”; no crearé un duplicado.");
        }
        Map<String, String> sizeDetails = Map.of(
                "size", size,
                "category", category,
                "gender", gender);
        return propose(session, "ADD_SIZE", sizeDetails,
                "Agregar talla “" + size + "” para la categoría “" + category
                        + "” y el género “" + gender + "”.");
    }

    private TurnResult bulkAdd(JsonNode arguments, HttpSession session) {
        BulkRegistration batch = parseBulkRegistration(arguments);
        List<String> skipped = new ArrayList<>();
        batch = excludeExistingCatalogValues(batch, skipped);
        List<String> missing = validateBulkCatalogs(batch);
        missing.addAll(validateBulkProducts(batch));
        if (!missing.isEmpty()) {
            return TurnResult.message("Antes de proponer el lote necesito completar o corregir: "
                    + String.join("; ", missing) + ".");
        }
        List<String> repeatedVariants = findRepeatedVariantsInBatch(batch.products());
        if (!repeatedVariants.isEmpty()) {
            return TurnResult.message("El lote contiene presentaciones repetidas: "
                    + String.join("; ", repeatedVariants)
                    + ". Corrige o consolida esas filas y vuelve a enviarlo; no se guardó nada.");
        }
        List<String> duplicateProducts = findExistingProductDuplicates(batch.products());
        if (!duplicateProducts.isEmpty()) {
            synchronized (session) {
                session.setAttribute(PENDING_BULK_DUPLICATES, batch);
            }
            return TurnResult.bulkDecision(
                    "Encontré presentaciones de producto que ya existen: "
                            + String.join("; ", duplicateProducts)
                            + ". Para esas presentaciones, ¿quieres agregar las cantidades al stock "
                            + "o excluirlas del lote? Los duplicados de catálogos se informarán y "
                            + "se omitirán.");
        }
        return proposeBulk(batch, skipped, session);
    }

    private TurnResult resolveBulkDuplicates(JsonNode arguments, HttpSession session) {
        String decision = requiredText(arguments, "decision").toLowerCase(Locale.ROOT);
        if (!"add_stock".equals(decision) && !"omit".equals(decision)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "La decisión debe ser add_stock u omit");
        }
        synchronized (session) {
            Object value = session.getAttribute(PENDING_BULK_DUPLICATES);
            if (!(value instanceof BulkRegistration pending)) {
                throw new ApiException(HttpStatus.NOT_FOUND,
                        "No hay un lote pendiente de decidir sobre duplicados");
            }
            List<String> skipped = new ArrayList<>();
            List<String> stockAdditions = List.of();
            BulkRegistration batch = pending;
            if ("omit".equals(decision)) {
                batch = omitExistingProductVariants(batch, skipped);
            } else {
                stockAdditions = findExistingProductDuplicates(batch.products());
            }
            TurnResult result = proposeBulk(batch, skipped, stockAdditions, session);
            session.removeAttribute(PENDING_BULK_DUPLICATES);
            return result;
        }
    }

    private BulkRegistration parseBulkRegistration(JsonNode arguments) {
        if (arguments == null || !arguments.isObject()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Los datos del lote no son válidos");
        }
        List<String> colors = textArray(arguments, "colors");
        List<SizeRegistration> sizes = objectArray(arguments, "sizes", item ->
                new SizeRegistration(requiredText(item, "size"),
                        requiredText(item, "category"), requiredText(item, "gender")));
        List<BrandRegistration> brands = objectArray(arguments, "brands", item ->
                new BrandRegistration(requiredText(item, "name"),
                        requiredText(item, "category")));
        List<StyleRegistration> styles = objectArray(arguments, "styles", item ->
                new StyleRegistration(requiredText(item, "name"), optionalText(item, "brand")));
        List<ProductCreation> products = objectArray(arguments, "products", item -> {
            ObjectNode draft = normalizeProductDraft(item);
            if (optionalText(draft, "category").isBlank()) {
                String brandName = optionalText(draft, "brand");
                String inferredCategory = isNotApplicable(brandName) ? null
                        : brands.stream()
                                .filter(brand -> normalize(brand.name()).equals(normalize(brandName)))
                                .map(BrandRegistration::category)
                                .findFirst()
                                .orElseGet(() -> catalogoService.getCategoriaParaMarca(brandName));
                if (inferredCategory == null || inferredCategory.isBlank()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "En el producto “" + optionalText(draft, "productName")
                                    + "” falta indicar una categoría existente; no se puede inferir "
                                    + "de una marca registrada");
                }
                draft.put("category", inferredCategory);
            }
            List<String> unspecified = unspecifiedOptionalAttributes(draft);
            if (!unspecified.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "En el producto “" + optionalText(draft, "productName")
                                + "” falta indicar " + String.join(" y ", unspecified)
                                + "; si no aplica, escribe NA");
            }
            ProductPreparation preparation = prepareProduct(draft);
            if (preparation.genderMissing()) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "El género “" + optionalText(draft, "gender")
                                + "” no existe en el catálogo activo");
            }
            return new ProductCreation(preparation.request(),
                    preparation.categoryToCreate(), preparation.colorToCreate());
        });
        int itemCount = colors.size() + sizes.size() + brands.size() + styles.size() + products.size();
        if (itemCount == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Incluye al menos un color, talla, marca, estilo o producto en el lote");
        }
        if (itemCount > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "El lote no puede superar 100 elementos");
        }
        return new BulkRegistration(colors, sizes, brands, styles, products);
    }

    private BulkRegistration excludeExistingCatalogValues(BulkRegistration batch,
                                                            List<String> skipped) {
        reportRepeated(batch.colors(), VoiceAssistantActionService::normalize,
                value -> "color " + value, skipped);
        reportRepeated(batch.sizes(), size ->
                        normalize(size.size()) + "|" + normalize(size.category()) + "|"
                                + normalize(size.gender()),
                size -> "talla " + size.size() + " (" + size.category() + ", "
                        + size.gender() + ")", skipped);
        reportRepeated(batch.brands(), brand -> normalize(brand.name()),
                brand -> "marca " + brand.name(), skipped);
        reportRepeated(batch.styles(), style -> normalize(style.name()),
                style -> "estilo " + style.name(), skipped);
        List<String> colors = distinct(batch.colors(), VoiceAssistantActionService::normalize)
                .stream().filter(color -> {
            if (!catalogoService.colorExists(color)) return true;
            skipped.add("color " + color);
            return false;
        }).toList();
        List<SizeRegistration> sizes = distinct(batch.sizes(), size ->
                normalize(size.size()) + "|" + normalize(size.category()) + "|"
                        + normalize(size.gender())).stream()
                .filter(size -> {
                    if (!catalogoService.tallaExists(size.size(), size.category(), size.gender())) {
                        return true;
                    }
                    skipped.add("talla " + size.size() + " (" + size.category()
                            + ", " + size.gender() + ")");
                    return false;
                })
                .toList();
        List<BrandRegistration> brands = distinct(batch.brands(),
                brand -> normalize(brand.name())).stream()
                .filter(brand -> {
                    if (!catalogoService.marcaExists(brand.name())) return true;
                    skipped.add("marca " + brand.name());
                    return false;
                })
                .toList();
        List<StyleRegistration> styles = distinct(batch.styles(),
                style -> normalize(style.name())).stream()
                .filter(style -> {
                    if (!catalogoService.estiloExists(style.name())) return true;
                    skipped.add("estilo " + style.name());
                    return false;
                })
                .toList();
        return new BulkRegistration(colors, sizes, brands, styles, batch.products());
    }

    private List<String> validateBulkCatalogs(BulkRegistration batch) {
        List<String> issues = new ArrayList<>();
        List<Map<String, Object>> categories = catalogoService.getCategorias();
        List<Map<String, Object>> genders = catalogoService.getGeneros();
        List<Map<String, Object>> activeBrands = catalogoService.getMarcasConCategoria();
        for (SizeRegistration size : batch.sizes()) {
            if (findCatalogRow(categories, size.category(), "nombre") == null) {
                issues.add("la categoría “" + size.category() + "” de la talla no está activa");
            }
            if (findCatalogRow(genders, size.gender(), "nombre") == null) {
                issues.add("el género “" + size.gender() + "” de la talla no está activo");
            }
        }
        for (BrandRegistration brand : batch.brands()) {
            if (findCatalogRow(categories, brand.category(), "nombre") == null) {
                issues.add("la categoría “" + brand.category() + "” de la marca “"
                        + brand.name() + "” no está activa");
            }
        }
        for (StyleRegistration style : batch.styles()) {
            if (style.brand().isBlank()) continue;
            boolean brandExists = activeBrands.stream().anyMatch(brand ->
                    normalize(String.valueOf(brand.get("nombre")))
                            .equals(normalize(style.brand())))
                    || batch.brands().stream().anyMatch(brand ->
                    normalize(brand.name()).equals(normalize(style.brand())));
            if (!brandExists) {
                issues.add("la marca “" + style.brand() + "” del estilo “" + style.name()
                        + "” no está activa ni incluida en el lote");
            }
        }
        return issues;
    }

    private List<String> validateBulkProducts(BulkRegistration batch) {
        List<String> missing = new ArrayList<>();
        List<Map<String, Object>> activeBrands = catalogoService.getMarcasConCategoria();
        List<Map<String, Object>> activeStyles = catalogoService.getEstilosUnicos();
        for (ProductCreation product : batch.products()) {
            AddProductoRequest request = product.request();
            if (product.categoryToCreate() != null) {
                missing.add("la categoría “" + product.categoryToCreate()
                        + "” del producto “" + request.nombre() + "” no existe; usa una categoría activa");
                continue;
            }
            if (request.id_categoria() == null || request.id_genero() == null) {
                missing.add("categoría y género activos para “" + request.nombre() + "”");
                continue;
            }

            String categoryName = categoryName(request.id_categoria());
            String brand = request.marca();
            if (brand != null && !brand.isBlank() && !isNotApplicable(brand)) {
                Map<String, Object> activeBrand = activeBrands.stream()
                        .filter(row -> normalize(String.valueOf(row.get("nombre")))
                                .equals(normalize(brand))).findFirst().orElse(null);
                boolean plannedBrand = batch.brands().stream().anyMatch(candidate ->
                        normalize(candidate.name()).equals(normalize(brand)));
                if (activeBrand == null && !plannedBrand) {
                    missing.add("agrega la marca “" + brand + "” con su categoría o selecciona una activa");
                } else {
                    String brandCategory = activeBrand == null
                            ? batch.brands().stream()
                                    .filter(candidate -> normalize(candidate.name()).equals(normalize(brand)))
                                    .map(BrandRegistration::category).findFirst().orElse("")
                            : String.valueOf(activeBrand.get("categoria"));
                    if (!normalize(categoryName).equals(normalize(brandCategory))) {
                        missing.add("la marca “" + brand + "” pertenece a la categoría “"
                                + brandCategory + "”, no a “" + categoryName + "”");
                    }
                }
            }

            String style = request.estilo();
            if (style != null && !style.isBlank() && !isNotApplicable(style)) {
                boolean activeStyle = activeStyles.stream().anyMatch(row ->
                        normalize(String.valueOf(row.get("nombre"))).equals(normalize(style)));
                boolean plannedStyle = batch.styles().stream().anyMatch(candidate ->
                        normalize(candidate.name()).equals(normalize(style)));
                if (!activeStyle && !plannedStyle) {
                    missing.add("agrega el estilo “" + style + "” al lote o selecciona uno activo");
                }
            }

            String color = product.colorToCreate();
            if (color != null && batch.colors().stream()
                    .noneMatch(candidate -> normalize(candidate).equals(normalize(color)))) {
                missing.add("agrega el color “" + color + "” al lote o selecciona uno existente");
            }
            for (AddProductoRequest.VarianteInput variant : request.variantes()) {
                boolean sizeExists = catalogoService.getTallasPorCategoriaGenero(
                                request.id_categoria(), request.id_genero()).stream()
                        .anyMatch(size -> normalize(String.valueOf(size.get("valor")))
                                .equals(normalize(variant.talla())));
                boolean sizePlanned = batch.sizes().stream().anyMatch(size ->
                        normalize(size.size()).equals(normalize(variant.talla()))
                                && normalize(size.category()).equals(normalize(categoryName(request.id_categoria())))
                                && normalize(size.gender()).equals(normalize(genderName(request.id_genero()))));
                if (!sizeExists && !sizePlanned) {
                    missing.add("agrega la talla “" + variant.talla() + "” para "
                            + categoryName(request.id_categoria()) + " / "
                            + genderName(request.id_genero()) + " al lote de tallas");
                }
            }
        }
        return missing.stream().distinct().toList();
    }

    private List<String> findRepeatedVariantsInBatch(List<ProductCreation> products) {
        List<String> repeated = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (ProductCreation product : products) {
            AddProductoRequest request = product.request();
            for (AddProductoRequest.VarianteInput variant : request.variantes()) {
                String key = String.join("|",
                        normalize(request.nombre()),
                        String.valueOf(request.id_genero()),
                        String.valueOf(request.id_categoria()),
                        normalize(request.marca()),
                        normalize(request.estilo()),
                        request.id_color() == null
                                ? normalize(product.colorToCreate())
                                : String.valueOf(request.id_color()),
                        normalize(variant.talla()),
                        Double.toString(variant.precio()));
                if (!seen.add(key)) {
                    repeated.add(request.nombre() + " / talla " + variant.talla()
                            + " / $" + variant.precio());
                }
            }
        }
        return repeated;
    }

    private List<String> findExistingProductDuplicates(List<ProductCreation> products) {
        List<String> duplicates = new ArrayList<>();
        for (ProductCreation product : products) {
            AddProductoRequest request = product.request();
            for (AddProductoRequest.VarianteInput variant : request.variantes()) {
                if (productoService.productExists(withVariant(request, variant))) {
                    duplicates.add(request.nombre() + " / talla " + variant.talla()
                            + " / color " + displayProductColor(product)
                            + " / " + variant.stock() + " unidades a $" + variant.precio());
                }
            }
        }
        return duplicates;
    }

    private BulkRegistration omitExistingProductVariants(BulkRegistration batch,
                                                          List<String> skipped) {
        List<ProductCreation> products = new ArrayList<>();
        for (ProductCreation product : batch.products()) {
            AddProductoRequest request = product.request();
            List<AddProductoRequest.VarianteInput> newVariants = request.variantes().stream()
                    .filter(variant -> {
                        if (!productoService.productExists(withVariant(request, variant))) return true;
                        skipped.add(request.nombre() + " / talla " + variant.talla()
                                + " / color " + displayProductColor(product)
                                + " / $" + variant.precio());
                        return false;
                    })
                    .toList();
            if (!newVariants.isEmpty()) {
                products.add(new ProductCreation(new AddProductoRequest(
                        request.id_genero(), request.id_categoria(), request.nombre(),
                        request.marca(), request.estilo(), request.id_color(), newVariants),
                        product.categoryToCreate(), product.colorToCreate()));
            }
        }
        return new BulkRegistration(batch.colors(), batch.sizes(), batch.brands(),
                batch.styles(), products);
    }

    private TurnResult proposeBulk(BulkRegistration batch, List<String> skipped,
                                   HttpSession session) {
        return proposeBulk(batch, skipped, List.of(), session);
    }

    private TurnResult proposeBulk(BulkRegistration batch, List<String> skipped,
                                   List<String> stockAdditions, HttpSession session) {
        if (batch.isEmpty()) {
            return TurnResult.message("No hay elementos nuevos para agregar."
                    + (skipped.isEmpty() ? "" : " Se omitieron: " + String.join(", ", skipped) + "."));
        }
        String summary = summarizeBulk(batch);
        if (!stockAdditions.isEmpty()) {
            summary += " Se sumarán al stock las cantidades indicadas para estas presentaciones "
                    + "que ya existen: " + String.join("; ", stockAdditions) + ".";
        }
        if (!skipped.isEmpty()) {
            summary += " No se incluirán duplicados del lote o ya existentes: "
                    + String.join(", ", skipped) + ".";
        }
        synchronized (session) {
            session.removeAttribute(PENDING_BULK_DUPLICATES);
        }
        return propose(session, "BULK_ADD", batch, summary);
    }

    private String summarizeBulk(BulkRegistration batch) {
        List<String> summary = new ArrayList<>();
        if (!batch.colors().isEmpty()) summary.add("colores " + String.join(", ", batch.colors()));
        if (!batch.sizes().isEmpty()) summary.add("tallas " + batch.sizes().stream()
                .map(size -> size.size() + " (" + size.category() + ", " + size.gender() + ")")
                .reduce((first, second) -> first + ", " + second).orElse(""));
        if (!batch.brands().isEmpty()) summary.add("marcas " + batch.brands().stream()
                .map(brand -> brand.name() + " (" + brand.category() + ")")
                .reduce((first, second) -> first + ", " + second).orElse(""));
        if (!batch.styles().isEmpty()) summary.add("estilos " + batch.styles().stream()
                .map(style -> style.brand().isBlank() ? style.name()
                        : style.name() + " (" + style.brand() + ")")
                .reduce((first, second) -> first + ", " + second).orElse(""));
        if (!batch.products().isEmpty()) summary.add("productos " + batch.products().stream()
                .map(product -> {
                    AddProductoRequest request = product.request();
                    String color = product.colorToCreate() != null
                            ? product.colorToCreate()
                            : displayOptionalAttributeFromId(request.id_color());
                    return request.nombre() + " (categoría " + categoryName(request.id_categoria())
                            + ", género " + genderName(request.id_genero())
                            + ", color " + color
                            + ", marca " + displayOptionalText(request.marca())
                            + ", estilo " + displayOptionalText(request.estilo()) + ") ["
                        + request.variantes().stream()
                                .map(variant -> "talla " + variant.talla() + ", "
                                        + variant.stock() + " unidades a $" + variant.precio())
                                .reduce((first, second) -> first + "; " + second).orElse("")
                        + "]";
                })
                .reduce((first, second) -> first + "; " + second).orElse(""));
        return "Agregar en un solo lote: " + String.join("; ", summary) + ".";
    }

    private String displayProductColor(ProductCreation product) {
        return product.colorToCreate() != null
                ? product.colorToCreate()
                : displayOptionalAttributeFromId(product.request().id_color());
    }

    private static String displayOptionalText(String value) {
        return value == null || value.isBlank() || isNotApplicable(value) ? "no aplica" : value;
    }

    private static AddProductoRequest withVariant(AddProductoRequest request,
                                                   AddProductoRequest.VarianteInput variant) {
        return new AddProductoRequest(request.id_genero(), request.id_categoria(),
                request.nombre(), request.marca(), request.estilo(), request.id_color(),
                List.of(variant));
    }

    private String categoryName(Integer id) {
        return catalogoService.getCategorias().stream()
                .filter(row -> ((Number) row.get("id_categoria")).intValue() == id)
                .map(row -> String.valueOf(row.get("nombre"))).findFirst().orElse("");
    }

    private String genderName(Integer id) {
        return catalogoService.getGeneros().stream()
                .filter(row -> ((Number) row.get("id_genero")).intValue() == id)
                .map(row -> String.valueOf(row.get("nombre"))).findFirst().orElse("");
    }

    private String displayOptionalAttributeFromId(Integer idColor) {
        if (idColor == null) return "sin color";
        return catalogoService.getColores().stream()
                .filter(row -> ((Number) row.get("id_color")).intValue() == idColor)
                .map(row -> String.valueOf(row.get("nombre"))).findFirst().orElse("color nuevo");
    }

    private void createProduct(ProductCreation creation) {
        Integer idCategory = creation.request().id_categoria();
        if (idCategory == null) {
            String categoryName = creation.categoryToCreate();
            if (categoryName == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "La categoría del producto ya no está disponible");
            }
            catalogoService.addCategoria(categoryName);
            idCategory = findCatalogId(catalogoService.getCategorias(), categoryName, "id_categoria");
        }
        Integer idColor = creation.request().id_color();
        String colorName = creation.colorToCreate();
        if (idColor == null && colorName != null) {
            if (!catalogoService.colorExists(colorName)) catalogoService.addColor(colorName);
            idColor = findCatalogId(catalogoService.getColores(), colorName, "id_color");
        }
        if (idCategory == null || (idColor == null && colorName != null)) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudieron resolver los nuevos valores de catálogo");
        }
        AddProductoRequest request = creation.request();
        productoService.addProducto(new AddProductoRequest(
                request.id_genero(), idCategory, request.nombre(), request.marca(),
                request.estilo(), idColor, request.variantes()));
    }

    private TurnResult createProduct(JsonNode submittedDraft, HttpSession session) {
        ObjectNode draft = normalizeProductDraft(submittedDraft);
        String brand = optionalText(draft, "brand");
        String registeredBrandCategory = brand.isBlank()
                ? null : catalogoService.getCategoriaParaMarca(brand);
        if (registeredBrandCategory != null) {
            draft.put("category", registeredBrandCategory);
            draft = normalizeProductDraft(draft);
        } else if (optionalText(draft, "category").isBlank()) {
            String brandPrompt = brand.isBlank()
                    ? "No indicaste una marca registrada que permita inferirla."
                    : "La marca “" + brand + "” no está registrada o no tiene categoría asociada.";
            return TurnResult.message(brandPrompt
                    + " ¿Qué categoría debo usar para registrar el producto y asociarla a esa marca?");
        }
        return prepareProductDraft(draft, session);
    }

    private TurnResult prepareProductDraft(ObjectNode draft, HttpSession session) {
        List<String> unspecified = unspecifiedOptionalAttributes(draft);
        if (!unspecified.isEmpty()) {
            return TurnResult.message("No indicaste " + String.join(" ni ", unspecified)
                    + ". ¿Cuál corresponde? Si alguna no aplica, responde “NA” para esa característica.");
        }
        ProductPreparation preparation = prepareProduct(draft);
        if (preparation.genderMissing()) {
            synchronized (session) {
                session.removeAttribute(PENDING_PRODUCT_CATALOGS);
            }
            return TurnResult.message("El género “" + draft.path("gender").asText()
                    + "” no existe en el catálogo y no se puede crear desde aquí porque sus IDs "
                    + "son fijos. Elige un género existente: " + preparation.availableGenders() + ".");
        }
        if (!preparation.missing().isEmpty()) {
            synchronized (session) {
                session.setAttribute(PENDING_PRODUCT_CATALOGS,
                        new PendingProductCatalogs(draft, preparation.missing()));
            }
            return TurnResult.message("No están parametrizadas estas características: "
                    + String.join(", ", preparation.missing())
                    + ". ¿Quieres que las agregue al catálogo para crear el producto? "
                    + "Responde sí o no; todavía no se ha guardado ningún cambio.");
        }
        return proposeProduct(draft, preparation, session);
    }

    private TurnResult resolveMissingProductAttributes(JsonNode arguments, HttpSession session) {
        String decision = requiredText(arguments, "decision").toLowerCase(Locale.ROOT);
        if (!"yes".equals(decision) && !"no".equals(decision)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La decisión debe ser yes o no");
        }
        PendingProductCatalogs pending;
        synchronized (session) {
            Object value = session.getAttribute(PENDING_PRODUCT_CATALOGS);
            if (!(value instanceof PendingProductCatalogs proposal)) {
                throw new ApiException(HttpStatus.NOT_FOUND,
                        "No hay características pendientes por resolver");
            }
            pending = proposal;
            session.removeAttribute(PENDING_PRODUCT_CATALOGS);
        }
        if ("no".equals(decision)) {
            return TurnResult.message("Entendido. No agregaré esas características. "
                    + "Dime qué valor del catálogo existente quieres usar.");
        }

        ProductPreparation preparation = prepareProduct(pending.draft());
        if (preparation.genderMissing()) {
            return TurnResult.message("El género “" + pending.draft().path("gender").asText()
                    + "” no se puede crear desde el asistente porque sus IDs son fijos. "
                    + "Elige un género existente: " + preparation.availableGenders() + ".");
        }
        return proposeProduct(pending.draft(), preparation, session);
    }

    private TurnResult proposeProduct(ObjectNode draft, ProductPreparation preparation,
                                      HttpSession session) {
        AddProductoRequest request = preparation.request();
        if (productoService.productExists(request)) {
            return TurnResult.message("Ya existe esa presentación de “" + request.nombre()
                    + "” con la misma talla y precio; no crearé un duplicado. "
                    + "Si necesitas más unidades, usa la opción de ingresar stock.");
        }
        ProductCreation creation = new ProductCreation(
                request, preparation.categoryToCreate(), preparation.colorToCreate());
        return propose(session, "CREATE_PRODUCT", creation, summarizeProduct(draft, request));
    }

    private ProductPreparation prepareProduct(ObjectNode draft) {
        String category = requiredText(draft, "category");
        String gender = requiredText(draft, "gender");
        String color = requiredText(draft, "color");
        String brand = requiredText(draft, "brand");
        String style = requiredText(draft, "style");
        String productName = requiredText(draft, "productName");

        Map<String, Object> categoryRow = findCatalogRow(catalogoService.getCategorias(), category, "nombre");
        Map<String, Object> genderRow = findCatalogRow(catalogoService.getGeneros(), gender, "nombre");
        Map<String, Object> colorRow = isNotApplicable(color)
                ? null : findCatalogRow(catalogoService.getColores(), color, "nombre");
        List<Map<String, Object>> brands = catalogoService.getMarcas();
        List<Map<String, Object>> styles = catalogoService.getEstilosUnicos();
        String canonicalBrand = catalogName(brands, brand, "nombre");
        String canonicalStyle = catalogName(styles, style, "nombre");
        String canonicalCategory = categoryRow == null ? category : String.valueOf(categoryRow.get("nombre"));
        String canonicalColor = colorRow == null ? color : String.valueOf(colorRow.get("nombre"));
        if (categoryRow == null && catalogoService.categoriaExists(category)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "La categoría “" + category + "” ya existe, pero no está activa; no se duplicó");
        }
        if (!isNotApplicable(color) && colorRow == null && catalogoService.colorExists(color)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "El color “" + color + "” ya existe, pero no está activo; no se duplicó");
        }

        List<String> missing = new ArrayList<>();
        if (categoryRow == null) missing.add("categoría “" + category + "”");
        if (!isNotApplicable(brand) && canonicalBrand == null) missing.add("marca “" + brand + "”");
        if (!isNotApplicable(style) && canonicalStyle == null) missing.add("estilo “" + style + "”");
        if (!isNotApplicable(color) && colorRow == null) missing.add("color “" + color + "”");

        Integer idCategory = categoryRow == null ? null : ((Number) categoryRow.get("id_categoria")).intValue();
        Integer idGender = genderRow == null ? null : ((Number) genderRow.get("id_genero")).intValue();
        List<Map<String, Object>> sizes = idCategory != null && idGender != null
                ? catalogoService.getTallasPorCategoriaGenero(idCategory, idGender)
                : catalogoService.getTallas();
        JsonNode variants = draft.path("variants");
        if (!variants.isArray() || variants.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El producto necesita al menos una variante");
        }
        List<AddProductoRequest.VarianteInput> inputs = new ArrayList<>();
        for (JsonNode variant : variants) {
            String size = requiredText(variant, "size");
            double price = requiredPositiveDouble(variant, "price");
            int quantity = requiredPositiveInt(variant, "quantity");
            String canonicalSize = catalogName(sizes, size, "valor");
            if (canonicalSize == null) {
                missing.add("talla “" + size + "”");
                canonicalSize = size;
            }
            inputs.add(new AddProductoRequest.VarianteInput(canonicalSize, price, quantity));
        }
        boolean genderMissing = genderRow == null;
        String availableGenders = catalogoService.getGeneros().stream()
                .map(row -> String.valueOf(row.get("nombre")))
                .reduce((first, second) -> first + ", " + second)
                .orElse("ninguno");
        AddProductoRequest request = new AddProductoRequest(
                idGender, idCategory, productName,
                isNotApplicable(brand) ? "" : canonicalBrand == null ? brand : canonicalBrand,
                isNotApplicable(style) ? "" : canonicalStyle == null ? style : canonicalStyle,
                colorRow == null ? null : ((Number) colorRow.get("id_color")).intValue(),
                inputs);
        return new ProductPreparation(request, missing, genderMissing, availableGenders,
                categoryRow == null ? canonicalCategory : null,
                isNotApplicable(color) || colorRow != null ? null : canonicalColor);
    }

    private static List<String> unspecifiedOptionalAttributes(ObjectNode draft) {
        List<String> unspecified = new ArrayList<>();
        if (optionalText(draft, "brand").isBlank()) unspecified.add("la marca");
        if (optionalText(draft, "style").isBlank()) unspecified.add("el estilo");
        if (optionalText(draft, "color").isBlank()) unspecified.add("el color");
        return unspecified;
    }

    private static ObjectNode normalizeProductDraft(JsonNode submittedDraft) {
        if (submittedDraft == null || !submittedDraft.isObject()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Los datos del producto no son válidos");
        }
        ObjectNode draft = submittedDraft.deepCopy();
        String category = optionalText(draft, "category");
        String productName = optionalText(draft, "productName");
        boolean boots = normalize(category).contains("bota") || normalize(productName).contains("bota");
        if (boots) {
            draft.put("productName", "Botas de Seguridad");
            if (optionalText(draft, "color").isBlank()) {
                draft.put("color", "Negro");
            }
        }
        return draft;
    }

    private static boolean isNotApplicable(String value) {
        return switch (normalize(value)) {
            case "na", "n/a", "no aplica", "ninguna", "ninguno",
                    "sin marca", "sin estilo", "sin color" -> true;
            default -> false;
        };
    }

    private static String catalogName(List<Map<String, Object>> catalog, String value, String nameKey) {
        Map<String, Object> row = findCatalogRow(catalog, value, nameKey);
        if (row == null) return null;
        Object name = row.get(nameKey);
        return name == null ? null : String.valueOf(name).trim();
    }

    private static Map<String, Object> findCatalogRow(List<Map<String, Object>> catalog,
                                                       String value, String nameKey) {
        String expected = normalize(value);
        return catalog.stream()
                .filter(row -> expected.equals(normalize(String.valueOf(row.get(nameKey)))))
                .findFirst()
                .orElse(null);
    }

    public TurnResult handleTurn(JsonNode response, HttpSession session) {
        String operation = response.path("operation").asText("NONE").toUpperCase(Locale.ROOT);
        JsonNode arguments = response.path("arguments");
        if ("CREATE_PRODUCT".equals(operation)
                && !response.path("readyToConfirm").asBoolean()) {
            return TurnResult.message(response.path("message").asText());
        }
        return switch (operation) {
            case "NONE" -> TurnResult.message(response.path("message").asText());
            case "SEARCH_PRODUCTS" -> searchProducts(arguments);
            case "GET_VARIANT" -> describeVariant(arguments);
            case "LIST_CATALOG" -> listCatalog(arguments);
            case "RECENT_MOVEMENTS" -> recentMovements();
            case "LAST_SALE" -> lastSale(arguments);
            case "CREATE_PRODUCT" -> createProduct(response.path("draft"), session);
            case "ADD_CATEGORY" -> addCategory(arguments, session);
            case "ADD_COLOR" -> addColor(arguments, session);
            case "ADD_SIZE" -> addSize(arguments, session);
            case "BULK_ADD" -> bulkAdd(arguments, session);
            case "RECEIVE_STOCK" -> {
                Map<String, Object> args = stockArguments(arguments);
                String summary = "Ingresar " + args.get("cantidad") + " unidades a "
                        + productoService.describeVariant((Long) args.get("idVariante")) + ".";
                yield propose(session, operation, args, summary);
            }
            case "SELL_STOCK" -> {
                Map<String, Object> args = saleArguments(arguments);
                Long variantId = (Long) args.get("idVariante");
                Integer quantity = (Integer) args.get("cantidad");
                Double unitPrice = (Double) args.get("precioVenta");
                stockService.validarVenta(variantId, quantity, unitPrice);
                double total = quantity * unitPrice;
                String summary = "Vender " + quantity + " unidades de la presentación ID "
                        + variantId + ": " + productoService.describeVariant(variantId)
                        + "; precio de venta unitario $" + unitPrice + ", total de venta $"
                        + total + ".";
                yield propose(session, operation, args, summary);
            }
            case "UPDATE_PRODUCT_NAME" -> {
                Map<String, Object> args = productNameArguments(arguments);
                String summary = "Cambiar el nombre de " + productoService.describeProduct(
                        (Long) args.get("idProducto")) + " a “" + args.get("nombre") + "”.";
                yield propose(session, operation, args, summary);
            }
            case "UPDATE_VARIANT_PRICE" -> {
                Map<String, Object> args = variantPriceArguments(arguments);
                String summary = "Cambiar el precio de "
                        + productoService.describeVariant((Long) args.get("idVariante"))
                        + " a $" + args.get("precio") + ".";
                yield propose(session, operation, args, summary);
            }
            case "HIDE_PRODUCT" -> {
                Long id = idArguments(arguments, "idProducto");
                yield propose(session, operation, id,
                        "Ocultar " + productoService.describeProduct(id)
                                + " y sus variantes activas, cambiando estado (sin borrar registros).");
            }
            case "HIDE_VARIANT" -> {
                Long id = idArguments(arguments, "idVariante");
                yield propose(session, operation, id,
                        "Ocultar " + productoService.describeVariant(id)
                                + " cambiando estado (sin borrar el registro).");
            }
            default -> throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "El asistente solicitó una operación no permitida");
        };
    }

    @Transactional
    public TurnResult handleToolCall(String functionName, JsonNode arguments, HttpSession session) {
        synchronized (session) {
            boolean pending = session.getAttribute(PENDING_ACTION) != null;
            boolean resolvingAction = "confirm_pending_action".equals(functionName)
                    || "cancel_pending_action".equals(functionName);
            boolean resolvingDuplicates = "resolve_bulk_duplicates".equals(functionName);
            boolean awaitingDuplicateDecision =
                    session.getAttribute(PENDING_BULK_DUPLICATES) != null;
            boolean cancellingBulkDecision = awaitingDuplicateDecision
                    && "cancel_pending_action".equals(functionName);
            boolean awaitingCatalogDecision =
                    session.getAttribute(PENDING_PRODUCT_CATALOGS) != null;
            if (pending && !resolvingAction && !READ_ONLY_TOOLS.contains(functionName)) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Confirma o cancela el cambio pendiente antes de proponer otro cambio");
            }
            if (!pending && resolvingAction) {
                if (!("cancel_pending_action".equals(functionName)
                        && session.getAttribute(PENDING_BULK_DUPLICATES) != null)) {
                    throw new ApiException(HttpStatus.NOT_FOUND,
                            "No hay cambios pendientes por resolver");
                }
            }
            if (resolvingDuplicates && !awaitingDuplicateDecision) {
                throw new ApiException(HttpStatus.NOT_FOUND,
                        "No hay un lote pendiente de decidir sobre duplicados");
            }
            if (awaitingDuplicateDecision && !resolvingDuplicates && !cancellingBulkDecision
                    && !READ_ONLY_TOOLS.contains(functionName)) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Resuelve si deseas agregar los duplicados al stock o excluirlos antes de continuar");
            }
            if (awaitingCatalogDecision
                    && !"resolve_missing_product_attributes".equals(functionName)
                    && !READ_ONLY_TOOLS.contains(functionName)) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Responde si deseas crear las características faltantes antes de proponer otro cambio");
            }
        }
        if ("confirm_pending_action".equals(functionName)) {
            return confirmPendingActionByVoice(session);
        }
        if ("cancel_pending_action".equals(functionName)) {
            synchronized (session) {
                if (session.getAttribute(PENDING_ACTION) == null
                        && session.getAttribute(PENDING_BULK_DUPLICATES) != null) {
                    session.removeAttribute(PENDING_BULK_DUPLICATES);
                    return TurnResult.message("Lote cancelado. No se aplicó ningún cambio.");
                }
            }
            cancelPendingActionByVoice(session);
            return TurnResult.message("Cambio cancelado por voz. No se aplicó ningún cambio.");
        }
        if ("resolve_bulk_duplicates".equals(functionName)) {
            return resolveBulkDuplicates(arguments, session);
        }
        if ("bulk_add".equals(functionName)) {
            return bulkAdd(arguments, session);
        }
        String operation = switch (functionName) {
            case "search_products" -> "SEARCH_PRODUCTS";
            case "get_variant" -> "GET_VARIANT";
            case "list_catalog" -> "LIST_CATALOG";
            case "recent_movements" -> "RECENT_MOVEMENTS";
            case "last_sale" -> "LAST_SALE";
            case "create_product" -> "CREATE_PRODUCT";
            case "resolve_missing_product_attributes" -> "RESOLVE_MISSING_PRODUCT_ATTRIBUTES";
            case "add_category" -> "ADD_CATEGORY";
            case "add_color" -> "ADD_COLOR";
            case "add_size" -> "ADD_SIZE";
            case "receive_stock" -> "RECEIVE_STOCK";
            case "sell_stock" -> "SELL_STOCK";
            case "update_product_name" -> "UPDATE_PRODUCT_NAME";
            case "update_variant_price" -> "UPDATE_VARIANT_PRICE";
            case "hide_product" -> "HIDE_PRODUCT";
            case "hide_variant" -> "HIDE_VARIANT";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST,
                    "La herramienta solicitada no está permitida");
        };
        if (arguments == null || !arguments.isObject()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Los argumentos de la herramienta no son válidos");
        }
        if ("resolve_missing_product_attributes".equals(functionName)) {
            return resolveMissingProductAttributes(arguments, session);
        }
        ObjectNode response = JsonNodeFactory.instance.objectNode();
        response.put("operation", operation);
        response.set("arguments", arguments);
        if ("CREATE_PRODUCT".equals(operation)) {
            response.set("draft", arguments);
            response.put("readyToConfirm", true);
        }
        return handleTurn(response, session);
    }

    public TurnResult confirmPendingActionByVoice(HttpSession session) {
        synchronized (session) {
            PendingAction pending = getPending(session);
            execute(pending);
            session.removeAttribute(PENDING_ACTION);
            return TurnResult.message("Confirmado por voz. Cambio aplicado: " + pending.summary());
        }
    }

    public void cancelPendingActionByVoice(HttpSession session) {
        synchronized (session) {
            getPending(session);
            session.removeAttribute(PENDING_ACTION);
        }
    }

    public void ensureNoPendingAction(HttpSession session) {
        synchronized (session) {
            if (session.getAttribute(PENDING_ACTION) != null
                    || session.getAttribute(PENDING_BULK_DUPLICATES) != null
                    || session.getAttribute(PENDING_PRODUCT_CATALOGS) != null) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Resuelve la decisión pendiente del asistente antes de iniciar otro turno");
            }
        }
    }

    public Map<String, Object> pendingAction(HttpSession session) {
        synchronized (session) {
            Object pending = session.getAttribute(PENDING_ACTION);
            if (pending instanceof PendingAction action) {
                return Map.of(
                        "pending", true,
                        "actionId", action.id(),
                        "actionSummary", action.summary());
            }
            if (session.getAttribute(PENDING_BULK_DUPLICATES) != null) {
                return Map.of("pending", true, "pendingBulkDecision", true);
            }
            if (session.getAttribute(PENDING_PRODUCT_CATALOGS) != null) {
                return Map.of("pending", true, "pendingProductCatalogDecision", true);
            }
            return Map.of("pending", false);
        }
    }

    @Transactional
    public String confirm(String actionId, HttpSession session) {
        synchronized (session) {
            PendingAction pending = getPending(session);
            if (actionId == null || !pending.id().equals(actionId)) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "La confirmación no corresponde al cambio pendiente");
            }
            execute(pending);
            session.removeAttribute(PENDING_ACTION);
            return pending.summary();
        }
    }

    private void execute(PendingAction pending) {
        switch (pending.operation()) {
            case "CREATE_PRODUCT" -> createProduct((ProductCreation) pending.arguments());
            case "ADD_CATEGORY" -> catalogoService.addCategoria((String) pending.arguments());
            case "ADD_COLOR" -> catalogoService.addColor((String) pending.arguments());
            case "BULK_ADD" -> executeBulk((BulkRegistration) pending.arguments());
            case "ADD_SIZE" -> {
                Map<?, ?> args = (Map<?, ?>) pending.arguments();
                catalogoService.addTalla((String) args.get("size"),
                        (String) args.get("category"), (String) args.get("gender"));
            }
            case "RECEIVE_STOCK" -> {
                Map<?, ?> args = (Map<?, ?>) pending.arguments();
                stockService.entradaStock((Long) args.get("idVariante"), (Integer) args.get("cantidad"));
            }
            case "SELL_STOCK" -> {
                Map<?, ?> args = (Map<?, ?>) pending.arguments();
                stockService.actualizarStock((Long) args.get("idVariante"),
                        (Integer) args.get("cantidad"), (Double) args.get("precioVenta"));
            }
            case "UPDATE_PRODUCT_NAME" -> {
                Map<?, ?> args = (Map<?, ?>) pending.arguments();
                productoService.renameProduct((Long) args.get("idProducto"), (String) args.get("nombre"));
            }
            case "UPDATE_VARIANT_PRICE" -> {
                Map<?, ?> args = (Map<?, ?>) pending.arguments();
                productoService.updateVariantPrice((Long) args.get("idVariante"), (Double) args.get("precio"));
            }
            case "HIDE_PRODUCT" -> productoService.hideProduct((Long) pending.arguments());
            case "HIDE_VARIANT" -> productoService.hideVariant((Long) pending.arguments());
            default -> throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "La operación pendiente no está permitida");
        }
    }

    public void cancel(String actionId, HttpSession session) {
        synchronized (session) {
            PendingAction pending = getPending(session);
            if (actionId == null || !pending.id().equals(actionId)) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "La cancelación no corresponde al cambio pendiente");
            }
            session.removeAttribute(PENDING_ACTION);
        }
    }

    private TurnResult searchProducts(JsonNode arguments) {
        ProductoService.InventorySearchResult result = productoService.searchInventory(
                new ProductoService.InventoryFilters(
                        optionalText(arguments, "query"),
                        optionalText(arguments, "productName"),
                        optionalText(arguments, "brand"),
                        optionalText(arguments, "style"),
                        optionalText(arguments, "color"),
                        optionalText(arguments, "size"),
                        optionalText(arguments, "category"),
                        optionalText(arguments, "gender"),
                        optionalPrice(arguments, "priceGreaterThan"),
                        optionalPrice(arguments, "priceLessThan"),
                        optionalPrice(arguments, "priceEquals")));
        if (result.matchingVariants() == 0) {
            return TurnResult.message("No encontré productos que coincidan con los filtros de esta consulta.");
        }
        String totals = "Coincidencias totales: " + result.matchingVariants()
                + " presentaciones de producto; stock total: " + result.totalStock() + " unidades.";
        String details = formatRows("Productos encontrados (máximo 20):", result.rows());
        return TurnResult.message(totals + "\n" + details);
    }

    private TurnResult describeVariant(JsonNode arguments) {
        Long idVariante = idArguments(arguments, "idVariante");
        return TurnResult.message("ID de variante " + idVariante + ": "
                + productoService.describeVariant(idVariante));
    }

    private TurnResult listCatalog(JsonNode arguments) {
        String catalog = requiredText(arguments, "catalog").toLowerCase(Locale.ROOT);
        List<Map<String, Object>> rows = switch (catalog) {
            case "categories", "categorias" -> catalogoService.getCategorias();
            case "genders", "generos" -> catalogoService.getGeneros();
            case "colors", "colores" -> catalogoService.getColores();
            case "sizes", "tallas" -> catalogoService.getTallas();
            case "styles", "estilos" -> catalogoService.getEstilosUnicos();
            case "brands", "marcas" -> catalogoService.getMarcasConCategoria();
            default -> throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "El catálogo solicitado no está disponible");
        };
        return TurnResult.message(formatRows("Catálogo " + catalog + ":", rows));
    }

    private TurnResult recentMovements() {
        List<Map<String, Object>> rows = movimientoRepository.findRecentMovements();
        return TurnResult.message(formatRows("Movimientos recientes (solo lectura):", rows));
    }

    private TurnResult lastSale(JsonNode arguments) {
        JsonNode productIdNode = arguments.get("idProducto");
        Long productId = productIdNode == null || productIdNode.isNull()
                ? null : requiredPositiveLong(arguments, "idProducto");
        String productName = optionalText(arguments, "productName");
        if ((productId == null) == productName.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Indica el ID exacto o el nombre exacto del producto para consultar su última venta");
        }

        List<Map<String, Object>> rows = movimientoRepository.findLatestSale(
                productId, productName.isBlank() ? null : productName);
        if (rows.isEmpty()) {
            return TurnResult.message("No hay ventas registradas para ese producto.");
        }
        Map<String, Object> sale = rows.get(0);
        Object timestampEpoch = sale.get("fecha_venta_epoch");
        if (!(timestampEpoch instanceof Number epochSeconds)) {
            throw new IllegalStateException("La consulta de última venta no devolvió un timestamp válido");
        }
        LocalDateTime localDateTime = Instant.ofEpochSecond(epochSeconds.longValue())
                .atZone(ZoneId.of("America/Bogota"))
                .toLocalDateTime();
        int hour = localDateTime.getHour();
        int hour12 = hour % 12 == 0 ? 12 : hour % 12;
        String period = hour < 12 ? "a. m." : "p. m.";
        String colombiaTime = localDateTime.format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' uuuu",
                Locale.forLanguageTag("es-CO")))
                + " a las " + hour12 + ":"
                + String.format(Locale.ROOT, "%02d", localDateTime.getMinute()) + " " + period;
        return TurnResult.message("La última venta registrada fue el " + colombiaTime
                + ". La fecha y hora ya están convertidas a la hora local de Colombia. "
                + "Repítelas exactamente, sin convertirlas ni modificarlas. Producto: "
                + sale.get("producto")
                + ", talla " + sale.get("talla") + ", color " + sale.get("color")
                + ", cantidad " + sale.get("cantidad") + ", precio unitario $"
                + sale.get("precio_venta") + ", total $" + sale.get("total_venta") + ".");
    }

    private TurnResult propose(HttpSession session, String operation, Object arguments) {
        return propose(session, operation, arguments, summarize(operation, arguments));
    }

    private TurnResult propose(HttpSession session, String operation, Object arguments, String summary) {
        synchronized (session) {
            if (session.getAttribute(PENDING_ACTION) != null) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Confirma o cancela el cambio pendiente antes de proponer otro");
            }
            PendingAction pending = new PendingAction(
                    UUID.randomUUID().toString(), operation, arguments, summary);
            session.setAttribute(PENDING_ACTION, pending);
            return new TurnResult("Revisa el resumen. No haré ningún cambio hasta que lo confirmes.",
                    pending.id(), summary);
        }
    }

    private static String nameArgument(JsonNode arguments) {
        return requiredText(arguments, "name");
    }

    private static Map<String, Object> stockArguments(JsonNode arguments) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("idVariante", requiredPositiveLong(arguments, "idVariante"));
        result.put("cantidad", requiredPositiveInt(arguments, "cantidad"));
        return result;
    }

    private static Map<String, Object> saleArguments(JsonNode arguments) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("idVariante", requiredPositiveLong(arguments, "idVariante"));
        result.put("cantidad", requiredPositiveInt(arguments, "cantidad"));
        result.put("precioVenta", requiredPositiveDouble(arguments, "precioVenta"));
        return result;
    }

    private static Map<String, Object> productNameArguments(JsonNode arguments) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("idProducto", requiredPositiveLong(arguments, "idProducto"));
        result.put("nombre", requiredText(arguments, "nombre"));
        return result;
    }

    private static Map<String, Object> variantPriceArguments(JsonNode arguments) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("idVariante", requiredPositiveLong(arguments, "idVariante"));
        result.put("precio", requiredPositiveDouble(arguments, "precio"));
        return result;
    }

    private static Long idArguments(JsonNode arguments, String key) {
        return requiredPositiveLong(arguments, key);
    }

    private static String summarize(String operation, Object arguments) {
        return switch (operation) {
            case "CREATE_PRODUCT" -> throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "No se pudo resumir el producto propuesto");
            case "ADD_CATEGORY" -> "Crear categoría “" + arguments + "”.";
            case "ADD_COLOR" -> "Crear color “" + arguments + "”.";
            case "ADD_SIZE" -> {
                Map<?, ?> size = (Map<?, ?>) arguments;
                yield "Agregar talla “" + size.get("size") + "” para la categoría “"
                        + size.get("category") + "” y el género “" + size.get("gender") + "”.";
            }
            case "BULK_ADD" -> throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "No se pudo resumir el lote propuesto");
            default -> throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "La operación solicitada no está permitida");
        };
    }

    private static String summarizeProduct(JsonNode draft, AddProductoRequest request) {
        String sizesAndStock = request.variantes().stream()
                .map(variant -> "talla " + variant.talla() + ", " + variant.stock()
                        + " unidades a $" + variant.precio())
                .reduce((first, second) -> first + "; " + second)
                .orElseThrow();
        return "Crear producto nuevo “" + request.nombre() + "”; categoría “"
                + draft.path("category").asText() + "”, género “" + draft.path("gender").asText()
                + "”, color “" + displayOptionalAttribute(draft, "color") + "”, marca “"
                + displayOptionalAttribute(draft, "brand") + "”, estilo “"
                + displayOptionalAttribute(draft, "style")
                + "”. Inventario inicial: " + sizesAndStock + ".";
    }

    private static String displayOptionalAttribute(JsonNode draft, String key) {
        return isNotApplicable(optionalText(draft, key)) ? "no aplica" : optionalText(draft, key);
    }

    private static List<String> textArray(JsonNode node, String key) {
        JsonNode values = node.path(key);
        if (values.isMissingNode() || values.isNull()) return List.of();
        if (!values.isArray()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, key + " debe ser una lista");
        }
        List<String> result = new ArrayList<>();
        for (JsonNode value : values) {
            if (!value.isTextual() || value.asText().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Cada valor de " + key + " debe ser texto no vacío");
            }
            result.add(value.asText().trim());
        }
        return result;
    }

    private static <T> List<T> objectArray(JsonNode node, String key,
                                            Function<JsonNode, T> mapper) {
        JsonNode values = node.path(key);
        if (values.isMissingNode() || values.isNull()) return List.of();
        if (!values.isArray()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, key + " debe ser una lista");
        }
        List<T> result = new ArrayList<>();
        for (JsonNode value : values) {
            if (!value.isObject()) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Cada elemento de " + key + " debe ser un objeto");
            }
            result.add(mapper.apply(value));
        }
        return result;
    }

    private static <T> List<T> distinct(List<T> values, Function<T, String> keyMapper) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        List<T> result = new ArrayList<>();
        for (T value : values) {
            if (seen.add(keyMapper.apply(value))) result.add(value);
        }
        return result;
    }

    private static <T> void reportRepeated(List<T> values, Function<T, String> keyMapper,
                                           Function<T, String> labelMapper,
                                           List<String> skipped) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (T value : values) {
            if (!seen.add(keyMapper.apply(value))) {
                skipped.add(labelMapper.apply(value) + " (repetido en el lote)");
            }
        }
    }

    private static Integer findCatalogId(List<Map<String, Object>> catalog, String name, String idKey) {
        String expected = normalize(name);
        return catalog.stream()
                .filter(row -> expected.equals(normalize(String.valueOf(row.get("nombre")))))
                .map(row -> ((Number) row.get(idKey)).intValue())
                .findFirst()
                .orElse(null);
    }

    private static String formatRows(String title, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return title + "\nNo hay registros.";
        StringBuilder text = new StringBuilder(title);
        rows.forEach(row -> text.append("\n• ").append(row));
        return text.toString();
    }

    private static String requiredText(JsonNode node, String key) {
        JsonNode value = node.path(key);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Falta el dato requerido: " + key);
        }
        return value.asText().trim();
    }

    private static String optionalText(JsonNode node, String key) {
        JsonNode value = node.path(key);
        return value.isTextual() ? value.asText().trim() : "";
    }

    private static Long requiredPositiveLong(JsonNode node, String key) {
        JsonNode value = node.path(key);
        if (!value.canConvertToLong() || value.asLong() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El identificador " + key + " no es válido");
        }
        return value.asLong();
    }

    private static Integer requiredPositiveInt(JsonNode node, String key) {
        JsonNode value = node.path(key);
        if (!value.canConvertToInt() || value.asInt() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, key + " debe ser un entero positivo");
        }
        return value.asInt();
    }

    private static Double requiredPositiveDouble(JsonNode node, String key) {
        JsonNode value = node.path(key);
        if (!value.isNumber() || !Double.isFinite(value.asDouble()) || value.asDouble() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, key + " debe ser un número positivo");
        }
        return value.asDouble();
    }

    private static Double optionalPrice(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || value.isNull()) return null;
        if (!value.isNumber() || !Double.isFinite(value.asDouble()) || value.asDouble() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "El filtro " + key + " debe ser un precio numérico válido");
        }
        return value.asDouble();
    }

    private static String normalize(String text) {
        if (text == null) return "";
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static PendingAction getPending(HttpSession session) {
        Object pending = session.getAttribute(PENDING_ACTION);
        if (!(pending instanceof PendingAction action)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "No hay cambios pendientes por confirmar");
        }
        return action;
    }

    private record PendingAction(String id, String operation, Object arguments, String summary) {}

    private record PendingProductCatalogs(ObjectNode draft, List<String> missing) {}

    private record ProductPreparation(
            AddProductoRequest request,
            List<String> missing,
            boolean genderMissing,
            String availableGenders,
            String categoryToCreate,
            String colorToCreate
    ) {}

    private record ProductCreation(
            AddProductoRequest request,
            String categoryToCreate,
            String colorToCreate
    ) {}

    private record SizeRegistration(String size, String category, String gender) {}

    private record BrandRegistration(String name, String category) {}

    private record StyleRegistration(String name, String brand) {}

    private record BulkRegistration(List<String> colors, List<SizeRegistration> sizes,
                                    List<BrandRegistration> brands, List<StyleRegistration> styles,
                                    List<ProductCreation> products) {
        boolean isEmpty() {
            return colors.isEmpty() && sizes.isEmpty() && brands.isEmpty()
                    && styles.isEmpty() && products.isEmpty();
        }
    }

    public record TurnResult(String message, String actionId, String actionSummary,
                             boolean needsDuplicateDecision) {
        public TurnResult(String message, String actionId, String actionSummary) {
            this(message, actionId, actionSummary, false);
        }

        static TurnResult message(String message) {
            return new TurnResult(message, null, null, false);
        }

        static TurnResult bulkDecision(String message) {
            return new TurnResult(message, null, null, true);
        }
    }
}
