# Phase 3 Plan — Hardware Layer (Printers & Scanner)

> **Status: 🔨 SOFTWARE COMPLETE — hardware checks outstanding**
> Q3 answered 15 Sep: **network printers**. That makes `TcpTransport` the entire transport layer,
> with no platform-specific code anywhere — the best case in D8.
> Depends on: Phase 1. **Does not depend on Phase 2** — can run in parallel.
> Blocked by: **Q3** (which printers) for the on-hardware checks only. All software work can start now.
> Estimated: 5–8 days

## Goal

Print a real receipt — **in Arabic** — and a real hang tag, from the real app, on the real printer.
Plus a scanner that reliably turns a scan into a variant lookup.

## Why this phase runs early

Everything after it assumes printing works. A wrong assumption here — Arabic renders as boxes, the
printer needs a driver we can't reach, the label stock is wrong — invalidates UI work built on top.
Discovering that at Phase 8 with a shop waiting is expensive; discovering it now costs a week.

**This phase deliberately ships no user-facing feature.** Its output is proof plus a tested layer.

---

## Architecture — KD-005

Per ADR-018, this is an **interface in `:core` bound through Koin**, not `expect`/`actual`. ADR-018's
scope list already names `PaymentTerminal`, which is the direct precedent.

The load-bearing split:

```
commonMain (pure, no platform deps, fully unit-testable)
├── EscPos          receipt protocol  → ByteArray
├── Tspl            label protocol    → ByteArray
├── ReceiptRenderer document         → MonoBitmap
└── TcpTransport    ktor-network      ← works on desktop AND android, zero actual code

core/platform (interfaces, Koin-bound)
├── IPrinterTransport
├── IReceiptPrinter
├── ILabelPrinter
└── IBarcodeScanner

desktopMain (thin)
└── DesktopUsbTransport, DesktopBluetoothTransport   ← only if Q3 says USB/BT
```

**ADR-018's "no raw context exposure" applies.** No `javax.print`, no `android.hardware.usb`, no
serial handle crosses the interface. The boundary is `suspend fun write(bytes: ByteArray)`.

---

## Deliverables

### 1. [NEW] `core/platform/IPrinterTransport.kt`

```kotlin
interface IPrinterTransport {
    suspend fun open(): Result<Unit>
    suspend fun write(bytes: ByteArray): Result<Unit>
    suspend fun close()
}
```

`Result` per ADR-032, with failures mapped onto the existing `core.error.Error` hierarchy rather
than a bespoke one.

### 2. [NEW] `core/printing/escpos/EscPos.kt` — pure commonMain

```kotlin
object EscPos {
    fun init(): ByteArray = byteArrayOf(0x1B, 0x40)
    fun cut(): ByteArray = byteArrayOf(0x1D, 0x56, 0x42, 0x00)
    fun kickDrawer(): ByteArray = byteArrayOf(0x1B, 0x70, 0x00, 0x19, 0xFA.toByte())

    /** GS v 0 — raster bit image. [bitmap] width must be a multiple of 8. */
    fun raster(bitmap: MonoBitmap): ByteArray
}
```

### 3. [NEW] `core/printing/render/ReceiptRenderer.kt` — the Arabic solution

**The single most important piece of this phase.** Thermal firmware mangles Arabic — CP864/CP1256
confusion, no letter shaping, no ligatures, no RTL. Do not send Arabic as text.

**Changed during build.** The plan assumed Compose's graphics APIs would serve and were "common
across all CMP targets". They are not, quite: `TextMeasurer` needs a `FontFamily.Resolver`, which is
itself platform-supplied — so rendering is a platform bridge either way. `DesktopReceiptRenderer`
therefore uses **AWT** (`BufferedImage` + `TextLayout`), which shapes Arabic and resolves
bidirectional runs correctly and is far better-trodden. Android gets its own implementation on
`android.graphics.Canvas` in Phase 6, behind the same `IReceiptRenderer`.

The document model and the raster encoding either side of it stay pure and testable; only glyph
rasterisation is platform-specific.

The original sketch, for reference:

```kotlin
fun renderReceipt(receipt: Receipt, widthDots: Int = 576): MonoBitmap {
    val bitmap = ImageBitmap(widthDots, measuredHeight)
    Canvas(bitmap).apply { /* text via Compose text layout, logo, QR */ }
    return bitmap.toPixelMap().dither()
}
```

- 80 mm paper at 203 dpi = **576 dots**. 58 mm = 384. Make it a parameter, not a constant.
- Compose's text layout does Arabic shaping and bidi correctly, for free — this is the whole reason
  the approach works.
- Dithering: **Floyd–Steinberg for the logo, hard threshold for text.** Dithered text on a thermal
  head turns to mush.
- **The QR of the sale ID goes on every receipt** (architecture plan D5) so a return is one scan.
  `qrose` is already proven in Cashi's catalog and works in common code.

### 4. [NEW] `core/printing/tspl/Tspl.kt` — hang tags

Label size, gap, density and speed are all printer-specific and belong in configuration, not
constants. Barcode generation via `zxing-core`, already in Cashi's catalog.

### 5. [NEW] `core/printing/transport/TcpTransport.kt` — commonMain

```kotlin
class TcpTransport(
    private val host: String,
    private val port: Int = 9100,
    private val dispatchers: DispatcherProvider,
) : IPrinterTransport
```

