import type { Response } from "express";

/** Respuesta en streaming NDJSON: una línea JSON por evento. */
export class NdjsonStream {
  private constructor(private readonly res: Response) {}

  static open(res: Response): NdjsonStream {
    res.status(200);
    res.setHeader("Content-Type", "application/x-ndjson");
    res.setHeader("Cache-Control", "no-store");
    res.flushHeaders();
    return new NdjsonStream(res);
  }

  send(event: object) {
    this.res.write(JSON.stringify(event) + "\n");
  }

  end() {
    this.res.end();
  }
}
