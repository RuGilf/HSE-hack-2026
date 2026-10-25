# Native agent chat — implementation plan

User-authorized scope: replace AssistantScreen with the real agent, reuse the Lifety Material theme. Keep route, Gradle, Manifest, models and repository interfaces unchanged. Prioritize a usable chat and explicit approvals; developer playground is not the UI reference.

1. Platform rememberAgentRuntime factories; a feature-local chat controller owns connection/session lifecycle, credentials, scopes and runner collection. Test no network before Send and cancel/close with a local HTTP fixture.
2. Replace AssistantScreen: lazy conversation, composer/Stop, settings sheet with API credentials and explicit data scopes, human-readable pending actions, confirmed memory and saved reports. No synthetic sources and no tool JSON in normal chat.
3. Verify Android build/host tests and common/iOS source compile, update integration instructions, sync latest main and push only codex/native-agent-harness. Physical device/runtime checks reported separately.

Review focus: secrets never in saved Compose state; no request while opening settings; exact changes visible before approval; scopes revocation waits for cancellation; leaving the screen closes the session.
