# SDD at QuiStock

SDD (*Spec-Driven Development*) in this repository means describing expected behavior before implementing it and keeping requirements, tests, and code consistent. A spec is short, reviewable, and tied to a real change; it does not replace the Jira issue or the technical README.

## When to create a spec

Create one for a feature, an observable behavior change, an integration, or a bug fix with meaningful rules. Use `docs/sdd/specs/QUIS-123-short-name.md` when there is a Jira issue; otherwise, use a descriptive name. A spec may cover one small PR or several explicitly identified PRs.

Mechanical formatting changes, renames without behavior changes, and standalone documentation changes may skip a spec. Explain the omission in the PR.

## Process

1. Copy [TEMPLATE.md](specs/TEMPLATE.md) and describe the problem, scope, acceptance criteria, and open questions. Separate known facts from assumptions.
2. Resolve open product or contract decisions before implementing work that depends on them.
3. For each criterion, choose a unit test, an instrumented test, or a justified manual check. Write the behavioral test first when practical.
4. Implement and refactor according to the [main README](../../README.md). Keep the spec and tests current if behavior changes.
5. In the PR, link the spec and summarize satisfied criteria, test evidence, and any remaining decisions.

Long-lived technical decisions that affect multiple features may be recorded in `docs/sdd/decisions/` with context, decision, and consequences. Create that directory only when the first such decision arises. Known project state and confidence limits are in [context.md](context.md).
