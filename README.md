# Keswa

**Keswa** is a comprehensive Clothing Retail Point of Sale (POS) system built with **Kotlin Multiplatform (KMP)**. It provides robust capabilities for both retail (B2C) and wholesale (B2B) modes, ensuring that cashiers and administrators can seamlessly track inventory, sales, and analytics without requiring a constant internet connection.

## Key Features

- **Offline-First by Design:** The app operates independently from any server, leveraging a local SQL database (via Room KMP) as its source of truth.
- **Multi-Platform Support:** Initially targeting JVM Desktop for primary till workstations, with plans to support Android for handheld terminals.
- **Append-Only Ledger:** Built with an append-only inventory ledger where stock levels merge smoothly over time, avoiding race conditions and complex merge conflicts.
- **Precise Financial Logic:** Financial amounts use a custom `Money` type represented cleanly in minor units (Piastres), meaning no `.toDouble()` rounding flaws.
- **Clean Architecture & MVI:** Implements rigorous Clean Architecture (`presentation`, `domain`, `data`, `di`) and Unidirectional Data Flow using the MVI architectural pattern.
- **Hardware Integration:** Employs Ktor networking to seamlessly interact directly with POS printers and scanners without complex platform bindings.

## Tech Stack

- **UI Framework:** Compose Multiplatform
- **Database:** Room KMP & SQlite Bundled
- **Dependency Injection:** Koin
- **Networking/Interop:** Ktor

## Documentation

Full architectural decisions, codebase constraints, and AI phase plans can be found within the repository:
- `docs/CODE_GUIDELINES.md`: The guiding rules on code shape, MVI styling, testing, and error handling.
- `ai-plans/keswa-architecture-plan.md`: The macro-level design architecture, phases, and design considerations.
- `ai-plans/keswa-conventions-and-deviations.md`: Keswa's specific rules deviating from its reference implementations (e.g. `Money` representation and Real Database migrations).

## Getting Started

### Building the Project
To compile the JVM Desktop version:
```bash
./gradlew :composeApp:compileKotlinDesktop
```

To run tests:
```bash
./gradlew allTests
./scripts/check-gates.sh
```

Ensure you adhere strictly to guidelines within `docs/CODE_GUIDELINES.md` prior to filing PRs.
