-- ==========================================
-- CRÉER EXTENSION PGVECTOR (pour embeddings)
-- ==========================================
CREATE EXTENSION IF NOT EXISTS vector;

-- ==========================================
-- TABLES BACKEND EXISTANTES (10 tables)
-- ==========================================

-- TABLE 1: USERS (créée par User.java)
CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    keycloak_id VARCHAR(255) NOT NULL UNIQUE,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    profile_type VARCHAR(50) DEFAULT 'PRUDENT',
    avatar_url VARCHAR(500),
    first_login BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- TABLE 2: COMPANY (créée par Company.java)
CREATE TABLE IF NOT EXISTS company (
    id SERIAL PRIMARY KEY,
    ticker VARCHAR(20) UNIQUE NOT NULL,
    name VARCHAR(255),
    sector VARCHAR(100),
    country VARCHAR(100),
    description TEXT,
    logo_url VARCHAR(500),
    website VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- TABLE 3: NEWS (créée par News.java)
CREATE TABLE IF NOT EXISTS news (
    id SERIAL PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    content TEXT,
    source VARCHAR(255),
    ticker VARCHAR(20),
    news_date TIMESTAMP,
    url VARCHAR(1000),
    sentiment_score FLOAT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- TABLE 4: ALERT (créée par Alert.java)
CREATE TABLE IF NOT EXISTS alert (
    id SERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    ticker VARCHAR(20),
    alert_type VARCHAR(50),
    severity VARCHAR(20),
    message TEXT,
    read BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- TABLE 5: CHAT_SESSIONS (créée par ChatSession.java)
CREATE TABLE IF NOT EXISTS chat_sessions (
    id SERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(255),
    context_type VARCHAR(20),
    company_id BIGINT REFERENCES company(id) ON DELETE SET NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_message_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- TABLE 6: CHAT_MESSAGES (créée par ChatMessage.java)
CREATE TABLE IF NOT EXISTS chat_messages (
    id SERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    sender VARCHAR(10) NOT NULL,
    message TEXT NOT NULL,
    intent VARCHAR(50),
    nci_snapshot FLOAT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- TABLE 7: DOCUMENTS_EMBEDDINGS (créée par DocumentsEmbedding.java)
-- NOTE: Nécessite pgvector extension
CREATE TABLE IF NOT EXISTS documents_embeddings (
    id SERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    embedding vector(1536),
    section VARCHAR(255),
    fiscal_year INTEGER,
    chunk_order INTEGER
);

-- TABLE 8: NCI_HISTORY (créée par NciHistory.java)
CREATE TABLE IF NOT EXISTS nci_history (
    id SERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    nci_value FLOAT NOT NULL,
    reason VARCHAR(255),
    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- TABLE 9: USER_STRATEGIES (créée par UserStrategy.java)
CREATE TABLE IF NOT EXISTS user_strategies (
    id SERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    company_id BIGINT NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    user_argument TEXT,
    nci_personalized FLOAT,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- TABLE 10: WATCHLIST (créée par WatchlistEntry.java)
CREATE TABLE IF NOT EXISTS watchlist (
    id SERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    company_id BIGINT NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    pinned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, company_id)
);

-- ==========================================
-- NOUVELLES TABLES MULTI-AGENT-ASSISTANT (3 tables)
-- ==========================================

-- TABLE 11: SAVED_REPORTS (Multi-Agent)
CREATE TABLE IF NOT EXISTS saved_reports (
    id SERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    ticker VARCHAR(10) NOT NULL,
    report_title VARCHAR(255),
    pdf_content BYTEA,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- TABLE 12: FAVORITE_COMPANIES (Multi-Agent)
CREATE TABLE IF NOT EXISTS favorite_companies (
    id SERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    ticker VARCHAR(10) NOT NULL,
    company_name VARCHAR(255),
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, ticker)
);

-- TABLE 13: STRATEGY_UPDATE_LOGS (Multi-Agent)
CREATE TABLE IF NOT EXISTS strategy_update_logs (
    id SERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    strategy_id VARCHAR(50),
    ticker VARCHAR(10),
    update_type VARCHAR(50),
    update_content TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ==========================================
-- INDEXES POUR PERFORMANCE
-- ==========================================

-- Indexes pour users
CREATE INDEX IF NOT EXISTS idx_users_keycloak_id ON users(keycloak_id);

-- Indexes pour company
CREATE INDEX IF NOT EXISTS idx_company_ticker ON company(ticker);

-- Indexes pour news
CREATE INDEX IF NOT EXISTS idx_news_ticker ON news(ticker);
CREATE INDEX IF NOT EXISTS idx_news_date ON news(news_date);

-- Indexes pour alert
CREATE INDEX IF NOT EXISTS idx_alert_user ON alert(user_id);
CREATE INDEX IF NOT EXISTS idx_alert_ticker ON alert(ticker);

-- Indexes pour chat
CREATE INDEX IF NOT EXISTS idx_chat_sessions_user ON chat_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_chat_sessions_company ON chat_sessions(company_id);
CREATE INDEX IF NOT EXISTS idx_chat_messages_session ON chat_messages(session_id);

-- Indexes pour documents_embeddings
CREATE INDEX IF NOT EXISTS idx_documents_embeddings_company ON documents_embeddings(company_id);

-- Indexes pour nci_history
CREATE INDEX IF NOT EXISTS idx_nci_history_company ON nci_history(company_id);

-- Indexes pour user_strategies
CREATE INDEX IF NOT EXISTS idx_user_strategies_user ON user_strategies(user_id);
CREATE INDEX IF NOT EXISTS idx_user_strategies_company ON user_strategies(company_id);

-- Indexes pour watchlist
CREATE INDEX IF NOT EXISTS idx_watchlist_user ON watchlist(user_id);
CREATE INDEX IF NOT EXISTS idx_watchlist_company ON watchlist(company_id);

-- Indexes pour saved_reports (Multi-Agent)
CREATE INDEX IF NOT EXISTS idx_saved_reports_user ON saved_reports(user_id);
CREATE INDEX IF NOT EXISTS idx_saved_reports_ticker ON saved_reports(ticker);

-- Indexes pour favorite_companies (Multi-Agent)
CREATE INDEX IF NOT EXISTS idx_favorite_companies_user ON favorite_companies(user_id);

-- Indexes pour strategy_update_logs (Multi-Agent)
CREATE INDEX IF NOT EXISTS idx_strategy_update_logs_user ON strategy_update_logs(user_id);