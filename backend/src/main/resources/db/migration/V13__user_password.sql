-- Optional password credentials for the simple email + password sign-in.
--
-- password_hash holds a BCrypt hash only; the plaintext password is never stored, logged or
-- returned. It is nullable because an account may sign in exclusively through Google or phone OTP.
-- must_change_password forces a password change on the next successful sign-in (used when an admin
-- sets a password).
ALTER TABLE users ADD COLUMN password_hash VARCHAR(255);
ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
