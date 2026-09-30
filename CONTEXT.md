# Offer Tracker Domain Context

## Purpose

Offer Tracker helps a person track companies, job applications, interview rounds, resumes, and optional AI interview practice. The system is currently a single-user application. Multi-user ownership and authentication are planned production work, not current capabilities.

## Canonical Terms

- **Company**: An organization a person is interested in or has applied to. A company can exist before any application is created.
- **Job application**: A person's record of applying to one position at one company. Its status is freely adjustable and drives the board column.
- **Application status**: One of `SAVED`, `APPLIED`, `WRITTEN_TEST`, `INTERVIEWING`, `OFFER`, `REJECTED`, or `WITHDRAWN`. `SAVED` means a company or opportunity is tracked before a formal application; it is not an application submission.
- **Interview round**: A manual record attached to a job application, such as an online assessment, technical interview, or HR interview. It records the round type, date, result, and notes.
- **Resume**: An uploaded PDF and its extracted text, optionally associated with a job application. It is source material for AI interview sessions.
- **AI interview session**: A practice interview created from a resume, optionally customized with a target job application. It contains ordered questions and may produce a final report.
- **AI interview question**: One question in an AI interview session, with an optional answer, score, and feedback. Question order is immutable once created.
- **AI provider configuration**: The OpenAI-compatible endpoint, model, and credential used to generate AI content. It is currently stored locally; per-user encrypted storage is a production target.
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
9. The current application has no user identity; therefore all data is currently local application data. A future multi-user release must add ownership checks to every read and write path before public deployment.

## Ownership And Lifecycle

The intended production ownership chain is `User -> Resume / Company / JobApplication -> InterviewRound / AiInterviewSession -> AiInterviewQuestion`. Until authentication is implemented, this chain is conceptual rather than enforced by the current schema.

The lifecycle of an AI interview is:

`ACTIVE -> COMPLETED`

An active session accepts an answer, evaluation, or follow-up under the current API rules. A completed session can be viewed but not mutated.

