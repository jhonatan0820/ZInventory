# Backend Java - Inventorio Dotaciones Zambrano

Backend de inventario en **Java 17 + Spring Boot 3**, con API y frontend en un
solo despliegue.

## Requisitos

- Java 17+
- Maven 3.9+
- MySQL 8.x accesible con el esquema de inventario
- Stored procedure `InformationGeneral(categoria, genero, producto, talla, estilo)` creado en MySQL

## Estructura

```
backend-java/
├── pom.xml
└── src/main/
    ├── java/com/dotacioneszambrano/inventorio/
    │   ├── InventorioApplication.java
    │   ├── config/       (DataSource, propiedades, beans)
    │   ├── controller/   (API de auth, productos, stock, catálogos, reportes y voz)
    │   ├── entity/        (entidades persistentes de MySQL)
    │   ├── repository/   (consultas y operaciones de persistencia)
    │   ├── service/      (lógica de negocio)
    │   ├── dto/          (records para request/response)
    │   ├── voiceassistant/ (asistente de voz y acciones permitidas)
    │   └── exception/    (ApiException + @RestControllerAdvice)
    ├── resources/
    │   ├── application.yml
    │   └── templates/information_general_pdf.html   (Thymeleaf del PDF)
└── ../frontend/                                     (HTML, CSS y JavaScript)
```

El frontend se mantiene en la carpeta hermana `frontend/`. Maven lo empaqueta
como recursos `static/` en el JAR para conservar las mismas rutas y el despliegue
único. Las entidades JPA están en `entity/`; las consultas y operaciones
de persistencia viven en `repository/`, y los servicios contienen la lógica de
negocio. Las tablas heredadas conservan sus nombres y columnas en MySQL; Hibernate
no genera ni modifica el esquema (`ddl-auto: none`).

## Configuración (obligatorio antes de arrancar)

Las credenciales viven **fuera del código fuente Java** en
`backend-java/application-secrets.yml`. Spring Boot importa ese archivo
al ejecutar desde la raiz del repositorio o desde `backend-java/`.

1. Copia `backend-java/application-secrets.example.yml` como
   `backend-java/application-secrets.yml` y reemplaza los valores de
   ejemplo por credenciales locales. El archivo real está ignorado por Git y no
   debe subirse:

   ```yaml
   app:
     security:
       secret-key: "una-cadena-larga-aleatoria"
     resend:
       api-key: "re_xxxxx"
   spring:
     datasource:
       url: "jdbc:mysql://HOST:3306/BASE_DATOS?useSSL=true"
       username: "USUARIO"
       password: "CONTRASENA"

   voice-assistant:
     gemini:
       api-key: "TU_LLAVE_GEMINI"
       base-url: "https://generativelanguage.googleapis.com/v1beta"
       model: "gemini-3.6-flash"
       live-model: "gemini-3.8-live"
     ```

La voz en vivo usa Gemini Live por WebSocket desde el navegador y un token
efímero de un solo uso emitido por el backend; la llave permanente nunca se
envía al navegador.

El asistente de voz usa operaciones cerradas del backend: buscar productos
(detalle máximo 20 variantes, con total de coincidencias y suma de stock calculados
sobre todos los resultados), consultar categorías/géneros/colores/tallas/estilos y
leer los 10 movimientos recientes. Puede proponer crear productos o categorías
y colores, ingresar stock, cambiar el nombre de un producto o el precio de una
variante, y ocultar productos o variantes cambiando su estado. Cada escritura
queda pendiente en la sesión autenticada y requiere confirmación explícita; no
se envía SQL ni se permite borrar movimientos, usuarios o credenciales.
También puede proponer tallas nuevas, siempre asociadas a una categoría y un
género existentes; valida la combinación antes de proponerla para evitar duplicados.
También puede proponer lotes de colores, tallas, marcas, estilos y productos con
validación conjunta y confirmación antes de guardar. Los duplicados de catálogos
se informan y omiten; para presentaciones de producto existentes, solicita
decidir si se agregan unidades al stock o se excluyen.
Al crear productos valida categoría, género, color, marca, estilo y talla contra
los catálogos activos. Si falta una característica, pregunta antes de agregarla.
Los productos de botas se guardan como “Botas de Seguridad” y usan “Negro”
cuando no se indicó otro color.
En búsquedas, solo aplica los filtros expresados en la pregunta actual, salvo
que el usuario haga una referencia explícita al contexto anterior.
Las búsquedas también aceptan precio exacto (`priceEquals`) y límites estrictos:
`priceGreaterThan` para mayor que y `priceLessThan` para menor que. Por ejemplo,
“¿cuántas botas hay con valor mayor a 50000?” filtra las presentaciones de botas
con precio superior a 50000 y devuelve tanto el total de presentaciones como las
unidades disponibles en esas presentaciones.

