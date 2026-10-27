package com.aplicafacil.profile.application.port.out;

/** Saca el texto de un CV (PDF, Word). La implementación vive en infrastructure (Tika). */
public interface CvTextExtractor {

    /** @return el texto, o vacío si el archivo no tiene texto legible (por ejemplo, un PDF escaneado) */
    String extract(CvFile file);

    /** @param contentType el que declaró el navegador; puede ser {@code null} */
    record CvFile(String fileName, String contentType, byte[] content) {
    }
}
