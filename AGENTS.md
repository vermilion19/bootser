# AGENTS.md

## Purpose
Repository-level instructions for coding agents.

## Skill loading rule
- Skills are recognized only when explicitly listed in this file.
- For this repository, skill definitions are stored under `.agents/skills`.
- Adding a YAML file alone does not automatically activate a skill.

## Available skills
- anti-pattern: `.agents/skills/anti-pattern`
- api: `.agents/skills/api`
- commit: `.agents/skills/commit`
- event-flow: `.agents/skills/event-flow`
- explain: `.agents/skills/explain`
- fix: `.agents/skills/fix`
- make-md: `.agents/skills/make-md`
- perf-audit: `.agents/skills/perf-audit`
- push-after-commit: `.agents/skills/push-after-commit`
- query-plan: `.agents/skills/query-plan`
- quiz: `.agents/skills/quiz`
- resilience: `.agents/skills/resilience`
- responsive: `.agents/skills/responsive`
- review: `.agents/skills/review`
- roadmap: `.agents/skills/roadmap`
- spec-review: `.agents/skills/spec-review`
- spec-writer: `.agents/skills/spec-writer`
- test: `.agents/skills/test`
- ui-gen: `.agents/skills/ui-gen`

## How to register a skill
1. Add the skill file path under `Available skills`.
2. Provide a short name and description.
3. Use the skill by name in a prompt, or issue a task that clearly matches its description.
