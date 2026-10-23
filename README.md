# Content Library Management

## Introduction

Content Library Management is a React + Java (Spring Boot) sample that provides a
centralized workspace for uploading, editing, versioning, and exporting
reusable DOCX content items using the Syncfusion<sup style="font-size:70%">&reg;</sup>
[React DOCX Editor](https://www.syncfusion.com/docx-editor-sdk/react-docx-editor?utm_source=github&utm_medium=listing&utm_campaign=github-github-documenteditor-examples) (Document Editor).

The sample is designed for organizations that need a reusable content
repository — for example, SOPs, HR policies, training material, safety
procedures, and finance templates — that multiple business documents
draw from over time.

Users can:

-   View all content items from a single dashboard table.
-   Upload new DOCX files with metadata (title, version, category, status, author).
-   Open a DOCX in the Syncfusion Word-like Document Editor with full ribbon toolbar, track changes, and comments.
-   Edit metadata for any item from a dialog (no need to open the editor).
-   Edit status of a document directly from the editor toolbar.
-   See the full version history of a DOCX item and open any previous version in the editor.
-   Merge multiple DOCX items into a single new document stored in the library, with custom title, category, status, and author supplied via a dialog.
-   Export selected items to a single combined DOCX or to an Excel workbook of metadata + plain text.
-   Download the current version of any document as an SFDT file.
-   Download the original (or a specific historical version) of any file.
-   Delete content items.

## Key Features

### Upload and Metadata

Uploading accepts `.docx`. Each item carries:

-   Title, Version, Category (SOP / HR / Safety / Training / Manufacturing / Finance / Other)
-   Status (Draft / Review / Approved)
-   Author, Created Date, Modified Date
-   List of stored versions (one file per save)

The metadata dialog lets users edit any of these fields for an existing
item without re-uploading.

### DOCX Editing with Syncfusion Document Editor

Selecting a DOCX row and clicking **Edit** opens the Syncfusion
Word-like Document Editor inside a modal dialog. The editor ships with
the full Ribbon toolbar (File / Home / Insert / Review / View …),
comments, and track changes support.

Track changes are driven by status:

-   **Draft** — track changes is off. Free editing.
-   **Review** or **Approved** — track changes is on. Every edit is recorded as a revision.
-   **Old version opened via History** — track changes is locked off (the version is a frozen snapshot).

The status dropdown in the editor footer saves the change to the
backend immediately, and the change is reflected in the home page row
without needing to save the document.

### Version History

Each save creates a new `v{n}.docx` on disk and adds a record to the
item's `Versions[]` array. The home-page toolbar exposes a **History**
button (enabled when exactly one DOCX is selected). It opens a dialog
that lists every stored version with its frozen status, author, and
modified timestamp, and lets the user open any historical version in
the editor or download it.

Versions are status-aware: a change to status is recorded against the
*current* version. Older versions keep the status they were saved with,
so the history view shows what state each version was in when it was
authored.

### Merge Selected DOCX Items

Users can select two or more DOCX items and click **Merge documents**.
A dialog asks for the new combined document's title, category, status,
author, and version. The server then concatenates the selected DOCX
content (separated by a metadata header per source) and persists the
result as a new library item. Once the merge completes, the editor
auto-opens the new document for review.

### Export to Combined DOCX or Excel

-   **Merge documents** (DOCX-only, ≥2 items) — saves a new library item (see above).
-   **Export Excel** (DOCX-only) — produces a single `.xlsx` whose rows are the selected DOCX items, with columns for Title, Version, Category, Status, Author, Created/Modified dates, and a plain-text extract of the document body. The workbook is generated **in the browser** with the Syncfusion JavaScript Excel library (`@syncfusion/ej2-xlsx`), because a Syncfusion Excel library is not available on the Java platform — the server only supplies the per-document plain text.

### Download SFDT

Selecting a single DOCX item and clicking **Download SFDT** converts
the stored `v{n}.docx` on the server (the same DOCX-to-SFDT conversion
the editor open uses) and downloads it client-side as
`{documentname}.sfdt`.

## Architecture

The sample consists of two applications:

- **Java webservice** (Spring Boot 3.2, Java 17) — exposes the Content Library REST endpoints and the Word Editor service endpoints, using the Syncfusion Java DocIO, Word Processor, SpellChecker, JavaHelper, and Licensing libraries. In production the webservice also serves the built React bundle from its own static resources, so the SPA and the API are deployed as one artifact.
- **React application** (`Client-Side/`) — Vite + React 19 UI with the Syncfusion Document Editor for in-browser DOCX editing and the Syncfusion JavaScript Excel library for client-side XLSX generation.

The Vite dev server proxies `/api/*` to the backend so the browser sees same-origin requests (no CORS). Files are stored on disk under `App_Data/ContentLibrary/{id}/v{n}.docx`; the metadata index lives in `App_Data/ContentLibrary/library.json`.

## Prerequisites

### Client

-   Node.js 18 or later (LTS recommended)
-   npm

### Server

-   JDK 17 (or later)
-   Gradle wrapper (included — no separate Gradle install needed)
-   Syncfusion Java packages referenced by `build.gradle` (DocIO, EJ2 Word Processor, SpellChecker, JavaHelper, Licensing — resolved from the Syncfusion Maven repository)

## How to Run

Start the Java webservice first because the React application uses the
server for DOCX import, save, status, merge, and export operations.

### 1. Start the Java Webservice

Open a terminal in the repository root:

``` text
Java-Webservice/
```

Run (development):

``` bash
./gradlew bootRun
```

or build and run the executable jar:

``` bash
./gradlew bootJar
java -jar build/libs/ej2-webservices-0.0.1-SNAPSHOT.jar
```

The configured development URL is:

``` text
http://localhost:9090
```

Keep this terminal running while using the React application.

### 2. Start the React Application

Open another terminal in:

``` text
Client-Side/
```

Install dependencies:

``` bash
npm install
```

Start the development server:

``` bash
npm run dev
```

Open the URL shown by Vite in the terminal, normally:

``` text
http://localhost:5173/
```

The React app's Vite config proxies all `/api/*` requests to the backend on
port `9090`. If you change the backend port, update `vite.config.js`
accordingly.

## Deployment (single artifact)

The React production build is bundled **into** the webservice jar —
there is no separate static hosting and no CORS in production:

``` bash
cd Client-Side
npm install
npm run build          # outputs into ../src/main/resources/static/
cd ..
./gradlew bootJar      # jar includes the SPA + the API
java -jar build/libs/ej2-webservices-0.0.1-SNAPSHOT.jar
```

Deploy the resulting jar to Azure App Service (or any Java 17 host). On
Azure App Service the injected `PORT` environment variable is picked up
automatically. Documents uploaded at runtime are stored in
`App_Data/ContentLibrary/` relative to the process working directory —
note that the App Service filesystem is not durable across
scale/reset operations, so use mounted Azure Storage for persistent
content.

## Server API

The Content Library controller exposes the following endpoints. The
Word Editor endpoints listed at the bottom are the default Document
Editor service endpoints.

### Content Library endpoints

| Endpoint | Purpose |
| --- | --- |
| `GET /api/contentlibrary/items` | List all content items (newest modified first). |
| `GET /api/contentlibrary/items/{id}` | Get a single item, including its `versions[]` array. |
| `POST /api/contentlibrary/upload` | Upload a new DOCX with metadata (file part named `files`). |
| `POST /api/contentlibrary/items/{id}/versions` | Save a new DOCX version (SFDT → DOCX). |
| `GET /api/contentlibrary/items/{id}/download` | Download the original or a specific version (`?version=N`). |
| `GET /api/contentlibrary/items/{id}/sfdt` | Convert a stored version to SFDT (used by the editor open and Download SFDT). |
| `GET /api/contentlibrary/items/{id}/text` | Extract readable plain text from a stored version (used by the client-side Excel export). |
| `DELETE /api/contentlibrary/items/{id}` | Delete the item and its folder. |
| `POST /api/contentlibrary/items/{id}/status` | Update workflow status (Draft / Review / Approved). |
| `POST /api/contentlibrary/items/{id}/metadata` | Update editable metadata. |
| `POST /api/contentlibrary/export/combined-docx` | Return a single combined DOCX for the given ids (download). |
| `POST /api/contentlibrary/merge-docx` | Merge selected DOCX items and persist the result as a new library item. |

### Word Editor service endpoints (used by the Syncfusion editor)

| Endpoint | Purpose |
| --- | --- |
| `POST /api/wordeditor/Import` | Imports a DOCX and converts it to SFDT. |
| `POST /api/wordeditor/Save` | Saves the edited SFDT content as a DOCX. |
| `POST /api/wordeditor/ExportSFDT` | Exports the edited SFDT content in the requested format. |
| `POST /api/wordeditor/SpellCheck` / `SpellCheckByPage` | Spell-check service for the editor. |
| `POST /api/wordeditor/RestrictEditing` | Computes the protection hash for restricted editing. |
| `POST /api/wordeditor/SystemClipboard` | Converts pasted content for the system clipboard. |

## Storage Layout

```text
{process working directory}/
  App_Data/
    ContentLibrary/
      library.json                          # metadata index (all items + versions)
      {human-readable-title}/              # one folder per item
        v1.docx                             # one file per save
        v2.docx
        v3.docx
```

Folder names are derived from each item's title (sanitized + a short
random suffix) so `App_Data/ContentLibrary` is easy to read on disk.
The folder is created relative to wherever the jar is launched from.

