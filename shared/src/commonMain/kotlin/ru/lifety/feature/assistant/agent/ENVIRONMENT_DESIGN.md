# Lifety: local agent workspace

Status: implemented direction after the user's workspace clarification, 2026-10-25.
Approved constraints: one APK, no Termux, Kotlin harness, configurable
OpenAI-compatible LLM API. User subsequently approved the QuickJS dependency and
instructed this branch to provide only the agent/API, leaving UI to another agent.
Integration contract and verified limits are described in INTEGRATION.md.

## Intent

Give the personal agent a persistent environment it can inspect and work in,
like a coding agent's project. The agent discovers data, writes and runs small
analysis programs, inspects errors, and saves useful results. New analytical
questions should not require a new Kotlin tool for each question.

The smallest demonstrable scenario: given permission to use expenses, the agent
lists its workspace, reads the expense schema and relevant records, writes a
script, executes it locally, corrects it if needed, and saves a report containing
source record IDs and the snapshot time. It can retrieve that report and
confirmed personal memory in a later session.

## Approaches

1. **Kotlin harness + embedded QuickJS (recommended).** Generic file operations
   and JavaScript execution work inside the APK. QuickJS-kt 1.0.15 publishes
   Android, JVM and Kotlin/Native targets and exposes memory, stack and evaluation
   timeout limits. This does not provide Python, Node, npm or an operating-system
   shell. It requires a shared Gradle dependency change through Codex 1.
2. **Kotlin harness + Android JavaScriptSandbox.** Offers an out-of-process
   engine. Availability depends on Android API 26+ and the WebView provider;
   the app currently supports API 24. It also needs a dependency and a separate
   iOS implementation. An option if process isolation becomes the priority.
3. **Embedded Python.** Consider only if Python libraries are a product
   requirement. It adds Android build integration, binary/package maintenance
   and a separate isolation problem. Do not promise pip or pandas without a
   tested packaging and execution design.

QuickJS is an interpreter boundary in the app process, not an OS security
boundary. No claim of protection against a native engine vulnerability.

## Workspace and ownership

All paths below are relative to a private app directory. The model cannot choose
the root. Provider credentials, checkpoints and action receipts live outside it.

```text
workspace/
  INDEX.md                   generated discovery index, coverage and timestamps
  schemas/                   generated data descriptions and script API
  data/<source>/             permission-scoped, read-only JSON/JSONL snapshots
  memory/profile.json        confirmed facts with source, date and validity
  notes/                     agent hypotheses/progress, explicitly unconfirmed
  skills/<name>/SKILL.md      packaged, reviewed instructions loaded on demand
  scripts/                   agent-written analysis programs
  scratch/                   temporary intermediate data
  reports/                   durable human-readable outputs
```

The application owns `INDEX.md`, `schemas/`, `data/`, `skills/` and confirmed
memory. The agent can freely edit `scripts/`, `scratch/`, `reports/` and its
unconfirmed notes. The API lets the UI integrator expose inspection/deletion.
Writing a note must never silently change authoritative user preferences.

Files are snapshots and working artifacts, not another transaction database.
The existing repositories remain authoritative. A source adapter exports
records, IDs, units, currency, last refresh, coverage and permission state. Health
currently has only the latest snapshot; no adapter invents historical records.
Future Health Connect history requires a real Core source adapter and grants.
Bank notification text is never exported. Amounts are decimal strings in minor
units; JavaScript calculations use BigInt, with separate totals per currency.

## Generic tool surface

- `files_list(path, cursor)`: bounded directory listing with sizes.
- `files_read(path, offset, limit)`: bounded text slices with continuation.
- `files_search(path, query, cursor)`: literal text search; bounded matches.
- `files_write(path, content, expectedRevision)`: atomic, conflict-checked write.
- `files_delete(path, expectedRevision)`: delete one writable artifact.
- `script_run(path)`: execute a saved JavaScript source file and return the run
  status plus bounded stdout/stderr and paths to larger output files.
- `source_refresh(source)`: regenerate an allowed source snapshot through its
  adapter; return the updated manifest, never a pretend successful sync.
- `action_propose(kind, arguments)`: request an app change or confirmed memory
  update. Runtime schemas and UI approval govern the available action kinds.

