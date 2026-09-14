# KD-003 — Dispatchers are injected, including in ViewModels

## Status
Accepted — 14 Sep 2026

## Context
`DispatcherProvider` already exists in kmp_cashimobile, but CODE_GUIDELINES' ViewModel template shows `viewModelScope.launch(Dispatchers.IO)` hardcoded. That template is defect T2 in the cashi_pax request-money review: hardcoded dispatchers block virtual time and produced a flaky test 'fixed' with a runBlocking workaround.

## Decision
Inject `DispatcherProvider` everywhere, ViewModels included. `TestDispatcherProvider` ships in Phase 0 — kmp_cashimobile references the type in KDoc but never created it.

## Consequences
Tests control virtual time. The correct pattern is the path of least resistance from the first test.

Full reasoning: `ai-plans/keswa-conventions-and-deviations.md` § KD-003.
