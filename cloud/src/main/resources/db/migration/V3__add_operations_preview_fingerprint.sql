-- Add preview_fingerprint column to operations table
-- Required for preview confirmation flow (Issue #18 / ADR 0007)
-- Column stores SHA-256 hex digest of canonical preview context for tamper detection

ALTER TABLE operations ADD COLUMN preview_fingerprint VARCHAR(64);
