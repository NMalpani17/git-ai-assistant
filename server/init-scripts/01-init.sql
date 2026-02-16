-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Git commands table with vector embeddings
CREATE TABLE IF NOT EXISTS git_commands (
                                            id SERIAL PRIMARY KEY,
                                            command VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    usage_scenario TEXT,
    example TEXT,
    risk_level VARCHAR(20) DEFAULT 'SAFE',
    category VARCHAR(50),
    embedding vector(1536),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

-- Index for vector similarity search
CREATE INDEX IF NOT EXISTS git_commands_embedding_idx
    ON git_commands USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 10);

-- Session logs table
CREATE TABLE IF NOT EXISTS session_logs (
                                            id SERIAL PRIMARY KEY,
                                            session_id VARCHAR(100) NOT NULL,
    user_query TEXT NOT NULL,
    generated_command TEXT,
    safety_level VARCHAR(20),
    response_time_ms INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

CREATE INDEX IF NOT EXISTS session_logs_session_idx ON session_logs(session_id);