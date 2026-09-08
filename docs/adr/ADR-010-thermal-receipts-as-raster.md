# ADR-010 — Thermal receipts rendered as raster images, not ESC/POS text

**Status:** Accepted · **Date:** 2026-09-08 · **Phase:** 3

## Context
Receipts must be printed in Arabic on an inexpensive 80mm ESC/POS thermal printer. Arabic requires
contextual letter shaping (a letter's glyph depends on its neighbours) and bidirectional layout
(Arabic runs right-to-left, embedded numbers run left-to-right). Cheap thermal printers implement
neither: they offer byte-per-glyph codepages, at best CP864, with no shaping engine.

## Decision
Do not use the printer's text mode for Arabic. Instead:

1. Lay out the receipt from a semantic `ReceiptDocument` (header, lines, totals, footer) using
   **Java2D/Skia**, which has correct Arabic shaping and bidi via the JVM's text layout.
2. Rasterize to a **1-bit bitmap** at the printer's exact dot width (576px @ 80mm, 384px @ 58mm).
3. Send with the ESC/POS raster command `GS v 0`, followed by feed and cut.
4. Transport via `javax.print` RAW (`DocFlavor.BYTE_ARRAY.AUTOSENSE`) to the installed Windows queue.
5. Keep the ESC/POS command set minimal: init, raster, feed, cut, drawer kick (`ESC p`). No vendor
   extensions.
6. `:printing:api` exposes `ReceiptDocument`; A4/PDF uses the same document via PDFBox.

## Alternatives considered
| Option | Rejected because |
|---|---|
| ESC/POS text mode with CP864 | Most cheap printers lack the codepage entirely; those that have it print **unshaped, disconnected letters in the wrong order**. Output is unreadable to a customer. |
| Client-side Arabic shaping into presentation forms + manual bidi reordering | Technically possible (Unicode Arabic Presentation Forms), but it is a font-and-locale minefield, breaks on ligatures and diacritics, and must be re-solved per printer codepage. The JVM already does this correctly. |
| Printing via the Windows driver as a normal document (`Graphics2D` printing) | Works, and shaping is correct — but it goes through the spooler with driver-dependent margins and scaling, is slow to start, and gives no access to the cash-drawer kick or the cutter. Kept as a fallback. |
| Generating a PDF and printing it | Extra dependency in the hot path of every sale, slower, and still no drawer/cutter control. Correct for A4, wrong for a 2-second receipt. |
| Buying a printer with native Arabic support | Narrows sourcing to specific models at higher cost, and ties the shop to a hardware choice. Raster works on essentially every ESC/POS printer. |
| Latin-transliterated receipts | Unacceptable to the customer and to the owner. |

## Consequences
**Good:** Arabic is correct by construction, because the JVM's text engine does the shaping;
identical output across printer models; full typographic control (logo, bold totals, QR code later);
the same `ReceiptDocument` renders to A4/PDF and could render on Android with the platform's own
text engine.

**Costs:** raster data is larger than text, so printing is slower — target under 3 seconds for a
20-line receipt, measured, with density reduced if needed; a font must be bundled and embedded so
output is deterministic; layout is hand-built (line wrapping, column alignment) rather than delegated
to the printer's text mode, which is roughly 6h of Phase 3.

**Test that matters:** an Arabic reader confirms a real printed receipt — connected letters, correct
word order, digits aligned and readable, totals unambiguous. No unit test substitutes for this.
