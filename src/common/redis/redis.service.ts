import { Injectable, Logger, OnModuleDestroy } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import Redis from 'ioredis';

/**
 * Wrapper de Redis con degradación silenciosa.
 *
 * Si Redis no está disponible, todas las operaciones devuelven null/vacío
 * sin lanzar excepciones: el flujo de recomendaciones continúa llamando al LLM
 * como si no hubiera caché.
 */
@Injectable()
export class RedisService implements OnModuleDestroy {
  private readonly logger = new Logger(RedisService.name);
  private readonly client: Redis | null;
  private connected = false;

  constructor(private readonly configService: ConfigService) {
    try {
      this.client = new Redis({
        host: this.configService.get<string>('REDIS_HOST') ?? 'localhost',
        port: Number(this.configService.get<string>('REDIS_PORT')) ?? 6379,
        password: this.configService.get<string>('REDIS_PASSWORD') ?? undefined,
        // Falla rápido en vez de encolar comandos cuando Redis está caído
        enableOfflineQueue: false,
        maxRetriesPerRequest: 1,
        connectTimeout: 2000,
      });

      this.client.on('connect', () => {
        this.connected = true;
        this.logger.log('Conectado a Redis');
      });
      this.client.on('close', () => {
        this.connected = false;
      });
      this.client.on('error', (err) => {
        this.connected = false;
        this.logger.warn(`Redis no disponible (${err.message}). La caché de e-tags se omite.`);
      });
    } catch (err) {
      this.logger.warn(
        `No se pudo inicializar Redis: ${err instanceof Error ? err.message : String(err)}`,
      );
      this.client = null;
    }
  }

  async onModuleDestroy(): Promise<void> {
    await this.client?.quit().catch(() => undefined);
  }

  isConnected(): boolean {
    return this.connected;
  }

  async get(key: string): Promise<string | null> {
    return this.run((client) => client.get(key));
  }

  /**
   * GET múltiple en un solo round-trip.
   * Devuelve el mismo orden que `keys`; null si Redis no está disponible.
   */
  async mget(keys: string[]): Promise<(string | null)[] | null> {
    if (keys.length === 0) return [];
    return this.run((client) => client.mget(keys));
  }

  async set(key: string, value: string, ttlSeconds: number): Promise<void> {
    await this.run((client) => client.set(key, value, 'EX', ttlSeconds));
  }

  // ------------------------------------------------------------------
  private async run<T>(fn: (client: Redis) => Promise<T>): Promise<T | null> {
    if (!this.client) return null;
    try {
      return await fn(this.client);
    } catch (err) {
      this.connected = false;
      this.logger.warn(
        `Operación Redis fallida: ${err instanceof Error ? err.message : String(err)}`,
      );
      return null;
    }
  }
}
