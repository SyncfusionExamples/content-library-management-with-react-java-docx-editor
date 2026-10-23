import { Workbook } from '@syncfusion/ej2-xlsx';
import { getItemText } from './api';

// Column layout — same as the Core sample export:
// Title, Version, Category, Status, Author, CreatedDate, ModifiedDate, PlainText
const HEADERS = ['Title', 'Version', 'Category', 'Status', 'Author', 'CreatedDate', 'ModifiedDate', 'PlainText'];
const COLUMNS = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H'];

// Header style per the Syncfusion JS Excel cell-formatting docs:
// bold Calibri font on a solid light-blue fill, matching the Core
// sample's header row look.
const HEADER_STYLE = {
    font: {
        name: 'Calibri',
        size: 11,
        bold: true,
        color: { rgb: '000000' },
    },
    fill: {
        patternType: 'solid',
        fgColor: { rgb: 'ADD8E6' },
    },
};

function cellValue(item, c) {
    switch (HEADERS[c]) {
        case 'Title': return item.title || '';
        case 'Version': return item.version || '';
        case 'Category': return item.category || '';
        case 'Status': return item.status || '';
        case 'Author': return item.author || '';
        case 'CreatedDate': return item.createdDate || '';
        case 'ModifiedDate': return item.modifiedDate || '';
        default: return '';
    }
}

// Builds the ContentExport.xlsx workbook for the selected items.
//
// The metadata (title/category/status/author/dates) is already in client
// state from the library list; the plain-text extract is fetched per item
// from the Java webservice (GET /items/{id}/text) — the same DOCX-to-text
// conversion the Core sample did server-side, now split so only the text
// crosses the wire and the Excel file is built in the browser with the
// Syncfusion JS Excel library.
export async function buildContentExport(selectedItems) {
    const workbook = Workbook.create();
    const sheet = workbook.sheet(0);
    sheet.name = 'ContentLibrary';

    // Header row
    for (let c = 0; c < HEADERS.length; c++) {
        const cell = sheet.cell(`${COLUMNS[c]}1`);
        cell.value = HEADERS[c];
        cell.style = HEADER_STYLE;
    }

    // Data rows: one per selected item, PlainText fetched from the server
    for (let r = 0; r < selectedItems.length; r++) {
        const item = selectedItems[r];
        const row = r + 2;
        for (let c = 0; c < HEADERS.length; c++) {
            const cell = sheet.cell(`${COLUMNS[c]}${row}`);
            if (HEADERS[c] === 'PlainText') {
                cell.value = await getItemText(item.id);
            } else {
                cell.value = cellValue(item, c);
            }
        }
    }

    // save() with no args (browser) returns the .xlsx bytes
    return await workbook.save();
}