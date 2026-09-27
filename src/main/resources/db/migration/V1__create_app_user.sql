CREATE TABLE app_user (
                          id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                          email         VARCHAR(255) NOT NULL UNIQUE,
                          password_hash VARCHAR(255) NOT NULL,
                          full_name     VARCHAR(120) NOT NULL,
                          role          VARCHAR(20)  NOT NULL CHECK (role IN ('CUSTOMER', 'ADMIN')),
                          created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);