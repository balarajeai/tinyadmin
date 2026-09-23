-- Seed Unlock User Safe Action definition
-- Production seed only - test fixtures belong in test setup

-- Unlock User Safe Action
INSERT INTO action_definitions (id, organization_id, key, name, description, status,
                               requires_confirmation, production_extra_confirm, max_affected_records,
                               rollback_policy, effect_definition, created_at, updated_at)
SELECT 
    '00000000-0000-0000-0000-000000000005'::uuid,
    id,
    'unlock-user',
    'Unlock User',
    'Unlocks a locked user account by setting locked=false',
    'ENABLED',
    true,
    true,
    1,
    'CONDITIONAL',
    '{"table":"users","fields":{"locked":{"from":true,"to":false}}}',
    NOW(),
    NOW()
FROM organizations
WHERE slug = 'test-org'
ON CONFLICT DO NOTHING;
