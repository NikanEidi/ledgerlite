CREATE TABLE account (
                         id             UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
                         owner_id       UUID           NOT NULL REFERENCES app_user (id),
                         account_number VARCHAR(20)    NOT NULL UNIQUE,
                         type           VARCHAR(20)    NOT NULL CHECK (type IN ('CHECKING', 'SAVINGS')),
                         balance        NUMERIC(19, 4) NOT NULL DEFAULT 0 CHECK (balance >= 0),
                         created_at     TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_account_owner_id ON account (owner_id);