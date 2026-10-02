-- Tabla del PgVectorStore de Spring AI (embeddings de perfiles, skills,
-- experiencias y educación). La extensión "vector" la crea infra/postgres/init.
CREATE TABLE IF NOT EXISTS vector_store (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content   TEXT,
    metadata  JSON,
    embedding public.vector(1536)
);

CREATE INDEX IF NOT EXISTS vector_store_embedding_idx
    ON vector_store USING HNSW (embedding public.vector_cosine_ops);
