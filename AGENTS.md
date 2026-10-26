# Lifety: parallel work contract

Read `docs/CONTRACTS.md` and `README.md` before editing. This checkout integrates Core & Planner P0, Finance/Notifications, Dashboard,
manual Health and opt-in AI (shared SQLite repositories, navigation and DI).
Keep feature work within the current user-authorized scope.

- Codex 1 owns core, database, tasks, calendar, root navigation, all Gradle files,
  Android entry point, resources and Manifest, documentation and shared tests.
- Codex 2 owns finance and Android notifications.
- Codex 3 owns assistant, dashboard, health and AI integrations.

Keep public models, repository interfaces, screen entry signatures and route IDs
stable. Request shared changes through Codex 1. Feature code uses repository
interfaces; SQLDelight and SQL stay in database. Do not add another database
framework, server, cloud database, registration or vector search.

Use separate branches/worktrees for parallel agents. Do not commit generated
files, `local.properties`, credentials or build output. Before handing off, run
the Android build and shared host tests specified in the README. Only claim
checks that actually ran. Document missing device/iOS checks.

The current working branch is `main` at `/Users/rugilf/Projects/lifety-core`.
At the owner's request, the pre-iOS main (`f8c942e`) is preserved in
`HSE-hack-2026`; old feature/codex branches have been removed. Their other
worktree directories remain available with detached HEADs. Follow the user's
current instructions when choosing a branch or publishing further changes.
Public core models/repository signatures and all existing `XxxScreen(modifier)`
entry points stay unchanged. Other agents consume `LocalRepositories`,
`LocalPlannerNavigation` and optionally `LocalRootNavigator`; do not replace the
composition root or edit another agent's feature implementation.

For all UI changes read `docs/DESIGN_SYSTEM.md`. Use the root LifetyTheme,
shared spacing/shapes and `LifetyMascot`; do not copy palettes or invent mock data
from the reference. Primary navigation has five sections; calendar lives in Plan.
