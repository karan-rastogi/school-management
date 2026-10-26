ALTER TABLE refresh_tokens
    ADD COLUMN active_role VARCHAR(20);

ALTER TABLE refresh_tokens
    ADD CONSTRAINT ck_refresh_tokens_active_role
        CHECK (active_role IS NULL OR active_role IN ('ADMIN', 'TEACHER', 'PARENT'));
