export type FileLike =
    | Buffer
    | { buffer: Buffer } // ej: Express Multer file
    | { base64: string } // ej: payload subido como base64
    | { path: string } // ej: archivo temporal en disco
    | { content: Buffer | string }; // genérico
 
export function isBufferLike(file: any): file is Buffer {
    return Buffer.isBuffer(file);
}
 