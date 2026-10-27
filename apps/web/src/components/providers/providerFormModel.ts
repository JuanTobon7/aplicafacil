import type { ModelParameters, NewProviderConfig, ProviderChanges, ProviderConfig, ProviderType } from "../../api/ai";
import type { FieldErrors } from "../form/Field";

/** Lo que la persona escribe; los números van como texto hasta validarlos. */
export type ProviderFormValues = {
  providerType: string;
  displayName: string;
  apiKey: string;
  model: string;
  baseUrl: string;
  temperature: string;
  maxTokens: string;
  topP: string;
  enabled: boolean;
};

const API_KEY_PATTERN = /^[\x21-\x7E]{8,512}$/;
const BASE_URL_PATTERN = /^(https:\/\/|http:\/\/(127\.0\.0\.1|localhost)[:/])/;

function toNumber(value: string): number | null {
  return value.trim() === "" ? null : Number(value);
}

function text(value: number | null | undefined): string {
  return value?.toString() ?? "";
}

export function initialValues(types: ProviderType[], editing?: ProviderConfig): ProviderFormValues {
  return editing ? fromConfig(editing) : blank(types[0]);
}

function blank(type: ProviderType | undefined): ProviderFormValues {
  return {
    providerType: type?.type ?? "",
    displayName: "",
    apiKey: "",
    model: type?.defaultModel ?? "",
    baseUrl: "",
    temperature: "",
    maxTokens: "",
    topP: "",
    enabled: true,
  };
}

function fromConfig(config: ProviderConfig): ProviderFormValues {
  return {
    providerType: config.providerType,
    displayName: config.displayName,
    apiKey: "",
    model: config.model,
    baseUrl: config.baseUrl ?? "",
    temperature: text(config.parameters.temperature),
    maxTokens: text(config.parameters.maxTokens),
    topP: text(config.parameters.topP),
    enabled: config.enabled,
  };
}

/** Cambiar de proveedor reinicia lo que depende de él. */
export function withProviderType(v: ProviderFormValues, types: ProviderType[], type: string): ProviderFormValues {
  return { ...v, providerType: type, model: types.find((t) => t.type === type)?.defaultModel ?? "", baseUrl: "" };
}

function outOfRange(value: string, min: number, max: number, integer = false): boolean {
  const n = toNumber(value);
  if (n === null) return false;
  return Number.isNaN(n) || n < min || n > max || (integer && !Number.isInteger(n));
}

/** Las mismas reglas que valida ai-config-service; aquí solo para avisar antes de enviar. */
export function validateProviderForm(v: ProviderFormValues, isNew: boolean): FieldErrors {
  const e: FieldErrors = {};
  if (!v.displayName.trim()) e.displayName = "Ponle un nombre para reconocerla.";
  if (isNew && !API_KEY_PATTERN.test(v.apiKey.trim())) e.apiKey = "Pega la API key completa, sin espacios.";
  if (outOfRange(v.temperature, 0, 2)) e.temperature = "Entre 0 y 2.";
  if (outOfRange(v.maxTokens, 1, 200000, true)) e.maxTokens = "Entero entre 1 y 200000.";
  if (outOfRange(v.topP, 0, 1)) e.topP = "Entre 0 y 1.";
  if (v.baseUrl.trim() && !BASE_URL_PATTERN.test(v.baseUrl.trim())) {
    e.baseUrl = "Usa https (o http solo para 127.0.0.1 / localhost).";
  }
  return e;
}

function parameters(v: ProviderFormValues): ModelParameters {
  return { temperature: toNumber(v.temperature), maxTokens: toNumber(v.maxTokens), topP: toNumber(v.topP) };
}

export function toNewConfig(v: ProviderFormValues): NewProviderConfig {
  return {
    providerType: v.providerType,
    displayName: v.displayName.trim(),
    apiKey: v.apiKey.trim(),
    model: v.model.trim(),
    baseUrl: v.baseUrl.trim() || null,
    parameters: parameters(v),
    enabled: v.enabled,
  };
}

export function toChanges(v: ProviderFormValues, editing: ProviderConfig): ProviderChanges {
  return {
    displayName: v.displayName.trim(),
    model: v.model.trim(),
    baseUrl: v.baseUrl.trim() || null,
    parameters: parameters(v),
    expectedVersion: editing.version,
  };
}
