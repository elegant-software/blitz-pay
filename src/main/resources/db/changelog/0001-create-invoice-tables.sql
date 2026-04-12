-- liquibase formatted sql

-- changeset codex:0001-create-invoices-table
CREATE TABLE invoices (
    id UUID NOT NULL PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    amount BIGINT NOT NULL,
    payment_status VARCHAR(20) NOT NULL,
    CONSTRAINT chk_invoices_payment_status
        CHECK (payment_status IN ('PENDING', 'PAID', 'RECEIVED'))
);
-- rollback DROP TABLE invoices;

-- changeset codex:0001-create-invoice-recipients-table
CREATE TABLE invoice_recipients (
    id UUID NOT NULL PRIMARY KEY,
    invoice_id UUID NOT NULL,
    recipient_type VARCHAR(20) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    group_id UUID,
    group_name VARCHAR(255),
    customer_reference VARCHAR(255),
    CONSTRAINT chk_invoice_recipients_type
        CHECK (recipient_type IN ('PERSON', 'GROUP')),
    CONSTRAINT fk_invoice_recipients_invoice
        FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE CASCADE
);
-- rollback DROP TABLE invoice_recipients;

-- changeset codex:0001-create-invoice-status-index
CREATE INDEX idx_invoices_payment_status ON invoices (payment_status);
-- rollback DROP INDEX idx_invoices_payment_status;

-- changeset codex:0001-create-invoice-recipient-invoice-index
CREATE INDEX idx_invoice_recipients_invoice_id ON invoice_recipients (invoice_id);
-- rollback DROP INDEX idx_invoice_recipients_invoice_id;
