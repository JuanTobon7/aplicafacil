/**
 * Estado de la sesión del navegador (cookies + origins). Es opaco y secreto: se recibe y se devuelve en memoria,
 * jamás se escribe en disco ni en logs.
 */
export type StorageState = Readonly<Record<string, unknown>>;
