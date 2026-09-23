-- Seed test organization, environment, agent, connection, and Unlock User action
-- This seed data supports the Unlock User vertical slice

-- Test Organization
INSERT INTO organizations (id, name, slug, status, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'Test Organization', 'test-org', 'ACTIVE', NOW(), NOW());

-- Test Environment (Production)
INSERT INTO environments (id, organization_id, key, display_name, kind, status, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 
        'production', 'Production', 'PRODUCTION', 'ACTIVE', NOW(), NOW());

-- Test Agent
INSERT INTO agents (id, organization_id, environment_id, display_name, status, 
                    public_key_material, activated_at, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000002', 'Test Agent', 'ACTIVE',
        'test-public-key', NOW(), NOW(), NOW());

-- Test Connection
INSERT INTO connections (id, organization_id, environment_id, agent_id, name, 
                        db_engine, customer_secret_ref, status, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000003',
        'Test PostgreSQL Connection', 'POSTGRESQL', 'DB_SECRET_REF', 'READY', NOW(), NOW());

-- Unlock User Safe Action
INSERT INTO action_definitions (id, organization_id, key, name, description, status,
                               requires_confirmation, production_extra_confirm, max_affected_records,
                               rollback_policy, effect_definition, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000001',
        'unlock-user', 'Unlock User', 
        'Unlocks a locked user account by setting locked=false',
        'ENABLED', true, true, 1, 'CONDITIONAL',
        '{"table":"users","fields":{"locked":{"from":true,"to":false}}}',
        NOW(), NOW());
