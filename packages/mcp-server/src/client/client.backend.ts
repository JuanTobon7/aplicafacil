import {
    Axios,
    AxiosRequestConfig,
    AxiosResponse,
} from "axios";

export class ClientBackend {

    constructor(
        private readonly  baseUrl: string = process.env.BACKEND_URL ?? "http://localhost:3000",
        private readonly client: Axios
    ) {}

    async get<T>(
        url: string,
        config?: AxiosRequestConfig
    ): Promise<T> {

        const response: AxiosResponse<T> =
            await this.client.get(this.baseUrl + url, config);

        return response.data;
    }

    async post<T>(
        url: string,
        body?: unknown,
        config?: AxiosRequestConfig
    ): Promise<T> {

        const response: AxiosResponse<T> =
            await this.client.post(this.baseUrl + url, body, config);

        return response.data;
    }

    async put<T>(
        url: string,
        body?: unknown,
        config?: AxiosRequestConfig
    ): Promise<T> {

        const response =
            await this.client.put<T>(this.baseUrl + url, body, config);

        return response.data;
    }

    async delete<T>(
        url: string,
        config?: AxiosRequestConfig
    ): Promise<T> {

        const response =
            await this.client.delete<T>(this.baseUrl + url, config);

        return response.data;
    }
}