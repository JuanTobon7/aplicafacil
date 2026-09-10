import { FileInterceptor } from 'src/components/storage.media/contract/file.interceptor';
import sanitizeHtml from 'sanitize-html';
import { FileLike, isBufferLike } from 'src/profiles/types/types.file';


export class HtmlFileInterceptor extends FileInterceptor {
    /**
     * Obtiene el Buffer real del archivo sin importar la forma en la que llegó.
     */
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

        throw new Error('No se pudo extraer el Buffer del archivo HTML proporcionado');
    }

    /**
     * Elimina código potencialmente malicioso: <script>, event handlers
     * (onclick, onerror...), iframes, objetos embebidos, javascript: en hrefs, etc.
     */
    async sanitizeFile(file: FileLike): Promise<Buffer> {
        const buffer = await this.getBufferFromFile(file);
        const html = buffer.toString('utf-8');

        const clean = sanitizeHtml(html, {
            allowedTags: sanitizeHtml.defaults.allowedTags.concat([
                'img', 'h1', 'h2', 'style'
            ]),
            allowedAttributes: {
                ...sanitizeHtml.defaults.allowedAttributes,
                '*': ['style', 'class'],
                img: ['src', 'alt', 'width', 'height'],
            },
            allowedSchemes: ['http', 'https', 'mailto', 'data'],
            disallowedTagsMode: 'discard',
            // Elimina explícitamente cualquier cosa ejecutable
            exclusiveFilter: (frame) => {
                const dangerousTags = ['script', 'iframe', 'object', 'embed', 'link', 'meta', 'form'];
                return dangerousTags.includes(frame.tag);
            },
        });

        return Buffer.from(clean, 'utf-8');
    }

    /**
     * "Reduce" el archivo: quita TODAS las etiquetas HTML dejando solo texto plano.
     */
    async reduceFile(file: FileLike): Promise<Buffer> {
        const buffer = await this.getBufferFromFile(file);
        const html = buffer.toString('utf-8');

        // Primero sanitizamos por seguridad, luego quitamos absolutamente todo tag
        const textOnly = sanitizeHtml(html, {
            allowedTags: [],
            allowedAttributes: {},
        })
            .replace(/&nbsp;/g, ' ')
            .replace(/\s+/g, ' ')
            .trim();

        return Buffer.from(textOnly, 'utf-8');
    }

    async getTextFromFile(file: FileLike): Promise<string> {
        const buffer = await this.reduceFile(file);
        return buffer.toString('utf-8');
    }
}