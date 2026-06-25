export class ClientBackend {
    constructor(
        private readonly baseUrl: string
    ) {
        this.baseUrl = process.env.BACKEND_URL || baseUrl;
    }
}