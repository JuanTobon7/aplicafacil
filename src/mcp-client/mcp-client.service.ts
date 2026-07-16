import { Injectable, Logger } from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { ConfigService } from '@nestjs/config';
import { firstValueFrom } from 'rxjs';

export interface FillFormRequest {
  system: string;
  prompt: string;
}

export interface FillFormResponse {
  content: Array<{
    type: string;
    text: string;
  }>;
}

export interface EmbeddingRequest {
  data: any;
}

export interface EmbeddingResponse {
  embedding: number[];
}

@Injectable()
export class McpClientService {
  private readonly logger = new Logger(McpClientService.name);
  private readonly mcpServerUrl: string;

  constructor(
    private readonly httpService: HttpService,
    private readonly configService: ConfigService,
  ) {
    this.mcpServerUrl =
      this.configService.get<string>('MCP_SERVER_URL') ||
      'http://localhost:3001';
  }

  /**
   * Llama al MCP server para rellenar un formulario usando IA
   * @param request Contiene el system prompt y el prompt del usuario
   * @returns La respuesta del LLM en formato JSON
   */
  async fillForm(request: FillFormRequest): Promise<string> {
    try {
      this.logger.debug(
        `Calling MCP server at ${this.mcpServerUrl}/tools/fill-form`,
      );
      const response = await firstValueFrom(
        this.httpService.post<FillFormResponse>(
          `${this.mcpServerUrl}/tools/fill-form`,
          request,
          {
            timeout: 30000,
          },
        ),
      );

      if (!response.data?.content?.[0]?.text) {
        throw new Error('Invalid response format from MCP server');
      }

      console.debug('Response from MCP server:', response.data.content[0].text);
      return response.data.content[0].text;
    } catch (error) {
      this.logger.error(
        `Error calling fillForm from MCP server: ${error instanceof Error ? error.message : String(error)}`,
      );
      throw error;
    }
  }

  /**
   * Obtiene el embedding de un texto usando el MCP server
   * @param data El texto o datos para los cuales obtener el embedding
   * @returns El vector de embedding
   */
  async getEmbedding(data: any): Promise<number[]> {
    try {
      this.logger.debug(
        `Calling MCP server at ${this.mcpServerUrl}/tools/embeddings`,
      );

      const response = await firstValueFrom(
        this.httpService.post<EmbeddingResponse>(
          `${this.mcpServerUrl}/tools/embeddings`,
          { data },
          {
            timeout: 10000,
          },
        ),
      );

      if (!Array.isArray(response.data?.embedding)) {
        throw new Error('Invalid embedding response from MCP server');
      }

      return response.data.embedding;
    } catch (error) {
      this.logger.error(
        `Error calling getEmbedding from MCP server: ${error instanceof Error ? error.message : String(error)}`,
      );
      throw error;
    }
  }

  /**
   * Busca un perfil por ID usando el MCP server
   * @param profileId El ID del perfil a buscar
   * @returns Los datos del perfil
   */
  async searchProfileById(profileId: string): Promise<any> {
    try {
      this.logger.debug(
        `Calling MCP server at ${this.mcpServerUrl}/tools/search-profile`,
      );

      const response = await firstValueFrom(
        this.httpService.get(`${this.mcpServerUrl}/tools/search-profile`, {
          params: { id: profileId },
          timeout: 10000,
        }),
      );

      return response.data;
    } catch (error) {
      this.logger.error(
        `Error calling searchProfileById from MCP server: ${error instanceof Error ? error.message : String(error)}`,
      );
      throw error;
    }
  }

  /**
   * Busca personas usando criterios del MCP server
   * @param criteria Criterios de búsqueda
   * @returns Lista de personas encontradas
   */
  async searchPeople(criteria: any): Promise<any[]> {
    try {
      this.logger.debug(
        `Calling MCP server at ${this.mcpServerUrl}/tools/search-people`,
      );

      const response = await firstValueFrom(
        this.httpService.post(
          `${this.mcpServerUrl}/tools/search-people`,
          criteria,
          {
            timeout: 15000,
          },
        ),
      );

      return response.data;
    } catch (error) {
      this.logger.error(
        `Error calling searchPeople from MCP server: ${error instanceof Error ? error.message : String(error)}`,
      );
      throw error;
    }
  }
}
