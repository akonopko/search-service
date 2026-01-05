CREATE TABLE IF NOT EXISTS document_chunks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,

    content         TEXT NOT NULL,

    chunk_summary   TEXT,

    status task_status DEFAULT 'PENDING',
    error_message   TEXT,
    attempts        INTEGER DEFAULT 0,

    created_at      TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS document_chunk_embeddings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    chunk_id        UUID NOT NULL REFERENCES document_chunks(id) ON DELETE CASCADE,
    content         TEXT NOT NULL,
    embedding       vector(768),

    created_at      TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ON document_chunk_embeddings USING hnsw (embedding vector_cosine_ops);
CREATE INDEX ON document_chunks (document_id);

CREATE INDEX idx_chunks_processing_queue ON document_chunks (document_id, status, attempts)
WHERE status = 'PENDING';

CREATE TRIGGER update_chunks_updated_at
    BEFORE UPDATE ON document_chunks
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();