Written once with `ktor-network`; runs unchanged on desktop and Android. **If Q3 lands on network
printers, this is the entire transport layer** — no platform code at all.

Needs a real connect/write timeout. A printer that is powered off but still holds its IP will
otherwise hang the sell screen.

### 6. [NEW] `core/platform/IBarcodeScanner.kt` + desktop HID capture

USB and Bluetooth scanners are **HID keyboards** — they type the code and press Enter. No driver,
no SDK.

```kotlin
interface IBarcodeScanner {
    val scans: Flow<String>
}
```

Desktop implementation captures key events via `onPreviewKeyEvent` and distinguishes scanner from
human by **inter-keystroke timing** — a scanner emits characters far faster than typing:

- Characters arriving < 30 ms apart accumulate into a buffer.
- `Enter` terminates a scan; a gap > 150 ms discards the partial buffer.
- Both thresholds are configurable — cheap scanners vary, and this is the first thing to tune on
  real hardware.

**A scan must never land in whatever text field happens to have focus.** Global capture, routed to
the scanner flow, consumed explicitly by the screen that wants it.

### 7. [NEW] `features/settings` — printer configuration

**Settings live in the database, as schema v2.** That made `app_setting` the project's **first real
migration** — which in turn closed the gap left at the end of Phase 1, where the migration harness
was built but never exercised on an actual version bump. `SchemaFixture` now builds a database at
any previously-exported schema version from Room's own JSON, so every future migration is testable
against the schema a real shop is actually carrying.

The minimum to make the above usable: printer IP/port, paper width, label dimensions, a **Test
Print** button, and scanner timing thresholds. Follows the standard feature layout and MVI rules —
it is also a gentler second exercise of the Phase 2 pattern.

---

## Design rule this phase establishes

**The sale is committed before anything is printed. Printing is never a precondition for the sale.**

A jammed printer, an empty paper roll or an unplugged network cable must not lose or block a
transaction. Print failure raises a `UiEffect`, queues the document for reprint, and the sale stands.
Reprint is available from the sale record.

This is easy to build in now and structurally awkward to retrofit, because it dictates transaction
boundaries in Phase 5's sell flow.

---

## Best-Practice Notes

**Deliberately NOT here:**

- **No USB or Bluetooth transport** unless Q3 forces it. Each is real platform work (KD-005), and
  network printers make both unnecessary. Bluetooth arrives in Phase 7 with handheld stock counting,
  where it is genuinely required.
- **No camera scanning.** Cashi has `qrscanner` with CameraX, but a dedicated HID scanner is faster
  and more reliable at a till. Camera scanning belongs to the Phase 7 handheld case.
- **No receipt template designer.** One hardcoded layout. Templating is a Phase 8+ feature and will
  be far better informed once a real shop has used the fixed one.

**Flagged as a genuine unknown:** the exact Arabic rendering quality on the target printer head.
The bitmap approach is sound and well-established, but 203 dpi is coarse for Arabic diacritics at
small point sizes. Budget a day for font and size tuning on real hardware, and **test with real
product names**, not lorem ipsum — Arabic shaping bugs hide until you use the actual characters.

---

## Verification Plan

### Software — no hardware needed

| # | Check | Method |
|---|---|---|
| 1 | ESC/POS byte output | **Golden-byte tests** — assert exact `ByteArray` against recorded fixtures |
| 2 | TSPL byte output | Same |
| 3 | Raster encoding | Known bitmap → known GS v 0 payload, including the width-padding edge case |
| 4 | Renderer | Snapshot tests on the `MonoBitmap`, Arabic and English |
| 5 | Scanner heuristic | Synthetic key streams: fast scan, slow typing, interleaved, partial-then-abandoned |
| 6 | Transport failure | Fake transport returning failures — assert `Result.failure`, no exception escapes |

### Hardware — blocked on Q3

| # | Check | Pass criterion |
|---|---|---|
| 7 | Receipt prints | Correct width, nothing clipped |
| 8 | **Arabic renders correctly** | Letters joined, correct RTL order, legible at size — verified by a native reader, not by me |
| 9 | Cash drawer | Opens on the kick command |
| 10 | Paper cut | Cuts in the right place |
| 11 | Hang tag | Barcode **scans back** on the actual scanner — the round trip is the test |
| 12 | Label durability | Thermal-transfer tag survives sunlight and fabric abrasion. Direct thermal will fail this — that's the point of running it |
| 13 | Scanner round trip | Scan a printed tag → correct variant resolves |
| 14 | Printer offline | Unplug mid-print → error surfaces, app stays responsive, nothing lost |

Checks 11 and 14 are the ones that catch real problems. A barcode that prints but doesn't scan is
the classic failure, and it is invisible until you try.

## Definition of Done

- [ ] Golden-byte tests cover ESC/POS and TSPL
- [ ] Receipt renders correctly in Arabic and English (snapshot + real print)
- [ ] `TcpTransport` prints to a real networked printer
- [ ] A printed hang tag scans back to the correct variant
- [ ] Cash drawer opens; paper cuts
- [ ] Printer failure degrades gracefully — no lost work, no frozen UI
- [ ] Settings screen configures printer and scanner without a rebuild
- [ ] Zero platform-specific code outside `desktopMain`
