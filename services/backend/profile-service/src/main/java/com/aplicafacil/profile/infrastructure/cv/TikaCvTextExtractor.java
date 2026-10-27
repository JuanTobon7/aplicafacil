package com.aplicafacil.profile.infrastructure.cv;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.aplicafacil.profile.application.port.out.CvTextExtractor;

/**
 * Texto de un CV con Apache Tika (PDF, DOC, DOCX). El archivo no se guarda: se lee en memoria y se descarta. Un
 * archivo dañado o protegido devuelve vacío y el caso de uso responde CV_UNREADABLE.
 */
@Component
public class TikaCvTextExtractor implements CvTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(TikaCvTextExtractor.class);
    /** Más que suficiente para un CV; acota la memoria ante un archivo raro. */
    private static final int MAX_CHARS = 200_000;

    private final Tika tika;

    public TikaCvTextExtractor() {
        this.tika = new Tika();
        this.tika.setMaxStringLength(MAX_CHARS);
    }

    @Override
    public String extract(CvFile file) {
        Metadata metadata = new Metadata();
        if (file.fileName() != null) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, file.fileName());
        }
        try (InputStream in = new ByteArrayInputStream(file.content())) {
            return tika.parseToString(in, metadata);
        } catch (IOException | TikaException e) {
            log.info("No se pudo leer el CV {}: {}", file.fileName(), e.getMessage());
            return "";
        }
    }
}
