-- Búsqueda solo de ofertas remotas (LinkedIn f_WT=2, Computrabajo /trabajo-de-{q}-en-remoto).
ALTER TABLE job_search ADD COLUMN remote BOOLEAN NOT NULL DEFAULT FALSE;
