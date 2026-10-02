package com.aplicafacil.profile.infrastructure.persistence.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Fila de {@code cvs}. La relación la posee {@link ProfileEntity} ({@code profiles.cv_id}),
 * como en el modelo original; el CV no conoce a su perfil.
 */
@Entity
@Table(name = "cvs")
public class CvEntity {

    @Id
    private UUID id;

    /** Referencia opaca al archivo (ruta, key de S3 o URL). Ver {@code Cv.location} en el dominio. */
    @Column(name = "file_path", nullable = false, length = 1024)
    private String filePath;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }
}
