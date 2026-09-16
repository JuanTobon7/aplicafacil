import { Injectable } from "@nestjs/common";
import { FileInterceptor } from "../contract/file.interceptor";
import { HtmlFileInterceptor } from "./html.impl.interceptor";
import { PdfFileInterceptor } from "./pdf.impl.interceptor";
import { WordFileInterceptor } from "./word.impl.interceptor";

export type SupportedFileType = 'html' | 'pdf' | 'docx';

const MIME_TYPE_MAP: Record<string, SupportedFileType> = {
    'text/html': 'html',
    'application/pdf': 'pdf',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document': 'docx',
    'application/vnd.ms-word.document.macroEnabled.12': 'docx',
    'application/msword': 'docx',
};

const EXTENSION_MAP: Record<string, SupportedFileType> = {
    '.html': 'html',
    '.htm': 'html',
    '.pdf': 'pdf',
    '.docx': 'docx',
    '.doc': 'docx',
};

export class FileInterceptorFactory {
    private static instances: Record<SupportedFileType, FileInterceptor> = {
        html: new HtmlFileInterceptor(),
        pdf: new PdfFileInterceptor(),
        docx: new WordFileInterceptor(),
    };

    /**
     * Obtiene el interceptor adecuado a partir de un mime type conocido.
     */
    static fromMimeType(mimeType: string): FileInterceptor {
        const type = MIME_TYPE_MAP[mimeType.toLowerCase()];
        if (!type) {
            throw new Error(`Tipo de archivo no soportado (mimeType): ${mimeType}`);
        }
        return this.instances[type];
    }

    /**
     * Obtiene el interceptor adecuado a partir del nombre/extensión del archivo.
     */
    static fromFileName(fileName: string): FileInterceptor {
        const ext = fileName.substring(fileName.lastIndexOf('.')).toLowerCase();
        const type = EXTENSION_MAP[ext];
        if (!type) {
            throw new Error(`Tipo de archivo no soportado (extensión): ${ext}`);
        }
        return this.instances[type];
    }

    /**
     * Obtiene el interceptor directamente por tipo explícito.
     */
    static fromType(type: SupportedFileType): FileInterceptor {
        return this.instances[type];
    }
}