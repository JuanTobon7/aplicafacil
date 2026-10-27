import { BadRequestException } from "@nestjs/common";
import type { StorageState } from "../domain/storage-state";

export type LoginRequest = { attemptId: string; loginUrl?: string; timeoutMs?: number };

export function parseLoginRequest(body: Partial<LoginRequest> | undefined): LoginRequest {
  if (!body?.attemptId) throw new BadRequestException("attemptId es obligatorio");
  return { attemptId: body.attemptId, loginUrl: body.loginUrl, timeoutMs: body.timeoutMs };
}

export function parseStorageState(body: { storageState?: unknown } | undefined): StorageState {
  if (!body?.storageState || typeof body.storageState !== "object") {
    throw new BadRequestException("storageState es obligatorio");
  }
  return body.storageState as StorageState;
}
