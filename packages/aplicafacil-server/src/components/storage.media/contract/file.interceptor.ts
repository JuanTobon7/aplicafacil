export abstract class FileInterceptor {
    abstract sanitizeFile(file: any): Promise<any>;
    abstract reduceFile(file: any): Promise<any>;
    abstract getBufferFromFile(file: any): Promise<Buffer>;
}