# KD-005 — Printers and scanners are `:core` interfaces, not expect/actual

## Status
Accepted — 14 Sep 2026

## Context
ADR-018's dominant mechanism is an interface in `:core` bound through Koin (~20 of them) with expect/actual reserved for what cannot be injected (9). Its scope list already names `PaymentTerminal`.

## Decision
`IPrinterTransport`, `IReceiptPrinter`, `ILabelPrinter`, `IBarcodeScanner` live in `core/platform`, Koin-bound. Protocol generation (`EscPos`, `Tspl`, the bitmap renderer) is pure `commonMain` and covered by golden-byte tests. `TcpTransport` is written once with `ktor-network`.

## Consequences
No `javax.print` or `android.hardware.usb` crosses the interface, per ADR-018's 'no raw context exposure'. With network printers the entire print stack is shared code.

Full reasoning: `ai-plans/keswa-conventions-and-deviations.md` § KD-005.
