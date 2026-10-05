CREATE TABLE loan_application (
                                  id               UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
                                  applicant_id     UUID           NOT NULL REFERENCES app_user (id),
                                  requested_amount NUMERIC(19, 4) NOT NULL CHECK (requested_amount > 0),
                                  status           VARCHAR(20)    NOT NULL
                                      CHECK (status IN ('SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED')),
                                  credit_score     INTEGER,
                                  created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
                                  updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_loan_applicant_id ON loan_application (applicant_id);