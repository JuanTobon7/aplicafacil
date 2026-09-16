/**
 * Puerto de caché de la capa de aplicación.
 *
 * Permite que los use cases tengan política de caché (e-tags, TTL)
 * sin depender de Redis ni de ningún proveedor concreto.
 */
export interface CachePort {
  /**
   * GET múltiple en un solo round-trip.
   * Devuelve el mismo orden que `keys`; null si la caché no está disponible.
   */
  mget(keys: string[]): Promise<(string | null)[] | null>;

  /**
   * SET con TTL en segundos.
   */
  set(key: string, value: string, ttlSeconds: number): Promise<void>;
}