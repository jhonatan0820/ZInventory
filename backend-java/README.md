# Backend Java - Inventorio Dotaciones Zambrano

Migración del backend Python (Flask) a **Java 17 + Spring Boot 3** manteniendo 1:1 la
lógica de negocio, endpoints, respuestas y comportamiento ante errores.

## Requisitos

- Java 17+
- Maven 3.9+
- MySQL 8.x accesible (mismo esquema que usaba el backend Python)
- Stored procedure `InformationGeneral(categoria, genero, producto, talla, estilo)` creado en MySQL

## Estructura

```
backend-java/
├── pom.xml
└── src/main/
    ├── java/com/dotacioneszambrano/inventorio/
    │   ├── InventorioApplication.java
    │   ├── config/       (CORS, DataSource, propiedades, beans)
    │   ├── controller/   (24 endpoints: auth, productos, stock, catálogos, reporte)
    │   ├── service/      (lógica de negocio migrada del Python)
    │   ├── dto/          (records para request/response)
    │   ├── exception/    (ApiException + @RestControllerAdvice)
    │   └── model/        (POJOs, si se necesitan)
    ├── resources/
    │   ├── application.yml
    │   ├── templates/information_general_pdf.html   (Thymeleaf del PDF)
    │   └── static/                                   (frontend integrado)
    │       ├── index.html, inventario.html, InformationGeneral.html,
    │       ├── recuperar.html, reset.html
    │       ├── config.js  (API_BASE = "" -> same-origin)
    │       ├── inventario.js, InformationGeneral.js, alerts.js
    │       └── styles.css, InformationGeneral.css
```

## Configuración (obligatorio antes de arrancar)

Las credenciales viven **fuera del código fuente Java** pero **dentro del proyecto**,
en `backend-java/config/application-secrets.yml`. Spring Boot lo importa mediante
`spring.config.import` (relativo al directorio de trabajo `backend-java/`).

1. Copia el ejemplo:

   ```bash
   cp config/application-secrets.example.yml config/application-secrets.yml
   ```

2. Rellena valores:

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
   ```

3. Alternativa (idéntica al backend Python): exporta `MYSQL_URL` con formato
   `mysql://user:pass@host:port/db`. El backend prioriza esta variable si existe.

Variables de entorno soportadas:

| Variable                     | Uso                                 |
|------------------------------|-------------------------------------|
| `MYSQL_URL`                  | Conexión MySQL estilo Python        |
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
Cambia el puerto con `SERVER_PORT=8081 mvn spring-boot:run` o exportando `PORT`.

Construir jar:

```bash
mvn -DskipTests package
java -jar target/inventorio-1.0.0.jar
```

## Endpoints migrados (24 rutas — mismos paths que el Python)

**Salud**
- `GET  /health`, `GET /ping`, `GET /activador`

> La ruta `/` la usa ahora el frontend (`index.html` como welcome page). El
> health check del backend Python (que estaba en `/`) se movió a `/health`.

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

## Diferencias respecto al Python

Documentadas exhaustivamente para trazabilidad de la migración:

1. **Persistencia:** Se usa `JdbcTemplate` (no JPA) para preservar los SQL originales
   línea a línea, incluidos los `<=>` NULL-safe, `ON DUPLICATE KEY` y `CALL`.
2. **BCrypt:** Los hashes generados por `flask-bcrypt` (prefijo `$2b$`) son
   completamente compatibles con `BCryptPasswordEncoder` de Spring Security.
3. **Sesión:** cookie `SESSION` con `SameSite=None`, `Secure`, `HttpOnly` —
   equivalente al setup Flask (`SESSION_COOKIE_SAMESITE=None`, `SECURE=True`).
4. **CORS:** los mismos 4 orígenes permitidos (`dotacioneszambrano.com`,
   `www.dotacioneszambrano.com`, `127.0.0.1:5500`, `localhost:5500`).
5. **PDF:** `xhtml2pdf` → **OpenHTMLtoPDF + Thymeleaf**. El template fue traducido
   de sintaxis Jinja a Thymeleaf preservando estilos y estructura.
6. **Envío de correo:** cliente HTTP a la API de Resend, mismo payload, misma URL.
7. **Errores:** `@RestControllerAdvice` global devuelve `{ ok:false, error:"..." }`
   con los mismos códigos HTTP que el backend Python.
8. **Logs:** SLF4J (`Logger`) en lugar de `print()`. Nunca se registran contraseñas,
   tokens completos ni credenciales.
9. **`MAX_INTENTOS`:** el rate-limit en el Python estaba **declarado pero no
   aplicado**; en Java se mantiene el mismo comportamiento (el conteo lo hace el
   frontend). Documentado como deuda técnica.
10. **`callproc`:** se ejecuta con `SimpleJdbcCall` sobre el mismo SP `InformationGeneral`.

## Estado

- ✅ Compila sin errores (`mvn compile`).
- ✅ Arranca sin base de datos (modo degradado con warning).
- ✅ Endpoints públicos verificados (`/`, `/ping`, CORS preflight).
- ✅ Validaciones de entrada (400) verificadas para Login, RecuperarPassword,
  ResetPassword, AddProducto, DeleteProductos, ActualizarStock.
- ⏳ Pendiente prueba integrada contra MySQL real (requiere credenciales).
- ⏳ Fase 2 (frontend modernizado) y Fase 3 (tests) pendientes.
