# Merchant Onboarding & Compliance Specification (EU)

## Purpose
Defines onboarding, compliance, and risk workflow aligned with EU AML and GDPR.

## Workflow
DRAFT → SUBMITTED → VERIFICATION → SCREENING → RISK → DECISION → SETUP → ACTIVE → MONITORING

## Key Components
- Business Verification (KYB)
- UBO Identification
- Sanctions & PEP Screening
- Risk Scoring
- Limits & Reserve Policy
- Decisioning
- Monitoring

## GDPR Principles
- Lawful basis (AML/legal obligation)
- Data minimization
- Purpose limitation
- Data retention (5–10 years)
- Data subject rights
- Encryption & RBAC
- EU data storage

## Core Entities
- Merchant
- BusinessProfile
- Person (UBO/Director)
- BankAccount
- ScreeningCase
- RiskAssessment
- DecisionRecord
- AuditEvent

## API Examples
POST /onboarding/applications
PUT /business-profile
POST /screening/run
POST /risk/calculate
POST /approve

## Notes
- Platform manages sub-merchants
- TrueLayer handles payments/payouts
- Internal ledger required
