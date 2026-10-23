// Centralized API client for the Content Library sample.
//
// API_BASE is derived from Vite's BASE_URL (the deployment base path set in
// vite.config.js), so the SPA calls the co-hosted Spring Boot API under the
// SAME path prefix it is served from:
//   <BASE_PATH>/api/...   e.g. /content-library-management-with-react-java-docx-editor/api/...
// This keeps the app working unchanged in dev (Vite proxies the path to
// localhost:9090), in production (Spring Boot serves the SPA from its own
// static resources under server.servlet.context-path), and on Azure App
// Service where the sample is hosted under the same sub-path.
const BASE_URL = import.meta.env.BASE_URL || '/';
export const API_BASE = `${BASE_URL.replace(/\/$/, '')}/api`;

const CONTENT_BASE = `${API_BASE}/contentlibrary`;

async function handle(response) {
    if (!response.ok) {
        const text = await response.text().catch(() => '');
        throw new Error(`HTTP ${response.status} ${response.statusText}: ${text}`);
    }
    // Some endpoints (delete) return 204; the rest return JSON.
    const ct = response.headers.get('content-type') || '';
    if (ct.includes('application/json')) {
        // Defensive: a misconfigured reverse proxy can return a 200
        // with an HTML body when the actual API is unreachable. The
        // JSON.parse would throw a SyntaxError; we catch and rethrow
        // as a clear "API not reachable" error so the UI doesn't
        // try to use a string as an array and crash.
        const text = await response.text();
        try {
            return JSON.parse(text);
        } catch {
            throw new Error(`Expected JSON but got: ${text.slice(0, 200)}`);
        }
    }
    return response.text();
}

export async function listItems() {
    return handle(await fetch(`${CONTENT_BASE}/items`));
}

export async function getItem(id) {
    return handle(await fetch(`${CONTENT_BASE}/items/${id}`));
}

// Returns the Versions array (and minimal metadata) for a single item.
// Used by the version-history dialog.
export async function getItemVersions(id) {
    const item = await getItem(id);
    return {
        id: item.id,
        title: item.title,
        category: item.category,
        status: item.status,
        author: item.author,
        currentFile: item.currentFile,
        currentVersion: item.currentVersion,
        versions: (item.versions || []).slice().sort((a, b) => b.versionNumber - a.versionNumber),
    };
}

export async function uploadDocument(file, metadata) {
    const form = new FormData();
    // The Java webservice upload endpoint expects the file part named "files".
    form.append('files', file);
    for (const [k, v] of Object.entries(metadata || {})) {
        if (v != null) form.append(k, v);
    }
    return handle(await fetch(`${CONTENT_BASE}/upload`, { method: 'POST', body: form }));
}

// Loads a stored version of a document from the Java webservice as SFDT
// (the JSON format the Syncfusion document editor opens). `version` is
// optional and defaults to the current version on the server.
export async function loadSfdt(id, version) {
    const v = version ? `?version=${version}` : '';
    const response = await fetch(`${CONTENT_BASE}/items/${id}/sfdt${v}`);
    if (!response.ok) {
        throw new Error(`Load SFDT failed: HTTP ${response.status}`);
    }
    return response.text();
}

export async function saveNewVersion(id, base64Data, modifiedUser) {
    return handle(await fetch(`${CONTENT_BASE}/items/${id}/versions`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ documentData: base64Data, modifiedUser })
    }));
}

export async function updateStatus(id, status) {
    return handle(await fetch(`${CONTENT_BASE}/items/${id}/status`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status })
    }));
}

export async function updateMetadata(id, payload) {
    return handle(await fetch(`${CONTENT_BASE}/items/${id}/metadata`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    }));
}

export async function deleteItem(id) {
    return handle(await fetch(`${CONTENT_BASE}/items/${id}`, { method: 'DELETE' }));
}

export function downloadUrl(id, version) {
    const v = version ? `?version=${version}` : '';
    return `${CONTENT_BASE}/items/${id}/download${v}`;
}

export async function exportCombinedDocx(ids) {
    const response = await fetch(`${CONTENT_BASE}/export/combined-docx`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(ids)
    });
    if (!response.ok) throw new Error('Combined DOCX export failed.');
    return response.blob();
}

// Merge selected DOCX items on the server and persist the result as a
// new ContentItem in the library. Returns the new item (same shape as
// listItems entries) so the UI can refresh + select it.
//
// `metadata` is optional: { title, category, status, author, version }.
// When omitted the server uses sensible defaults.
export async function mergeAndSaveDocx(ids, metadata) {
    const payload = { ids, ...(metadata || {}) };
    const response = await fetch(`${CONTENT_BASE}/merge-docx`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });
    if (!response.ok) {
        const text = await response.text().catch(() => '');
        throw new Error(`Merge failed: HTTP ${response.status} ${text}`);
    }
    return response.json();
}

export function downloadBlob(blob, fileName) {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
}
