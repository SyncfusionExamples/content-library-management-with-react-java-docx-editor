import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The deployment base path. This MUST match the Java webservice's
// server.servlet.context-path in src/main/resources/application.properties,
// because the built client is served from the same Spring Boot app
// (bundled into src/main/resources/static/ by `npm run build`).
// When hosted on Azure App Service the sample lives under this path:
//   https://<host>/content-library-management-with-react-java-docx-editor/
export const BASE_PATH = '/content-library-management-with-react-java-docx-editor/'

export default defineConfig({
    base: BASE_PATH,
    plugins: [react()],
    // Output the production build straight into the Spring Boot jar's
    // static resources so `./gradlew bootJar` serves the SPA + API from
    // one origin under the same path prefix. Relative to Client-Side/.
    build: {
        outDir: '../src/main/resources/static',
        emptyOutDir: true,
    },
    server: {
        port: 5173,
        proxy: {
            // In dev, /api is proxied to the local Java backend. The proxy
            // target is the ROOT (no context path) of the Spring Boot app
            // and the path below is appended, so it already includes the
            // context path — matching production, where API_BASE is
            // BASE_PATH + '/api'.
            '/content-library-management-with-react-java-docx-editor/api': {
                target: 'http://localhost:9090',
                changeOrigin: true,
                secure: false
            }
        }
    }
})