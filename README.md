# Zinventory Dotaciones Zambrano

Sistema de inventario con **frontend y backend en un solo despliegue**:

- **Backend Java (Spring Boot 3 + Java 17)** en `backend-java/` sirve tanto la API
  como el frontend estático (HTML + CSS + JS vanilla).
- **Backend Python legacy** en `back_inventario/` — conservado como fuente de
  verdad funcional hasta validar equivalencia con el backend Java.

## Estructura

```
ZINVENTORY/
├── back_inventario/                        Backend legacy (Flask). NO borrar aún.
├── backend-java/
│   ├── pom.xml
│   ├── README.md
│   ├── config/
│   │   ├── application-secrets.example.yml    Plantilla (versionada)
│   │   └── application-secrets.yml            Credenciales reales (NO versionar)
│   └── src/main/
│       ├── java/com/dotacioneszambrano/inventorio/    Backend Spring Boot
│       └── resources/
│           ├── application.yml
│           ├── templates/                              Template Thymeleaf del PDF
│           │   └── information_general_pdf.html
│           └── static/                                 Frontend integrado
│               ├── index.html          (login)
│               ├── inventario.html
│               ├── InformationGeneral.html
│               ├── recuperar.html
│               ├── reset.html
│               ├── config.js           (API_BASE centralizado)
│               ├── inventario.js
│               ├── InformationGeneral.js
│               ├── alerts.js
│               ├── styles.css
│               └── InformationGeneral.css
├── .gitignore
└── README.md
```

## Fases del proyecto

| Fase | Alcance | Estado |
|------|---------|--------|
| 1    | Backend Java completo con todos los endpoints, config, PDF, sesión, y frontend integrado como recursos estáticos | ✅ Completa |
| 2    | Rediseño visual del frontend (design system, responsive real, UX)                                                 | ⏳ Pendiente |
| 3    | Tests comparativos Python ↔ Java + validación final                                                                | ⏳ Pendiente |

## Puesta en marcha

```bash
# 1. Copiar el ejemplo de credenciales y rellenarlo
cp backend-java/config/application-secrets.example.yml backend-java/config/application-secrets.yml
# editar el archivo con url/usuario/contraseña reales

# 2. Ejecutar backend + frontend (juntos)
cd backend-java
mvn spring-boot:run
```

Abre <http://localhost:8080>. El login (`index.html`) se sirve directamente
desde Spring Boot. Todas las llamadas a la API son al mismo origen (no hay CORS).

Ver `backend-java/README.md` para detalles completos.

## Credenciales y secretos

**Nunca** se commitean. Todos los valores sensibles viven en
`backend-java/config/application-secrets.yml`, que está ignorado por Git.
El archivo `backend-java/config/application-secrets.example.yml` es la plantilla
(con valores vacíos).

## Notas de la integración frontend↔backend

- El frontend fue movido a `backend-java/src/main/resources/static/` para que
  Spring Boot lo sirva automáticamente (welcome page = `index.html`).
- Se creó `config.js` como único punto de configuración del `API_BASE`.
  Actualmente vale `""` (same-origin). Para apuntar a otro backend basta editar
  ese archivo.
- Se eliminaron las URLs hardcodeadas a `render.dotacioneszambrano.com` y
  `dotacioneszambrano.com` en todo el frontend.
- El endpoint de salud del backend Python era `/`; se movió a `/health` para
  no colisionar con el `index.html` servido como welcome page.
- Se corrigieron los redirects a `login.html` (archivo inexistente) apuntando
  ahora a `index.html`.

