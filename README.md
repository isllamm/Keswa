# Keswa

**Keswa** is a comprehensive Clothing Retail Point of Sale (POS) system built with **Kotlin Multiplatform (KMP)**. It provides robust capabilities for both retail (B2C) and wholesale (B2B) modes, ensuring that cashiers and administrators can seamlessly track inventory, sales, and analytics without requiring a constant internet connection.

## Key Features

- **Offline-First by Design:** The app operates independently from any server, leveraging a local SQL database (via Room KMP) as its source of truth.
- **Multi-Platform Support:** Runs on JVM Desktop for primary till workstations and Android for handheld inventory/counting terminals.
- **Append-Only Ledger:** Built with an append-only inventory ledger where stock levels merge smoothly over time, avoiding race conditions and complex merge conflicts.
- **Central Sync Server & Web Back Office:** Embedded lightweight sync engine with real-time log-shipping, multi-till synchronization, device enrolment, and a responsive web dashboard for owners.
- **Precise Financial Logic:** Financial amounts use a custom `Money` type represented cleanly in minor units (Piastres), meaning no `.toDouble()` rounding flaws.
- **Clean Architecture & MVI:** Implements rigorous Clean Architecture (`presentation`, `domain`, `data`, `di`) and Unidirectional Data Flow using the MVI architectural pattern.
- **Hardware Integration:** Employs raw TCP socket networking to seamlessly interact directly with POS ESC/POS receipt printers, TSPL hang-tag printers, and barcode scanners.

## Tech Stack

- **UI Framework:** Compose Multiplatform (Desktop + Android)
- **Database:** Room KMP & SQLite Bundled (with immutable migrations)
- **Dependency Injection:** Koin
- **Networking/Sync:** Ktor Client & Server
- **Architecture:** Unidirectional Data Flow (MVI) & Clean Architecture

## Documentation

Full architectural decisions, codebase constraints, and AI phase plans can be found within the repository:
- `docs/CODE_GUIDELINES.md`: The guiding rules on code shape, MVI styling, testing, and error handling.
- `docs/BRANCHING.md`: Branching policy (`dev`, `prod`, `feature/*`), releases, and current state.
- `docs/adr/`: Architectural Decision Records (KD-001 through KD-010).
- `server/README.md`: Sync server and Web Back Office reporting dashboard documentation.

## Getting Started

### Building the POS Client
To compile the JVM Desktop version:
```bash
./gradlew :composeApp:compileKotlinDesktop
```

To build the Android Debug APK:
```bash
./gradlew :composeApp:assembleDebug
```

### Running the Sync Server & Back Office
```bash
KESWA_ADMIN_TOKEN="your-secure-token" ./gradlew :server:run
```
Then open `http://localhost:8080/` in your browser to access the Web Back Office dashboard.

### Running Tests & Quality Gates
```bash
./gradlew allTests
./scripts/check-gates.sh
```

Ensure you adhere strictly to guidelines within `docs/CODE_GUIDELINES.md` prior to filing PRs.
