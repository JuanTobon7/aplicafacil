import { Injectable, Logger } from '@nestjs/common';
import { Page } from 'puppeteer';

/**
 * Servicio de "comportamiento humano".
 *
 * Centraliza los delays aleatorios y las acciones de interacción
 * (escribir, click, scroll) para que la automatización no actúe de
 * forma instantánea y robótica. LinkedIn detecta patrones demasiado
 * rápidos y uniformes; introducir pausas variables reduce ese riesgo.
 *
 * Los rangos se configuran por variables de entorno:
 *
 * - `LINKEDIN_HUMAN_DELAY_MIN_MS` (por defecto 300)
 * - `LINKEDIN_HUMAN_DELAY_MAX_MS` (por defecto 1200)
 * - `LINKEDIN_HUMAN_TYPE_MIN_MS`  (por defecto 60)  — delay por tecla
 * - `LINKEDIN_HUMAN_TYPE_MAX_MS`  (por defecto 180) — delay por tecla
 */
@Injectable()
export class HumanBehaviorService {
  private readonly logger = new Logger(HumanBehaviorService.name);

  private readonly delayMin: number;
  private readonly delayMax: number;
  private readonly typeMin: number;
  private readonly typeMax: number;

  constructor() {
    this.delayMin = this.readEnv('LINKEDIN_HUMAN_DELAY_MIN_MS', 300);
    this.delayMax = this.readEnv('LINKEDIN_HUMAN_DELAY_MAX_MS', 1200);
    this.typeMin = this.readEnv('LINKEDIN_HUMAN_TYPE_MIN_MS', 60);
    this.typeMax = this.readEnv('LINKEDIN_HUMAN_TYPE_MAX_MS', 180);
  }

  private readEnv(name: string, fallback: number): number {
    const raw = process.env[name];
    const parsed = raw ? Number(raw) : NaN;
    return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback;
  }

  /**
   * Devuelve un entero aleatorio en el rango [min, max].
   */
  private randomBetween(min: number, max: number): number {
    const lo = Math.min(min, max);
    const hi = Math.max(min, max);
    return Math.floor(Math.random() * (hi - lo + 1)) + lo;
  }

  /**
   * Pausa aleatoria entre acciones (por defecto 300–1200 ms).
   */
  async wait(min = this.delayMin, max = this.delayMax): Promise<void> {
    const ms = this.randomBetween(min, max);
    await new Promise((resolve) => setTimeout(resolve, ms));
  }

  /**
   * Pausa aleatoria por cada tecla al escribir (por defecto 60–180 ms).
   */
  async typeDelay(): Promise<number> {
    return this.randomBetween(this.typeMin, this.typeMax);
  }

  /**
   * Escribe en un input de forma humana: pausa antes, escribe con
   * delay variable por tecla y pausa después.
   */
  async type(
    page: Page,
    selector: string,
    text: string,
  ): Promise<void> {
    await this.wait();
    await page.type(selector, text, {
      delay: await this.typeDelay(),
    });
    await this.wait();
  }

  /**
   * Hace click de forma humana: pausa antes y después del click.
   */
  async click(page: Page, selector: string): Promise<void> {
    await this.wait();
    await page.click(selector);
    await this.wait();
  }

  /**
   * Hace click sobre un elemento ya resuelto (ElementHandle) con
   * pausas humanas antes y después.
   */
  async clickElement(
    element: { click: () => Promise<void> },
  ): Promise<void> {
    await this.wait();
    await element.click();
    await this.wait();
  }

  /**
   * Scroll humano: avanza en pasos pequeños con pausas variables.
   */
  async scroll(
    page: Page,
    containerSelector: string,
    stepPx = 400,
  ): Promise<void> {
    await page.evaluate(
      async (selector: string, step: number) => {
        const container = document.querySelector(selector);
        if (!container) return;

        await new Promise<void>((resolve) => {
          let totalHeight = 0;
          const timer = setInterval(() => {
            const scrollHeight = container.scrollHeight;
            container.scrollBy(0, step);
            totalHeight += step;

            if (totalHeight >= scrollHeight) {
              clearInterval(timer);
              resolve();
            }
          }, 200);
        });
      },
      containerSelector,
      stepPx,
    );
    await this.wait();
  }
}