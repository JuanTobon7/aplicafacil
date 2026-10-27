import { apiHeaders } from "./http";

/**
 * SSE con fetch (EventSource no permite enviar X-Local-Token ni Authorization). Si el stream falla,
 * se pasa a polling con `poll` cada `intervalMs`.
 */
export function subscribe<T>(
  path: string,
  onEvent: (data: T) => void,
  options: { poll?: () => Promise<T>; intervalMs?: number; isFinal?: (data: T) => boolean } = {},
): () => void {
  const controller = new AbortController();
  let stopped = false;
  let pollTimer: number | undefined;

  const emit = (data: T) => {
    if (stopped) return;
    onEvent(data);
    if (options.isFinal?.(data)) stop();
  };

  const startPolling = () => {
    if (stopped || !options.poll) return;
    const tick = async () => {
      try {
        emit(await options.poll!());
      } catch {
        /* se reintenta en el siguiente ciclo */
      }
      if (!stopped) pollTimer = window.setTimeout(tick, options.intervalMs ?? 2000);
    };
    void tick();
  };

  const stop = () => {
    stopped = true;
    controller.abort();
    window.clearTimeout(pollTimer);
  };

  // Si el servidor cierra el stream sin estado final, o falla, se sigue por polling
  void readStream(path, controller.signal, emit, () => stopped)
    .catch(() => undefined)
    .then(() => {
      if (!stopped) startPolling();
    });

  return stop;
}

async function readStream<T>(path: string, signal: AbortSignal, emit: (data: T) => void, stopped: () => boolean) {
  const res = await fetch(`/api${path}`, { headers: apiHeaders({ Accept: "text/event-stream" }), signal });
  if (!res.ok || !res.body) throw new Error(`SSE ${res.status}`);
  const reader = res.body.pipeThrough(new TextDecoderStream()).getReader();
  let buffer = "";
  while (!stopped()) {
    const { value, done } = await reader.read();
    if (done) return;
    const events = (buffer + value).split(/\r?\n\r?\n/);
    buffer = events.pop() ?? "";
    events.map(dataOf).filter(Boolean).forEach((data) => emit(JSON.parse(data) as T));
  }
}

/** Las líneas `data:` de un evento SSE, unidas. */
function dataOf(raw: string): string {
  return raw
    .split(/\r?\n/)
    .filter((line) => line.startsWith("data:"))
    .map((line) => line.slice(5).trimStart())
    .join("\n");
}
