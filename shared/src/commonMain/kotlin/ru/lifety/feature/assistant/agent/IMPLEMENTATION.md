# Native Lifety agent — implementation plan

Approved direction: one APK, native Kotlin harness, external OpenAI-compatible LLM API only.
Use Agent-Skills-for-Context-Engineering context/memory/tool/compression guidance.
No OpenCode, runtime server, OS shell, extra DB framework or independent transaction storage.

Revision: the user clarified that the agent needs its own files and execution environment.
See ENVIRONMENT_DESIGN.md. Its proposed QuickJS choice is still to be confirmed; do not
finish the old domain-query approach as the primary analytical interface. Existing kernel
and transport scaffolding can be reused. No new runtime dependency has been installed.
Shared Gradle/schema/Manifest/root-route changes remain owned by Codex 1.

- [ ] Kernel: provider-neutral messages/tool contracts; bounded model-tool loop, cancellation,
  checkpoint-before-action, interrupted-call recovery, permissions enforced outside the model.
- [ ] Workspace: bounded list/read/search/write/delete; private paths, quotas, atomic writes;
  separate immutable source/skill files from editable notes/scripts/reports.
- [ ] Runtime: verify QuickJS artifacts and actual timeout/BigInt/memory behavior; a fresh
  restricted interpreter per script; no network/OS bindings; exact Gradle patch for Codex 1.
- [ ] Data: pluggable snapshot sources over existing repositories; exact minor-unit money,
  freshness/coverage metadata; no raw bank notification text, no fictional health history.
- [ ] Memory/actions: sourced facts, proposal/approval, idempotent task updates with conflict checks;
  file-backed private session/memory checkpoints (not a duplicate transaction database).
- [ ] Context: stable instructions/tool definitions, bounded current snapshot/relevant memory;
  incremental model-generated working summary, retained raw local history and tool receipts.
- [ ] Transport: configurable HTTPS Chat Completions API + tool calling, no automatic retries of
  actions, bounded response, redacted errors, redirects disabled; test against a local HTTP server.
- [ ] Platform/UI: Compose assistant, settings and data scopes, cancel, proposal confirmation,
  memory inspection/deletion, Android atomic file + Keystore credentials, iOS private file and
  session-only key. User supplies endpoint/model/key; no live credential is available to this task.
- [ ] Tests/evals: exact calculations, tools/permissions, replay/conflicts, interruption, compaction,
  state persistence failures, API protocol/error behavior, then Android full build + host tests + iOS.

Execution: inline with TDD and one independent final review.
MVP scope: foreground/user-driven runs. Always-on/background scheduling and Health Connect sync
need Core/Android integration; no claim of background reliability or historical health analysis.
A later Responses/Anthropic transport can implement the same AgentModel interface.
