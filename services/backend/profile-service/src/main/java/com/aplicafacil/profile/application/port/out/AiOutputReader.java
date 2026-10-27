package com.aplicafacil.profile.application.port.out;

import com.aplicafacil.profile.domain.ProfileContent;
import com.aplicafacil.profile.domain.SearchSuggestion;

/**
 * Lee la respuesta de la IA (JSON, a veces envuelto en texto o en un bloque de código) y la convierte al dominio.
 * Lo que no cumple el formato pedido sale como {@link InvalidAiOutputException}; una entrada inválida dentro de una
 * lista se descarta y el resto sigue.
 */
public interface AiOutputReader {

    ProfileContent readProfile(String output);

    SearchSuggestion readSuggestion(String output);

    class InvalidAiOutputException extends RuntimeException {

        public InvalidAiOutputException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
