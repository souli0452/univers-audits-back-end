CREATE TABLE IF NOT EXISTS audit_log (
    id            UUID         DEFAULT gen_random_uuid() PRIMARY KEY,
    agent_id      VARCHAR(100) NOT NULL,
    agent_name    VARCHAR(200),
    agent_role    VARCHAR(100),
    action        VARCHAR(80)  NOT NULL,
    entity_type   VARCHAR(60),
    entity_id     VARCHAR(100),
    description   VARCHAR(500),
    ip_address    VARCHAR(45),
    user_agent    VARCHAR(300),
    status        VARCHAR(20)  NOT NULL DEFAULT 'SUCCESS'
                      CHECK (status IN ('SUCCESS','FAILURE')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS login_log (
    id             UUID         DEFAULT gen_random_uuid() PRIMARY KEY,
    agent_id       VARCHAR(100) NOT NULL,
    agent_name     VARCHAR(200),
    success        BOOLEAN      NOT NULL DEFAULT TRUE,
    ip_address     VARCHAR(45),
    user_agent     VARCHAR(300),
    failure_reason VARCHAR(200),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_log_agent  ON audit_log(agent_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_action ON audit_log(action);
CREATE INDEX IF NOT EXISTS idx_audit_log_date   ON audit_log(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_login_log_agent  ON login_log(agent_id);
CREATE INDEX IF NOT EXISTS idx_login_log_date   ON login_log(created_at DESC);