Script bindings expose the same bounded workspace operations, not Java
reflection, arbitrary host objects, shell commands, networking or credentials.
An initial implementation may use asynchronous `readText`/`writeText` bindings
and `console.log`. No network or module download binding is installed. Only
source text runs; never load model-supplied native code or QuickJS bytecode.

Each tool checks permissions and path ownership itself. Reject absolute paths,
traversal, symlinks and paths resolving outside the root. Start with a 20 MiB
workspace quota, 1 MiB per text file and 64 KiB per tool read. Paginate source
exports rather than silently dropping records. Quota or storage failure is a
visible error. Scratch data has an explicit retention policy; durable memory
and user reports are not silently removed to recover space.

## Execution and context

Kotlin owns model requests, tool dispatch, validation, checkpoints, cancellation
and limits. Start each script in a fresh QuickJS runtime with a proposed 32 MiB
heap, 256 KiB stack and 2 seconds of JavaScript execution; also enforce an outer
wall-clock timeout for asynchronous bindings. Output, file and tool-operation
counts are bounded. A cancelled/failed script never automatically re-runs.
Its successfully written scratch artifacts can remain, but real app data cannot
change through file operations. Expensive analyses can be split into scripts.

The initial model context contains rules, tool signatures, a small workspace
index, a bounded confirmed profile and recent conversation. Detailed source
data, skills, history and reports are retrieved on demand. Persist raw run/tool
logs outside the mutable workspace; summaries retain exact file references,
record IDs, decisions and open questions. Persisted file content is data, never
an elevation of instruction priority.

Revoking a data domain invalidates that domain's exports, run artifacts, derived
notes/reports and active chat context. Until derivation tracking is reliable,
conservatively clear the entire agent-generated workspace and conversation on
scope changes, preserving only explicitly confirmed, still-allowed profile
facts. Explain this consequence in the scope UI before the user applies it.
Data already sent to a provider cannot be recalled by a local permission change.

Personal memory stores source and observation time, distinguishes a statement
from an inference, supports correction/removal and stays separate from working
summaries. Long-term memory updates use proposals the user can review. A user
forget action clears active context and related local derived artifacts to avoid
quiet reintroduction from the same local trace.

## Application actions

File writes alone cannot modify tasks, expenses, permissions or confirmed memory.
App integrations expose typed action schemas over existing repository methods.
The model creates a proposed change; the user sees the exact operation before
commit. Store an action receipt and use stable IDs/conflict checks for recovery
and repeated approvals. Read-only analytical flexibility comes from scripts;
adding a new writable application capability requires a deliberate adapter.

## Integration and verification

Implementation remains in the assistant feature and platform adapters in its
own branch/worktree. AssistantScreen and root navigation stay untouched. The
integrator supplies Repositories from DI; null means unavailable, never demo data.

User approved applying the exact QuickJS dependency/host-test variant patch.
It is the only shared Gradle change; Manifest, schema, contracts and navigation
are untouched. Direct local script execution needs no service registration.

Before choosing the runtime permanently, verify its published artifacts resolve
with this project's Kotlin/AGP stack, its Android native binaries have suitable
ABI/page-size support, BigInt is exact, and a busy loop is actually interrupted.
The first device proof is read -> write script -> run -> report -> reopen app.
Also test path escape/symlink rejection, quotas, oversized output, permission
revocation, memory deletion, cancellation/recovery, and proposal replay.
Run Android build/host tests and iOS source compilation. Do not claim iOS
runtime, Android device behavior or a live provider request from host tests.

## Sources

- https://github.com/dokar3/quickjs-kt/blob/v1.0.15/README.md
- https://github.com/dokar3/quickjs-kt/blob/v1.0.15/quickjs/src/commonMain/kotlin/com/dokar/quickjs/QuickJs.kt
- https://github.com/dokar3/quickjs-kt/blob/v1.0.15/quickjs/build.gradle.kts
- https://developer.android.com/develop/ui/views/layout/webapps/jsengine
- Agent-Skills-for-Context-Engineering: filesystem-context, memory-systems,
  tool-design, context-fundamentals and context-compression.
