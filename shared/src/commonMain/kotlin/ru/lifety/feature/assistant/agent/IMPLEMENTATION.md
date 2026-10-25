# Native Lifety agent — implementation plan

Approved direction: one APK, native Kotlin harness, external OpenAI-compatible LLM API only.
Use Agent-Skills-for-Context-Engineering context/memory/tool/compression guidance.
No OpenCode, runtime server, OS shell, extra DB framework or independent transaction storage.

Revision: the user clarified that the agent needs its own files and execution environment.
See ENVIRONMENT_DESIGN.md. User approved QuickJS and the exact shared Gradle dependency
patch. Later steering explicitly excludes UI: provide a Kotlin API and INTEGRATION.md.
No screen, root navigation, schema, model/repository contract or Manifest changes.

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
- [ ] Platform/API: headless Android/iOS factories, session lifecycle, data scopes and proposals,
  memory inspection/deletion, Android atomic file + Keystore credentials, iOS private file and
  in-memory key. UI integration belongs to the other agent. No live credential is available.
- [ ] Tests/evals: exact calculations, tools/permissions, replay/conflicts, interruption, compaction,
  state persistence failures, API protocol/error behavior, then Android full build + host tests + iOS.

Execution: inline with TDD and one independent final review.
MVP scope: foreground/user-driven runs. Always-on/background scheduling and Health Connect sync
need Core/Android integration; no claim of background reliability or historical health analysis.
A later Responses/Anthropic transport can implement the same AgentModel interface.

## Execution ledger

- Workspace/runtime: real native QuickJS host execution passed exact BigInt report,
  timeout/recovery, read-only snapshot and memory exhaustion checks.
- Kernel/context: interrupted-call recovery, proposal replay/conflict, checkpoint failure,
  anchored JSON compaction including large tool arguments, current-goal pinning verified.
- Memory: confirmation, source quote, replacement and forget cleanup verified.
- API: real localhost HTTP tool-call round trip, endpoint validation, redirect rejection.
- Lifecycle: single-session lease and close/reopen checked; factories compile for both targets.
- Latest combined command: Android full build + host tests + common metadata + iOS simulator
  source compilation passed. 59 tests, 0 failures, 1 skipped (Windows symlink privilege).
- Android QuickJS ELF LOAD alignment checked on all four ABI binaries: 16384 bytes.
- User rulings: QuickJS/shared Gradle patch approved; UI explicitly excluded; push own
  branch and provide INTEGRATION.md. No shared contract/schema/Manifest/nav edits.
- Pending: independent final review and any required fixes, final verification, commit/push.
- Live provider/Android device/Keystore runtime/iOS runtime/link not tested: no key/device.
