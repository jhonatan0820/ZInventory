# ZInventory — Dotaciones Zambrano

Aplicación web de inventario. Spring Boot sirve la API y el frontend desde un
mismo origen, con persistencia en MySQL.

## Problema

El inventario requiere registrar productos y sus presentaciones —por ejemplo,
color, talla y precio— además de controlar entradas, ventas y existencias.
Registrar estos datos en flujos separados puede causar variantes duplicadas,
descuadres de stock y movimientos difíciles de verificar. También se necesita
consultar el inventario sin depender de navegar manualmente todos los catálogos.

## Solución

ZInventory reúne la gestión de productos, catálogos, inventario, ventas y
reportes en una aplicación web. Incluye un asistente de voz para consultar datos
y proponer cambios con confirmación explícita antes de escribir en la base.

Las altas masivas reúnen colores, tallas, marcas, estilos y productos; validan el
lote completo antes de proponerlo, omiten duplicados de catálogo y preguntan qué
hacer con presentaciones ya existentes. Las ventas requieren identificar la
presentación, cantidad y precio, y registran el movimiento de salida asociado.

## Desarrollo y herramientas

- **Backend:** Java 17, Spring Boot 3, Spring MVC, Spring Data JPA y JDBC.
- **Base de datos:** MySQL con el esquema existente; Hibernate no crea ni altera
  tablas automáticamente.
- **Frontend:** HTML, CSS y JavaScript sin framework. Maven lo incluye en el JAR
  como contenido estático.
- **Asistente de voz:** Gemini Live y herramientas de backend con operaciones
  permitidas y confirmación de escrituras.
- **Reportes:** Thymeleaf, Jsoup y OpenHTMLtoPDF.
- **Build y despliegue:** Maven y Docker Compose; contenedor Java 17 con usuario
  sin privilegios.

La aplicación se organiza por capas: controladores HTTP, servicios de negocio,
repositorios y entidades de persistencia. El frontend y la API se sirven en el
mismo origen para compartir la sesión autenticada. Las credenciales y llaves se
mantienen en un archivo externo ignorado por Git, nunca en el código o la imagen.

## Especificaciones importantes

- Java 17 y Maven 3.9 o superior.
- MySQL debe tener el esquema de inventario y el procedimiento
  `InformationGeneral` requeridos por los reportes.
- `ddl-auto: none`: el backend no modifica automáticamente el esquema.
- Cada operación de escritura del asistente queda pendiente y requiere
  confirmación explícita.
- Los movimientos de inventario preservan el historial; ventas generan una
  salida vinculada a la presentación.
- Las contraseñas se almacenan con PBKDF2-HMAC-SHA-256.
- El asistente usa un token efímero para conectar Gemini Live; la llave
  permanente permanece en el backend.
- No se incluye `src/test`, de acuerdo con la decisión del proyecto. El build
  valida compilación y empaquetado, pero no ejecuta pruebas unitarias.

## Requisitos

- JDK 17 o superior
- Maven 3.9 o superior
- MySQL con el esquema de inventario existente

## Configuración y ejecución local

1. Copia `backend-java/application-secrets.example.yml` a
   `backend-java/application-secrets.yml`.
2. Reemplaza los valores `REPLACE_*`, `HOST`, `DATABASE` y `DATABASE_USER`.
   Genera una clave aleatoria larga para `secret-key`.
3. Desde `backend-java`, ejecuta:

   ```powershell
   mvn spring-boot:run
   ```

4. Abre <http://localhost:8080>.

`backend-java/application-secrets.yml` y los archivos `.env` locales están
excluidos de Git. No copies credenciales reales a la plantilla de ejemplo.

## Docker Compose

Prepara el archivo local de secretos como se indicó arriba y define `MYSQL_URL`
con el formato `mysql://usuario:contraseña@host:3306/base_de_datos`. Codifica los
caracteres especiales de las credenciales para URL.

```powershell
$env:MYSQL_URL = "mysql://usuario:contraseña@host:3306/base_de_datos"
docker compose up --build -d
```

Opcionalmente define `APP_RESEND_API_KEY`, `RESET_BASE_URL` y las variables
`REPORT_COMPANY_*`.

## Compilación

Desde `backend-java`:

```powershell
mvn clean package
```

Este comando compila el backend y empaqueta la aplicación y el frontend en
`backend-java/target/inventorio-1.0.0.jar`.

## Documentación técnica

Consulta [backend-java/README.md](backend-java/README.md) para los endpoints,
configuración de base de datos y detalles de persistencia.
