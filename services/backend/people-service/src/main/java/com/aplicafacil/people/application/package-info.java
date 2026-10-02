/**
 * Aplicación: solo conoce al dominio.
 * <ul>
 *   <li>{@code port.in}: contratos de entrada que usan los adaptadores de infrastructure.</li>
 *   <li>{@code port.out}: contratos de salida que implementan los adaptadores (persistence).</li>
 *   <li>{@code services}: implementaciones de {@code port.in}; orquestan dominio y {@code port.out}.</li>
 *   <li>{@code dto}, {@code mapper}, {@code exception}: frontera hacia afuera.</li>
 * </ul>
 */
package com.aplicafacil.people.application;
