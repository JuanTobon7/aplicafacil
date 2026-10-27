package com.aplicafacil.computrabajo.application.services;

import java.time.Duration;

/** @param loginTimeout tiempo máximo para iniciar sesión en la ventana (incluye el acceso con Google) */
public record ConnectPolicy(Duration loginTimeout) {
}
