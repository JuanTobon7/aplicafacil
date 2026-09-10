/**
 * Logger Utility - Centralized logging for the MCP Server
 */
export class Logger {
  private timestamp(): string {
    return new Date().toISOString();
  }

  info(message: string, data?: any) {
    console.log(`[${this.timestamp()}] ℹ️  INFO: ${message}`, data || '');
  }

  debug(message: string, data?: any) {
    console.log(`[${this.timestamp()}] 🐛 DEBUG: ${message}`, data || '');
  }

  warn(message: string, data?: any) {
    console.warn(`[${this.timestamp()}] ⚠️  WARN: ${message}`, data || '');
  }

  error(message: string, error?: any) {
    console.error(
      `[${this.timestamp()}] ❌ ERROR: ${message}`,
      error instanceof Error ? error.message : error || ''
    );
  }

  success(message: string, data?: any) {
    console.log(`[${this.timestamp()}] ✅ SUCCESS: ${message}`, data || '');
  }
}

export const logger = new Logger();