2. Alternativa: define `MYSQL_URL` con los componentes URI de MySQL (usuario,
   contraseña, servidor, puerto y nombre de la base). Codifica en URL los
   caracteres especiales de la contraseña. Para Docker Compose, el archivo
   `backend-java/application-secrets.yml` debe existir junto al proyecto; Compose lo monta
   en modo de solo lectura dentro del contenedor.

Variables de entorno soportadas:

| Variable                     | Uso                                 |
|------------------------------|-------------------------------------|
| `MYSQL_URL`                  | URL MySQL con credenciales          |
| `PORT`                       | Puerto del servidor (default 8080)  |
| `REPORT_COMPANY_NAME`        | Nombre en el PDF                    |
| `REPORT_COMPANY_LOGO`        | URL del logo en el PDF              |
| `REPORT_COMPANY_ADDRESS`     | Dirección en el PDF                 |
| `REPORT_COMPANY_PHONE`       | Teléfono en el PDF                  |

## Cómo ejecutar

```bash
cd backend-java
mvn spring-boot:run
```

Por defecto arranca en `http://localhost:8080`.
Cambia el puerto definiendo la variable de entorno `PORT`.

Construir jar:

```bash
mvn -DskipTests package
java -jar target/inventorio-1.0.0.jar
```

## Docker

Desde la raíz del repositorio, define `MYSQL_URL` y opcionalmente
`APP_RESEND_API_KEY`, `RESET_BASE_URL` y las variables `REPORT_COMPANY_*`.
Después ejecuta:

```bash
docker compose up --build -d
```

Docker construye el JAR con Maven en una etapa de compilación y lo ejecuta sobre
una imagen Java 17 JRE mínima con usuario sin privilegios. Los secretos se pasan
por el entorno del contenedor y no se incluyen en la imagen.

## Endpoints principales

**Salud**
- `GET  /health`, `GET /ping`, `GET /activador`

> La ruta `/` sirve el frontend (`index.html` como welcome page); `/health` se
> conserva para comprobar el estado de la API.

**Autenticación (sesión por cookie)**
- `POST /Login`
- `GET  /CheckSession`
- `POST /Logout`
- `POST /RecuperarPassword`
- `POST /ResetPassword`

**Productos**
- `GET  /GetProductos`
- `POST /AddProducto`
- `POST /DeleteProductos`

**Stock**
- `POST /ActualizarStock` (SALIDA)
- `POST /EntradaStock`
- `POST /EntradaStockConPrecio`

### Política de precio mínimo de venta

La regla se evalúa completamente en Java. Se permite la venta cuando el precio
de venta es igual o mayor al precio de compra de la variante; si es menor, el
backend rechaza la operación antes de descontar el stock o registrar el
movimiento. Esta validación no requiere tabla ni función en MySQL.

**Catálogos**
- `GET  /GetCategorias`, `POST /AddCategoria`
- `GET  /GetGeneros`
- `GET  /GetColores`, `POST /AddColor`
- `GET  /GetTallas`, `GET /GetTallasPorCategoriaGenero`,
  `GET /GetTallasPorCategoria`, `GET /GetTallasValidas`
- `GET  /GetEstilosUnicos`
- `GET  /GetNombresProductos`

**Reporte**
- `GET  /InformationGeneral` (llama al SP `InformationGeneral`)
- `GET  /InformationGeneralPdf?format=pdf|html` (Thymeleaf + OpenHTMLtoPDF)

**Asistente de voz**
- `POST /api/voice-assistant/live-session`
- `POST /api/voice-assistant/tools`
- `POST /api/voice-assistant/turn`
- `POST /api/voice-assistant/confirm`
- `POST /api/voice-assistant/cancel`
- `GET  /api/voice-assistant/pending`

## Decisiones de arquitectura

- **Persistencia:** entidades JPA y repositorios Spring Data JPA encapsulan el
  acceso a las tablas. El esquema existente no se crea ni modifica
  automáticamente (`ddl-auto: none`).
- **Contraseñas:** PBKDF2-HMAC-SHA-256 del JDK con salt aleatorio y 600.000
  iteraciones. El backend solo acepta este formato; las cuentas con hashes
  anteriores deben restablecer su contraseña.
  La columna `usuarios.password` debe permitir al menos 100 caracteres (se
  recomienda `VARCHAR(255)`).
- **Sesiones:** cookie `SESSION` con `SameSite=None`, `Secure` y `HttpOnly`.
- **Origen web:** frontend y API se sirven desde el mismo origen; no se configura
  CORS.
- **Reportes y movimientos:** las llamadas al procedimiento `InformationGeneral`
  y las operaciones del historial de movimientos se encapsulan en repositorios.
- **Errores y logs:** `@RestControllerAdvice` da forma a los errores; SLF4J no
  registra contraseñas, tokens completos ni credenciales.

## Compilación

- `mvn clean package` compila y empaqueta el backend.
- La app y los recursos frontend se sirven desde el JAR con las mismas rutas.
- La verificación completa de consultas JPA requiere ejecutar los flujos sobre
  una base MySQL con el esquema de inventario.
- Este repositorio no incluye carpeta de pruebas automatizadas.
