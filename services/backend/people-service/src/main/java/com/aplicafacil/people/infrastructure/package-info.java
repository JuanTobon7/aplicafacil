/**
 * Infrastructure: adaptadores de entrada (REST en {@code web}, eventos consumidos en {@code messaging}).
 * Solo usan {@code application.port.in} y sus DTOs; nunca el dominio ni persistence.
 * Un adaptador de salida (p. ej. publicar un evento) no va aquí: se declara en
 * {@code application.port.out} y se implementa del lado de persistence.
 */
package com.aplicafacil.people.infrastructure;
