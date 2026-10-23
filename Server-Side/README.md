# Java-Webservice — Content Library sample (Java 17 + Spring Boot + React)

Syncfusion Document Editor Content Library sample: a Spring Boot 3.2 (Java 17)
webservice with a React (Vite) client that lets users upload, version, edit,
merge, and export reusable DOCX content items.

## Path mapping / deployment layout

The sample is hosted under a **sub-path**:

```
https://<host>/content-library-management-with-react-java-docx-editor/
```

Both the client and the server use this same prefix:

| Piece | Config | File |
|---|---|---|
| Spring context path | `server.servlet.context-path=/content-library-management-with-react-java-docx-editor` | `src/main/resources/application.properties` |
| Vite build base | `base: '/content-library-management-with-react-java-docx-editor/'` | `Client-Side/vite.config.js` |
| Client API calls | `API_BASE = import.meta.env.BASE_URL + 'api'` (same origin, same prefix) | `Client-Side/src/api.js` |

## How the client is served by the Java app

You do **not** need to copy the `dist` folder manually. `npm run build` in
`Client-Side/` writes the production bundle **directly into the Spring Boot
jar's static-resources folder** (`build.outDir = ../src/main/resources/static`
in `vite.config.js`). Spring Boot serves everything under
`src/main/resources/static/` at the context path automatically, so the SPA
and the REST API are same-origin — no CORS, no separate static hosting.

Build order:

```bash
# 1. Build the client (outputs into src/main/resources/static/)
cd Client-Side
npm install
npm run build

# 2. Package the jar (includes the SPA)
cd ..
./gradlew bootJar          # -> build/libs/ej2-webservices-0.0.1-SNAPSHOT.jar
```

The `src/main/resources/static/` folder is git-ignored; it is a build artifact.

## Azure App Service

* **Stack**: Java 17 (or 21), embedded web server — deploy the executable jar
  (`build/libs/ej2-webservices-*.jar`).
* **Port**: Azure injects `PORT`; Spring Boot binds to it automatically
  (relaxed binding). If your app uses a fixed port instead, set the app
  setting `WEBSITE_PORT` / `WEBSITES_PORT` to match `server.port`.
* **Path mapping**: no extra Azure configuration is needed — the sub-path is
  part of the app itself (context path + Vite base). Requests to
  `/<context-path>/api/**` hit Spring MVC; `/<context-path>/**` fall through
  to the bundled SPA index.html.
* **Storage**: the Content Library writes to `App_Data/ContentLibrary/`
  **relative to the JVM working directory**. On Azure App Service (Linux)
  this is `/home/site/wwwroot/App_Data/ContentLibrary` unless overridden.
  App Service filesystem is **ephemeral** — data does not survive re-scale /
  re-deploy. For durable storage either mount Azure Storage, or point the
  webservice at `/home` (persistent, not replicated) — see
  `ContentLibraryService` (`Paths.get("App_Data", "ContentLibrary")`).

## Development

* **Server**: `./gradlew bootRun` → `http://localhost:9090/content-library-management-with-react-java-docx-editor/api/...`
  (port 9090 is set in `RestServiceCorsApplication`).
* **Client**: `cd Client-Side && npm run dev` → Vite on port 5173, proxying
  `<BASE_PATH>/api` to `http://localhost:9090` (see `vite.config.js`).

## Notes

* The Excel export from the ASP.NET Core sample is intentionally omitted —
  Syncfusion XlsIO is not available on the Java platform.
* All original `/api/wordeditor/*` Document Editor endpoints are unchanged.