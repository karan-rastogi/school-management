ALTER TABLE notices
    ADD COLUMN attachment_key          VARCHAR(255),
    ADD COLUMN attachment_file_name    VARCHAR(255),
    ADD COLUMN attachment_content_type VARCHAR(150),
    ADD COLUMN attachment_size_bytes   BIGINT,
    ADD COLUMN attachment_uploaded_at  TIMESTAMPTZ,
    ADD CONSTRAINT ck_notices_attachment_present
        CHECK ((attachment_key IS NULL) = (attachment_file_name IS NULL));
