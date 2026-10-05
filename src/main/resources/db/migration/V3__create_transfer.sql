CREATE TABLE transfer (
                          id               UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
                          initiator_id     UUID           NOT NULL REFERENCES app_user (id),
                          from_account_id  UUID           NOT NULL REFERENCES account (id),
                          to_account_id    UUID           NOT NULL REFERENCES account (id),
                          amount           NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
                          idempotency_key  VARCHAR(64)    NOT NULL,
                          created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
                          CONSTRAINT transfer_no_self CHECK (from_account_id <> to_account_id),
                          CONSTRAINT transfer_idempotency_unique UNIQUE (initiator_id, idempotency_key)
);

CREATE INDEX idx_transfer_from_account ON transfer (from_account_id);
CREATE INDEX idx_transfer_to_account ON transfer (to_account_id);
