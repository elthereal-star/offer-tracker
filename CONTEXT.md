# Offer Tracker Domain Context

## Purpose

Offer Tracker helps people track companies, job applications, interview rounds, resumes, and optional AI interview practice. The production profile supports authenticated multi-user ownership. The default profile remains a local-first H2 application for personal use and compatibility.

## Canonical Terms

- **Company**: An organization a person is interested in or has applied to. A company can exist before any application is created.
- **Job application**: A person's record of applying to one position at one company. Its status is freely adjustable and drives the board column.
- **Application status**: One of `SAVED`, `APPLIED`, `WRITTEN_TEST`, `INTERVIEWING`, `OFFER`, `REJECTED`, or `WITHDRAWN`. `SAVED` means a company or opportunity is tracked before a formal application; it is not an application submission.
- **Interview round**: A manual record attached to a job application, such as an online assessment, technical interview, or HR interview. It records the round type, date, result, and notes.
- **Resume**: An uploaded PDF and its extracted text, optionally associated with a job application. It is source material for AI interview sessions.
- **AI interview session**: A practice interview created from a resume, optionally customized with a target job application. It contains ordered questions and may produce a final report.
- **AI interview question**: One question in an AI interview session, with an optional answer, score, and feedback. Question order is immutable once created.
- **AI provider configuration**: The OpenAI-compatible endpoint, model, and credential used to generate AI content. Production stores it per user with authenticated encryption; local mode retains a local configuration file.
- **Interview history**: Persisted AI questions, answers, scores, feedback, and reports. Deleting a resume does not delete this history, because it is intended for review.

## Domain Invariants

1. A job application belongs to exactly one company.
2. Interview rounds belong to a job application and are ordered by round number.
3. A resume may exist without an application; an AI interview may reference a resume and optionally an application.
4. AI interview question numbers are ordered within a session and are not reused for a different question.
5. A completed AI interview is read-only: answers, scores, feedback, and follow-ups cannot be changed.
6. An AI interview requires at least one scored question before it can be completed.
7. Deleting a resume removes the resume record and stored file but preserves associated AI interview history.
8. AI provider credentials must never appear in API responses, logs, exports, source control, or client-side bundles.
9. Production reads and writes are scoped to the authenticated owner. Local compatibility mode may operate without an identity and must not be exposed as a public multi-user deployment.

## Ownership And Lifecycle

The production ownership chain is `User -> Resume / Company / JobApplication -> InterviewRound / AiInterviewSession -> AiInterviewQuestion`. Database owner IDs and service-level checks enforce this chain in authenticated mode. Legacy rows with a null owner remain hidden until an operator performs the reviewed ownership cutover.

The lifecycle of an AI interview is:

`ACTIVE -> COMPLETED`

An active session accepts an answer, evaluation, or follow-up under the current API rules. A completed session can be viewed but not mutated.
