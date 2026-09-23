-- TinyAdmin Cloud V1 Initial Schema
-- Issue #18: Unlock User PostgreSQL vertical slice

-- Organizations
CREATE TABLE organizations (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_organizations_slug ON organizations(slug);
CREATE INDEX idx_organizations_status ON organizations(status);

-- Environments
CREATE TABLE environments (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    key VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    kind VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_environments_org_key UNIQUE (organization_id, key)
);

CREATE INDEX idx_environments_org ON environments(organization_id);
CREATE INDEX idx_environments_org_key ON environments(organization_id, key);

-- Agents
CREATE TABLE agents (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    environment_id UUID NOT NULL REFERENCES environments(id),
    display_name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    public_key_material TEXT,
    activated_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    last_seen_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_agents_org ON agents(organization_id);
CREATE INDEX idx_agents_env ON agents(environment_id);
CREATE INDEX idx_agents_org_env ON agents(organization_id, environment_id);
CREATE INDEX idx_agents_status ON agents(status);

-- Connections
CREATE TABLE connections (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    environment_id UUID NOT NULL REFERENCES environments(id),
    agent_id UUID NOT NULL REFERENCES agents(id),
    name VARCHAR(255) NOT NULL,
    db_engine VARCHAR(50) NOT NULL,
    metadata TEXT,
    customer_secret_ref VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_connections_org_env_name UNIQUE (organization_id, environment_id, name)
);

CREATE INDEX idx_connections_org ON connections(organization_id);
CREATE INDEX idx_connections_env ON connections(environment_id);
CREATE INDEX idx_connections_agent ON connections(agent_id);
CREATE INDEX idx_connections_org_env ON connections(organization_id, environment_id);

-- Action Definitions
CREATE TABLE action_definitions (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    key VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL,
    requires_confirmation BOOLEAN NOT NULL,
    production_extra_confirm BOOLEAN NOT NULL,
    max_affected_records INTEGER,
    rollback_policy VARCHAR(50) NOT NULL,
    effect_definition TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_action_definitions_org_key UNIQUE (organization_id, key)
);

CREATE INDEX idx_action_definitions_org ON action_definitions(organization_id);
CREATE INDEX idx_action_definitions_org_key ON action_definitions(organization_id, key);
CREATE INDEX idx_action_definitions_status ON action_definitions(status);

-- Operations
CREATE TABLE operations (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    environment_id UUID NOT NULL REFERENCES environments(id),
    agent_id UUID REFERENCES agents(id),
    connection_id UUID REFERENCES connections(id),
    actor_user_id UUID,
    kind VARCHAR(50) NOT NULL,
    action_definition_id UUID REFERENCES action_definitions(id),
    target TEXT,
    parameters TEXT,
    lifecycle_status VARCHAR(50) NOT NULL,
    preview_id UUID,
    confirmation_id UUID,
    parent_operation_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    terminal_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_operations_org ON operations(organization_id);
CREATE INDEX idx_operations_env ON operations(environment_id);
CREATE INDEX idx_operations_org_env_created ON operations(organization_id, environment_id, created_at);
CREATE INDEX idx_operations_lifecycle_updated ON operations(lifecycle_status, updated_at);
CREATE INDEX idx_operations_action ON operations(action_definition_id);
CREATE INDEX idx_operations_agent ON operations(agent_id);
CREATE INDEX idx_operations_connection ON operations(connection_id);

-- Previews
CREATE TABLE previews (
    id UUID PRIMARY KEY,
    operation_id UUID,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    environment_id UUID NOT NULL REFERENCES environments(id),
    agent_id UUID REFERENCES agents(id),
    connection_id UUID REFERENCES connections(id),
    actor_user_id UUID,
    kind VARCHAR(50) NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expected_affected_count INTEGER,
    expected_affected_summary TEXT,
    limitation_flags TEXT,
    staleness_hint TEXT,
    status VARCHAR(50) NOT NULL
);

CREATE INDEX idx_previews_org ON previews(organization_id);
CREATE INDEX idx_previews_env ON previews(environment_id);
CREATE INDEX idx_previews_operation ON previews(operation_id);

-- Confirmations
CREATE TABLE confirmations (
    id UUID PRIMARY KEY,
    operation_id UUID NOT NULL,
    actor_user_id UUID,
    confirmed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    acknowledged_limitation_flags TEXT,
    production_ack BOOLEAN,
    preview_fingerprint TEXT
);

CREATE INDEX idx_confirmations_operation ON confirmations(operation_id);
CREATE UNIQUE INDEX uk_confirmations_operation ON confirmations(operation_id);

-- Audit Events
CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    environment_id UUID REFERENCES environments(id),
    actor_user_id UUID,
    agent_id UUID,
    connection_id UUID,
    operation_id UUID,
    event_type VARCHAR(255) NOT NULL,
    action_definition_id UUID,
    target TEXT,
    before_state TEXT,
    after_state TEXT,
    result_status VARCHAR(255),
    rollback_of_operation_id UUID,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    security_context TEXT
);

CREATE INDEX idx_audit_events_org ON audit_events(organization_id);
CREATE INDEX idx_audit_events_org_occurred ON audit_events(organization_id, occurred_at);
CREATE INDEX idx_audit_events_operation ON audit_events(operation_id);
CREATE INDEX idx_audit_events_event_type ON audit_events(event_type);

-- Audit append-only enforcement: revoke UPDATE and DELETE for application role
-- This will be set up per deployment with appropriate role grants
-- For now, document the requirement in comments
-- REVOKE UPDATE, DELETE ON audit_events FROM tinyadmin_app_role;
