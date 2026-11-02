package com.dotacioneszambrano.inventorio.voiceassistant.service;

import com.dotacioneszambrano.inventorio.exception.ApiException;
import com.dotacioneszambrano.inventorio.voiceassistant.config.GeminiVoiceProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GeminiLiveSessionService {

    private static final Logger log = LoggerFactory.getLogger(GeminiLiveSessionService.class);
    private static final String SYSTEM_INSTRUCTION = """
            Eres un asistente de voz en español para el inventario de una empresa de dotaciones.
            Habla de forma breve y natural. Usa las herramientas disponibles para consultar
            productos, catálogos y movimientos o para proponer cambios. No inventes datos ni IDs:
            busca primero productos cuando sea necesario. Nunca afirmes que una escritura quedó
            hecha: las escrituras quedan pendientes y solo se ejecutan cuando el usuario confirma
            explícitamente el resumen por voz o mediante el botón. No hay herramientas para borrar,
            cambiar usuarios, acceder a secretos ni ejecutar SQL. Si falta información
            o hay ambigüedad, pregunta antes de llamar una herramienta de escritura.
            Después de proponer un cambio, lee el resumen y espera una respuesta explícita.
            Solo llama confirm_pending_action si el usuario dice claramente que confirma
            ese cambio después de escuchar el resumen. Acepta cualquier respuesta claramente
            afirmativa (por ejemplo: “sí”, “ok”, “está bien”, “perfecto”, “adelante” o
            “hazlo”), pero no aceptes dudas, condiciones ni respuestas ambiguas. Si responde que no, que cancele,
            o rechaza el cambio, llama cancel_pending_action. Si expresa duda o pide un
            ajuste, no confirmes ni canceles: explica que puede usar los botones para
            resolver el cambio pendiente. Mientras haya un cambio pendiente, puedes atender
            consultas de solo lectura de productos, catálogos y movimientos; no propongas otro
            cambio hasta resolver el actual. Nunca interpretes silencio como confirmación.

            Trata cada intervención como una solicitud nueva. Para consultas de inventario,
            usa únicamente los filtros expresados en el mensaje actual; no arrastres marca,
            color, estilo, categoría ni talla de preguntas anteriores, excepto cuando el usuario
            haga una referencia explícita como "de esa marca" o "los mismos". No uses el borrador
            de creación de producto para completar filtros de búsqueda.
            En preguntas de cantidad ("cuántos hay", "total", "en total"), usa search_products
            con los filtros de ese turno y responde con el stock total informado por el backend,
            no cuentes solo las filas del detalle. Ejemplo: tras preguntar por botas de marca
            Fábrica, "¿cuántas botas talla 37 hay en total?" filtra por botas y talla 37, sin
            conservar la marca Fábrica salvo que el usuario la repita.
            Si preguntan por la última venta de un producto, identifica el producto exacto con
            search_products si hace falta y llama last_sale usando el id_producto (no id_variante).
            Si el nombre o la referencia del producto es ambigua, pregunta cuál antes de consultar.
            La herramienta last_sale entrega la fecha y la hora ya convertidas a la hora local de
            Colombia. Repite exactamente esos valores; no conviertas ni calcules zonas horarias.
            No busques ni deduzcas la hora usando otros movimientos.
            Para agregar una talla, pide siempre su valor, una categoría existente y un género
            existente. Si falta algún dato, pregunta y no llames add_size todavía. Cuando tengas
            los tres, usa add_size; si la combinación ya existe, comunícalo sin intentar crearla.
            Lee el resumen de confirmación con los tres datos y espera confirmación explícita.
            Si el usuario dicta o pega varios colores, tallas, marcas, estilos o productos,
            reúne todo en una sola llamada bulk_add. No guardes nada parcialmente. Para cada
            producto exige nombre, categoría existente o inferible por una marca registrada,
            género existente, talla, precio y cantidad; pregunta por los campos faltantes antes
            de llamar la herramienta. Marca, estilo y color también deben quedar explícitos;
            si no aplican, el usuario puede indicarlo con “NA”. Los colores, tallas, marcas y
            estilos que ya existan se informarán y se omitirán. Si una presentación de producto
            ya existe, pregunta si agrega sus unidades al stock o la excluye y llama
            resolve_bulk_duplicates solo después de que el usuario elija claramente una opción.
            Lee el lote completo y espera una afirmación clara antes de confirmarlo.
            En cada resultado, id_variante es el identificador de la presentación mostrado en la
            tarjeta. Si el usuario dice "producto número N" o "variante número N", interpreta N
            como idVariante. Para responder qué producto es N, llama get_variant con ese ID.
            Para cambios de stock, precio u ocultar presentación, usa también ese N como idVariante;
            no lo confundas con idProducto, talla, precio o cantidad.
            Para vender, primero identifica la presentación exacta con search_products. Si hay
            varias coincidencias posibles, pregunta por talla, color u otro detalle hasta que
            quede inequívoca; nunca escojas una presentación por suposición. Necesitas el ID exacto,
            la cantidad y el precio unitario de venta indicado por el usuario. No infieras el precio
            de venta del precio de compra ni la cantidad del stock disponible. Si falta uno de esos
            datos, pregunta antes de usar sell_stock. La herramienta solo propone la venta: lee
            producto, talla, color, cantidad, precio unitario y total, y espera confirmación
            explícita antes de registrar la salida.
            También puedes filtrar por precio de cada presentación: usa priceGreaterThan para
            "mayor a"/"más de", priceLessThan para "menor a"/"menos de", y priceEquals para
            "cuesta"/"vale exactamente". Los límites mayor y menor son estrictos; combina
            precio con producto u otros filtros y omite los campos de precio no mencionados.
            Ejemplo: "¿cuántas botas hay con valor mayor a 50000?" usa query "botas" y
            priceGreaterThan 50000; responde las presentaciones encontradas y el stock total.
            Envía el nombre de producto como query o productName y cada característica solicitada
            en su propio campo. Los filtros enviados se combinan todos; omite los que no se
            mencionan en la pregunta actual.
            Antes de crear un producto, comprueba si ya existe una presentación con la misma talla,
            color y precio usando search_products. Si coincide, no propongas duplicarla; indica
            que puede ingresar stock a esa presentación. Si el producto y talla ya existen pero
            el precio es diferente, es una presentación nueva válida: propón crearla conservando
            el mismo producto y guardando el nuevo precio. Antes de agregar una
            categoría o color, consulta el catálogo correspondiente; si ya aparece, informa que
            existe y no lo agregues. Para marca y estilo, comprueba sus catálogos antes de crear.
            Si no existen, presenta el cambio para confirmación y no insertes nada antes de ella.

            La lista de catálogos incluida al iniciar puede quedar desactualizada: antes de crear
            un producto consulta los catálogos necesarios con list_catalog para usar sus valores
            actuales. Antes de preguntar la categoría, busca la marca en el catálogo de marcas,
            que incluye la categoría asociada. Si la marca ya está registrada con una categoría,
            usa esa categoría automáticamente y no preguntes cuál es. Si la marca no existe o no
            tiene categoría asociada y el usuario no indicó categoría, pregunta cuál debe usarse;
            esa categoría se asociará a la marca nueva al guardar. No deduzcas la categoría por
            el nombre de una marca ni por ejemplos de conversación anteriores.
            Al crear productos, género, talla, precio y cantidad son necesarios. La categoría se
            infiere de la marca registrada cuando sea posible; si no, solicítala.
            Marca, estilo y color son opcionales, pero si el usuario no menciona alguno,
            pregúntale qué valor usar antes de proponer la creación. Si confirma que no aplica,
            registra "NA"; no asumas que un campo omitido significa NA. Valida los valores
            proporcionados contra los catálogos actuales. Si una característica indicada no
            existe, pregunta explícitamente si quiere agregarla; solo tras un "sí" claro llama
            resolve_missing_product_attributes con decision "yes". Si responde que no, usa "no".
            Nunca parametrices una característica sin esa confirmación verbal. El backend
            volverá a validar los datos.
            Escribe los nombres de productos, marcas y estilos con capitalización normal;
            no los guardes en mayúsculas sostenidas. Los colores sí se guardan en MAYÚSCULAS.
            Usa los nombres exactos del catálogo para categoría y talla.
            Para cualquier producto de botas, el nombre se normaliza automáticamente a
            "Botas de Seguridad". Si no se menciona color para botas, usa "Negro".
            En las confirmaciones, explica el inventario inicial por talla, unidades y precio;
            evita la palabra técnica "variante".
            """;

    private final GeminiVoiceProperties properties;
    private final RestClient restClient;

    public GeminiLiveSessionService(GeminiVoiceProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("x-goog-api-key", properties.apiKey())
                .build();
    }

    public Map<String, Object> createSession() {
        requireConfiguration();
        Instant now = Instant.now();
        Map<String, Object> request = Map.of(
                "uses", 1,
                "expireTime", now.plusSeconds(600).toString(),
                "newSessionExpireTime", now.plusSeconds(60).toString());

        try {
            JsonNode response = restClient.post()
                    .uri("/auth_tokens")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            String token = response == null ? null : response.path("name").asText(null);
            if (token == null || token.isBlank()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY,
                        "Gemini no devolvió un token temporal para la sesión de voz");
            }

            Map<String, Object> setup = new LinkedHashMap<>();
            setup.put("model", "models/" + properties.liveModel());
            setup.put("generationConfig", Map.of("responseModalities", List.of("AUDIO")));
            setup.put("systemInstruction", Map.of("parts", List.of(Map.of("text", SYSTEM_INSTRUCTION))));
            setup.put("inputAudioTranscription", Map.of("languageCodes", List.of("es-CO")));
            setup.put("outputAudioTranscription", Map.of());
            setup.put("tools", toolConfiguration());

            return Map.of(
                    "token", token,
                    "model", properties.liveModel(),
                    "setup", setup);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            String providerMessage = ex.getResponseBodyAsString().replaceAll("\\s+", " ").trim();
            if (providerMessage.length() > 500) {
                providerMessage = providerMessage.substring(0, 500);
            }
            log.warn("Gemini Live token request returned HTTP {}: {}", status,
                    providerMessage.isBlank() ? "provider returned no error details" : providerMessage);
            if (status == 429) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                        "Gemini alcanzó el límite o la cuota disponible. Revisa Google AI Studio.");
            }
            if (status == 401 || status == 403) {
                throw new ApiException(HttpStatus.BAD_GATEWAY,
                        "Gemini rechazó la llave o el acceso a Live API.");
            }
            if (status == 404) {
                throw new ApiException(HttpStatus.BAD_GATEWAY,
                        "Gemini no encontró el servicio de tokens temporales.");
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Gemini no pudo crear una sesión de voz (HTTP " + status + ").");
        } catch (ResourceAccessException ex) {
            log.warn("Could not reach Gemini Live token API: {}",
                    ex.getMostSpecificCause().getClass().getSimpleName());
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "No se pudo conectar con Gemini Live para iniciar la sesión.");
        }
    }

    private void requireConfiguration() {
        if (!properties.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El asistente de voz no está configurado. Agrega la llave de Gemini a application-secrets.yml.");
        }
        if (properties.baseUrl() == null || properties.baseUrl().isBlank()
                || properties.liveModel() == null || properties.liveModel().isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "La configuración de Gemini Live está incompleta");
        }
    }

    private static List<Map<String, Object>> toolConfiguration() {
        List<Map<String, Object>> declarations = new ArrayList<>();
        declarations.add(function("search_products",
                "Consulta productos y sus presentaciones activas con filtros explícitos del mensaje actual. Devuelve también el total de unidades en stock para todas las coincidencias, no solo las filas de detalle.",
                fields("query", stringField("Palabra corta de producto o categoría, solo si se menciona en la pregunta actual"),
                        "productName", stringField("Nombre de producto explícito en la pregunta actual"),
                        "brand", stringField("Marca explícita en la pregunta actual"),
                        "style", stringField("Estilo explícito en la pregunta actual"),
                        "color", stringField("Color explícito en la pregunta actual"),
                        "size", stringField("Talla explícita en la pregunta actual; debe coincidir exactamente"),
                        "category", stringField("Categoría explícita en la pregunta actual"),
                        "gender", stringField("Género explícito en la pregunta actual"),
                        "priceGreaterThan", numberField("Precio estrictamente mayor que este valor, solo si se solicita"),
                        "priceLessThan", numberField("Precio estrictamente menor que este valor, solo si se solicita"),
                        "priceEquals", numberField("Precio exacto, solo si se solicita; no combinar con límites")), List.of()));
        declarations.add(function("get_variant",
                "Consulta una presentación por su ID exacto id_variante.",
                fields("idVariante", integerField("ID de variante visible en la tarjeta")),
                List.of("idVariante")));
        declarations.add(function("list_catalog", "Consulta las opciones activas de un catálogo.",
                fields("catalog", enumField("Catálogo a consultar",
                        List.of("categories", "genders", "colors", "sizes", "styles", "brands"))), List.of("catalog")));
        declarations.add(function("recent_movements", "Consulta los diez movimientos de inventario más recientes.",
                Map.of(), List.of()));
        declarations.add(function("last_sale",
                "Consulta la venta más reciente de un producto exacto. Usa idProducto de una búsqueda o el nombre exacto; no uses idVariante como idProducto.",
                fields("idProducto", integerField("ID exacto del producto, no el ID de variante"),
                        "productName", stringField("Nombre exacto del producto si no se tiene su ID")),
                List.of()));
        declarations.add(function("confirm_pending_action",
                "Aplica el cambio pendiente solo después de que el usuario confirme explícitamente en voz alta.",
                Map.of(), List.of()));
        declarations.add(function("cancel_pending_action",
                "Cancela el cambio pendiente si el usuario lo rechaza explícitamente en voz alta.",
                Map.of(), List.of()));
        declarations.add(function("create_product", "Propone crear un producto con inventario inicial por talla.",
                productFields(), List.of("productName", "gender", "variants")));
        declarations.add(function("resolve_missing_product_attributes",
                "Resuelve la pregunta pendiente sobre agregar al catálogo las características faltantes de un producto.",
                fields("decision", enumField("Usa yes solo tras un sí explícito del usuario; usa no si lo rechaza.",
                        List.of("yes", "no"))), List.of("decision")));
        declarations.add(function("add_category", "Propone agregar una categoría.",
                fields("name", stringField("Nombre de la nueva categoría")), List.of("name")));
        declarations.add(function("add_color", "Propone agregar un color.",
                fields("name", stringField("Nombre del nuevo color")), List.of("name")));
        declarations.add(function("add_size",
                "Propone agregar una talla asociada obligatoriamente a una categoría y un género. "
                        + "Comprueba que la combinación no exista.",
                fields("size", stringField("Valor exacto de la talla"),
                        "category", stringField("Nombre exacto de una categoría existente"),
                        "gender", stringField("Nombre exacto de un género existente")),
                List.of("size", "category", "gender")));
        Map<String, Object> sizes = arraySchema(objectSchema(fields(
                "size", stringField("Valor exacto de la talla"),
                "category", stringField("Categoría existente"),
                "gender", stringField("Género existente")),
                List.of("size", "category", "gender")));
        Map<String, Object> brands = arraySchema(objectSchema(fields(
                "name", stringField("Nombre de la marca"),
                "category", stringField("Categoría activa asociada")),
                List.of("name", "category")));
        Map<String, Object> styles = arraySchema(objectSchema(fields(
                "name", stringField("Nombre del estilo"),
                "brand", stringField("Marca activa o incluida en el lote; opcional")),
                List.of("name")));
        Map<String, Object> products = arraySchema(objectSchema(productFields(),
                List.of("productName", "gender", "variants")));
        declarations.add(function("bulk_add",
                "Propone agregar en un solo lote colores, tallas, marcas, estilos y productos. "
                        + "No guarda nada hasta confirmar el resumen completo. Omite campos de lista vacíos.",
                fields("colors", arraySchema(stringField("Color a agregar")),
                        "sizes", sizes,
                        "brands", brands,
                        "styles", styles,
                        "products", products),
                List.of()));
        declarations.add(function("resolve_bulk_duplicates",
                "Resuelve la pregunta pendiente para una presentación de producto ya existente. "
                        + "Usa add_stock solo si el usuario eligió claramente sumar cantidad al stock; "
                        + "usa omit solo si eligió excluirla. No confirma el lote.",
                fields("decision", enumField("Opción elegida explícitamente por el usuario",
                        List.of("add_stock", "omit"))),
                List.of("decision")));
        declarations.add(function("receive_stock", "Propone ingresar unidades a una presentación de producto.",
                fields("idVariante", integerField("ID de la presentación de producto"),
                        "cantidad", integerField("Cantidad a ingresar")), List.of("idVariante", "cantidad")));
        declarations.add(function("sell_stock",
                "Propone vender unidades de una presentación identificada de forma inequívoca. Requiere el ID obtenido al buscar, cantidad y precio unitario explícito del usuario; no ejecuta la venta hasta confirmación.",
                fields("idVariante", integerField("ID exacto de presentación obtenido con search_products"),
                        "cantidad", integerField("Cantidad positiva que se venderá"),
                        "precioVenta", numberField("Precio unitario de venta indicado explícitamente por el usuario")),
                List.of("idVariante", "cantidad", "precioVenta")));
        declarations.add(function("update_product_name", "Propone cambiar el nombre de un producto activo.",
                fields("idProducto", integerField("ID del producto"),
                        "nombre", stringField("Nuevo nombre")), List.of("idProducto", "nombre")));
        declarations.add(function("update_variant_price", "Propone actualizar el precio de una presentación de producto.",
                fields("idVariante", integerField("ID de la presentación de producto"),
                        "precio", numberField("Nuevo precio positivo")), List.of("idVariante", "precio")));
        declarations.add(function("hide_product", "Propone ocultar un producto cambiando su estado, sin borrarlo.",
                fields("idProducto", integerField("ID del producto")), List.of("idProducto")));
        declarations.add(function("hide_variant", "Propone ocultar una presentación de producto sin borrarla.",
                fields("idVariante", integerField("ID de la presentación de producto")), List.of("idVariante")));
        return List.of(Map.of("functionDeclarations", declarations));
    }

    private static Map<String, Object> productFields() {
        Map<String, Object> variantFields = fields(
                "size", stringField("Talla"),
                "price", numberField("Precio positivo"),
                "quantity", integerField("Cantidad positiva"));
        Map<String, Object> variants = Map.of(
                "type", "ARRAY",
                "description", "Tallas del producto con precio y cantidad inicial",
                "items", objectSchema(variantFields, List.of("size", "price", "quantity")));
        return fields(
                "category", stringField("Categoría exacta; omítela si el backend puede inferirla por la marca registrada"),
                "productName", stringField("Nombre del producto"),
                "brand", stringField("Marca; si no aplica, NA. Si no se menciona, pregunta antes de proponer."),
                "style", stringField("Estilo; si no aplica, NA. Si no se menciona, pregunta antes de proponer."),
                "gender", stringField("Nombre exacto de un género del catálogo"),
                "color", stringField("Color exacto del catálogo o NA si no aplica; pregunta si no se menciona (excepto botas, que usan Negro por defecto)."),
                "variants", variants);
    }

    private static Map<String, Object> function(String name, String description,
                                                Map<String, Object> properties,
                                                List<String> required) {
        return Map.of(
                "name", name,
                "description", description,
                "parameters", objectSchema(properties, required));
    }

    private static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        return Map.of("type", "OBJECT", "properties", properties, "required", required);
    }

    private static Map<String, Object> stringField(String description) {
        return Map.of("type", "STRING", "description", description);
    }

    private static Map<String, Object> integerField(String description) {
        return Map.of("type", "INTEGER", "description", description);
    }

    private static Map<String, Object> numberField(String description) {
        return Map.of("type", "NUMBER", "description", description);
    }

    private static Map<String, Object> enumField(String description, List<String> values) {
        return Map.of("type", "STRING", "description", description, "enum", values);
    }

    private static Map<String, Object> arraySchema(Map<String, Object> itemSchema) {
        return Map.of("type", "ARRAY", "items", itemSchema);
    }

    private static Map<String, Object> fields(Object... values) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            fields.put((String) values[i], values[i + 1]);
        }
        return fields;
    }
}
