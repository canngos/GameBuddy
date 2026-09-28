-- A transfer has no store transaction ID of its own. Claim RevenueCat's stable event ID
-- in the same transaction as moving the entitlement, so webhook retries cannot move a
-- newer purchase that happened to land on the old account after the first transfer.
-- Run after upgrade-2026-45-report-cases.sql. Idempotent.

CREATE TABLE IF NOT EXISTS gamebuddy.revenuecat_transfer_event (
    event_id character varying(255) PRIMARY KEY,
    processed_at timestamp(6) with time zone NOT NULL
);
