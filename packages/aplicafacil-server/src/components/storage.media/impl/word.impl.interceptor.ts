import JSZip from 'jszip';
import mammoth from 'mammoth';
import { FileInterceptor } from '../contract/file.interceptor';
import { FileLike, isBufferLike } from 'src/profiles/types/types.file';

export class WordFileInterceptor extends FileInterceptor {
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

        throw new Error('No se pudo extraer el Buffer del archivo Word proporcionado');
    }

    /**
     * Un .docx es un ZIP (Open XML). Aquí eliminamos:
     * - Macros VBA (vbaProject.bin) -> vector clásico de malware en Word
     * - Objetos OLE incrustados (embeddings) que pueden contener ejecutables
     * - Referencias externas / templates remotos (attachedTemplate) que
     *   podrían usarse para "template injection"
     */
    async sanitizeFile(file: FileLike): Promise<Buffer> {
        const buffer = await this.getBufferFromFile(file);
        const zip = await JSZip.loadAsync(buffer);

        const dangerousPatterns = [
            /^word\/vbaProject\.bin$/i,
            /^word\/embeddings\//i,
            /^word\/activeX\//i,
            /vbaData\.xml$/i,
        ];

        for (const filePath of Object.keys(zip.files)) {
            if (dangerousPatterns.some((pattern) => pattern.test(filePath))) {
                zip.remove(filePath);
            }
        }

        // Quita la referencia a plantillas remotas (evita template injection)
        const settingsPath = 'word/settings.xml';
        const settingsFile = zip.file(settingsPath);
        if (settingsFile) {
            let settingsXml = await settingsFile.async('string');
            settingsXml = settingsXml.replace(/<w:attachedTemplate[^/]*\/>/g, '');
            zip.file(settingsPath, settingsXml);
        }

        // Si el documento se declaraba como habilitado para macros, lo
        // regresamos a un content-type normal de docx
        const contentTypesPath = '[Content_Types].xml';
        const contentTypesFile = zip.file(contentTypesPath);
        if (contentTypesFile) {
            let contentTypesXml = await contentTypesFile.async('string');
            contentTypesXml = contentTypesXml.replace(
                /application\/vnd\.ms-word\.document\.macroEnabled\.main\+xml/g,
                'application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml'
            );
            zip.file(contentTypesPath, contentTypesXml);
        }

        const sanitizedBuffer = await zip.generateAsync({ type: 'nodebuffer' });
        return sanitizedBuffer;
    }

    /**
     * "Reduce" el Word: extrae solo el texto plano, descartando todo el
     * XML/estructura (estilos, tablas, imágenes, encabezados, etc).
     */
    async reduceFile(file: FileLike): Promise<Buffer> {
        const buffer = await this.getBufferFromFile(file);
        const result = await mammoth.extractRawText({ buffer });

        const textOnly = result.value.replace(/\s+/g, ' ').trim();

        return Buffer.from(textOnly, 'utf-8');
    }

}