## Resources

- **Product page:** [Syncfusion React DOCX Editor](https://www.syncfusion.com/docx-editor-sdk/react-docx-editor?utm_source=github&utm_medium=listing&utm_campaign=github-github-documenteditor-examples)
- **Documentation:** [Syncfusion React DOCX Editor - Documentation](https://help.syncfusion.com/document-processing/word/word-processor/react/overview?utm_source=github&utm_medium=listing&utm_campaign=github-github-documenteditor-examples)
- **Online demo:** [Syncfusion React DOCX Editor - Online demo](https://document.syncfusion.com/demos/docx-editor/react/#/tailwind3/document-editor/default?utm_source=github&utm_medium=listing&utm_campaign=github-github-documenteditor-examples)
- **JavaScript Excel library (client-side export):** [Create Excel documents in React](https://help.syncfusion.com/document-processing/excel/excel-library/javascript/create-excel-document-react)

## Support and feedback

For any other queries, reach our [Syncfusion support team](https://support.syncfusion.com/?utm_source=github&utm_medium=listing&utm_campaign=github-github-documenteditor-examples) or post the queries through the [community forums](https://www.syncfusion.com/forums?utm_source=github&utm_medium=listing&utm_campaign=github-github-documenteditor-examples). 

Request new feature through [Syncfusion feedback portal](https://www.syncfusion.com/feedback?utm_source=github&utm_medium=listing&utm_campaign=github-github-documenteditor-examples). 

## License

This is a commercial product and requires a paid license for possession or use. Syncfusion's licensed software, including this component, is subject to the terms and conditions of [Syncfusion's EULA](https://www.syncfusion.com/license/studio/35.1.37/syncfusion_essential_studio_eula.pdf?utm_source=github&utm_medium=listing&utm_campaign=github-github-documenteditor-examples). You can purchase a license [here](https://www.syncfusion.com/sales/products?utm_source=github&utm_medium=listing&utm_campaign=github-github-documenteditor-examples) or start a free 30-day trial [here](https://www.syncfusion.com/account/manage-trials/start-trials?utm_source=github&utm_medium=listing&utm_campaign=github-github-documenteditor-examples).