-- Opaque referral codes (stop deriving codes from user UUID prefixes).
ALTER TABLE users ADD COLUMN IF NOT EXISTS referral_code VARCHAR(16);

CREATE UNIQUE INDEX IF NOT EXISTS users_referral_code_uidx
    ON users (referral_code)
    WHERE referral_code IS NOT NULL;
