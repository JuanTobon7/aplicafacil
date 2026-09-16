import { PDFDocument, PDFName,PDFDict } from 'pdf-lib';
import pdfParse from 'pdf-parse';
import { FileLike, isBufferLike } from 'src/profiles/types/types.file';
import { FileInterceptor } from '../contract/file.interceptor';

export class PdfFileInterceptor extends FileInterceptor {
    async getBufferFromFile(file: FileLike): Promise<Buffer> {
        if (isBufferLike(file)) return file;

        if ('buffer' in file && file.buffer) {
            return file.buffer;
        }

        if ('base64' in file && file.base64) {
            return Buffer.from(file.base64, 'base64');
        }

        if ('path' in file && file.path) {
            const fs = await import('fs/promises');
            return fs.readFile(file.path);
        }

        if ('content' in file && file.content) {
            return Buffer.isBuffer(file.content)
                ? file.content
                : Buffer.from(file.content, 'utf-8');
        }

        throw new Error('No se pudo extraer el Buffer del archivo PDF proporcionado');
    }

    /**
     * Elimina JavaScript embebido, acciones automáticas (OpenAction),
     * y archivos adjuntos incrustados que puedan ser vectores de ataque.
     */
    async sanitizeFile(file: FileLike): Promise<Buffer> {
        const buffer = await this.getBufferFromFile(file);

        const pdfDoc = await PDFDocument.load(buffer, {
            ignoreEncryption: true,
        });

        const catalog = pdfDoc.catalog;

        // Elimina OpenAction (ejecución automática de JS/acciones al abrir el PDF)
        catalog.delete(PDFName.of('OpenAction'));

        // Elimina el diccionario Names -> JavaScript (scripts embebidos en el documento)
        const namesDict = catalog.lookupMaybe(
            PDFName.of('Names'),
            PDFDict
        );
        if (namesDict && typeof (namesDict as any).delete === 'function') {
            (namesDict as any).delete(PDFName.of('JavaScript'));
        }

        // Elimina AA (Additional Actions) a nivel de documento y de páginas
        catalog.delete(PDFName.of('AA'));
        for (const page of pdfDoc.getPages()) {
            (page.node as any).delete?.(PDFName.of('AA'));
        }

        // Elimina AcroForm (formularios con posibles acciones/JS al enviarse)
        catalog.delete(PDFName.of('AcroForm'));

        const sanitizedBytes = await pdfDoc.save({
            useObjectStreams: false,
        });

        return Buffer.from(sanitizedBytes);
    }

    /**
     * "Reduce" el PDF: extrae únicamente el texto plano, descartando todo
     * el marcado/estructura interna del PDF (fuentes, posiciones, imágenes, etc).
     */
    async reduceFile(file: FileLike): Promise<string> {
        const buffer = await this.getBufferFromFile(file);
        const data = await pdfParse(buffer);

        const textOnly = data.text.replace(/\s+/g, ' ').trim();

        return textOnly
    }
}