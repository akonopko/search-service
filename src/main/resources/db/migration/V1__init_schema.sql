CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE clients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    first_name VARCHAR NOT NULL,
    last_name VARCHAR NOT NULL,
    email VARCHAR NOT NULL UNIQUE,
    description TEXT,
    social_links TEXT[] DEFAULT '{}',
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_clients_full_name_lower ON clients (LOWER(last_name), LOWER(first_name));

CREATE INDEX idx_clients_search_trgm ON clients USING GIN (
    LOWER(
        COALESCE(first_name, '') || ' ' ||
        COALESCE(last_name, '') || ' ' ||
        COALESCE(email, '') || ' ' ||
        COALESCE(description, '')
    ) gin_trgm_ops
);

ALTER TABLE clients ADD CONSTRAINT email_must_be_lower CHECK (email = LOWER(email));

CREATE TYPE task_status AS ENUM ('PENDING', 'PROCESSING', 'READY', 'FAILED');

CREATE TABLE documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    summary TEXT,
    summary_status task_status DEFAULT 'PENDING',
    summary_error_message TEXT,
    summary_attempts INTEGER DEFAULT 0,
    status task_status DEFAULT 'PENDING',
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_clients_updated_at
    BEFORE UPDATE ON clients
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_documents_updated_at
    BEFORE UPDATE ON documents
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();