CREATE TABLE IF NOT EXISTS member (
    id          VARCHAR(26)  PRIMARY KEY,
    email       VARCHAR(255) NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    provider    VARCHAR(50)  NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    work_email  VARCHAR(255),
    role        VARCHAR(50)  NOT NULL DEFAULT 'MARKETER'
                    CHECK (role IN ('MARKETER', 'AGENCY_MANAGER', 'OPERATOR')),
    status      VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE', 'DORMANT', 'SUSPENDED',
                                      'SECURITY_LOCKOUT', 'PENDING_DELETION', 'DELETED')),
    deleted_at  TIMESTAMP,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS notification_setting (
    id              VARCHAR(26)  PRIMARY KEY,
    member_id       VARCHAR(26)  NOT NULL UNIQUE REFERENCES member(id),
    inquiry_alert   BOOLEAN      NOT NULL DEFAULT true,
    marketing_alert BOOLEAN      NOT NULL DEFAULT false,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS history (
    id           VARCHAR(26)  PRIMARY KEY,
    entity_type  VARCHAR(100) NOT NULL,
    entity_id    VARCHAR(26)  NOT NULL,
    action       VARCHAR(50)  NOT NULL,
    before_value JSONB,
    after_value  JSONB,
    actor_id     VARCHAR(26)  NOT NULL,
    operator_id  VARCHAR(26),
    shadow_id    VARCHAR(26),
    is_shadow    BOOLEAN      NOT NULL DEFAULT false,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);
