# Phase 3 — Hardware & Speed

> *"Scan, print, done."*

**Effort:** ~40h · **Calendar:** ~4 weeks @10h/wk

---

## Goal
Make the till fast and physical: barcode scanning, printed labels, thermal receipts in correct Arabic,
and a build the shop can update itself. This phase is deliberately late because it has the highest
hardware variance and everything before it works without it.

## In scope

**Barcode**
- `ScanBuffer` state machine in commonMain (inter-key timing, terminator, min length) with unit tests
- Window-root key interception so scanning works regardless of focus
- **Arabic keyboard-layout handling**: read physical key codes, not typed characters (see architecture §7.4)
- Scan-to-add on POS, scan-to-find in catalogue, scan during stock count and goods receipt
- Barcode generation for products without one (CODE128, internal prefix)
- Label printing: variant labels with Arabic name, size/colour, price, barcode — to a label printer
  or an A4 sheet of labels

**Printing**
- `:printing:escpos`: layout → 1-bit raster (Java2D, correct Arabic shaping/bidi) → `GS v 0`
- `javax.print` RAW transport to a Windows print queue; printer selection and test print in Settings
- Receipt template from settings: shop name, address, phone, footer message, logo
- Cash drawer kick (ESC/POS `ESC p`) on cash sale completion
- Reprint last receipt; reprint any sale from its document

**Speed & polish**
- Report/query performance pass with real data volume; add missing indexes
- POS startup time and search latency budget (<150ms for a search over the full catalogue)
- `SALES_BY_HOUR`, `DEAD_STOCK` reports
- Manual "check for updates" → download MSI → verify SHA-256 → launch installer
- Crash/error log surfaced in Settings with a "copy diagnostics" button

## Explicitly out of scope
Automatic silent updates · scale/weighing integration · customer-facing display · card terminal
integration (card is still recorded manually) · mobile scanning · any network code beyond the update
check

## Deliverables
1. Cashier completing sales by scanning only
2. Thermal receipt with correctly shaped, correctly ordered Arabic
3. Labels printed for the shop's unlabelled stock
4. In-app update path exercised once end to end

## Exit criteria
- [ ] 50 consecutive scans registered correctly, including with the Windows input language set to Arabic
- [ ] A scan while a text field has focus does not corrupt that field
- [ ] Thermal receipt reviewed by an Arabic reader: connected letters, correct word order, correct
      digit alignment, totals legible
- [ ] Cash drawer opens on cash sale, does not open on card sale
- [ ] Catalogue search under 150ms with the full catalogue loaded
- [ ] Update from version N to N+1 preserves the database and all settings

## Migration impact
Minimal. Possibly a `label_template` setting and an index or two from the performance pass.

## Risks
| Risk | Mitigation |
|---|---|
| **Arabic on thermal printers** — the biggest technical unknown in the project | Raster rendering (ADR-010) sidesteps codepages entirely. Buy the printer and test in **week 1** of this phase, not week 4 |
| Printer model behaves differently from spec | Keep the ESC/POS command set minimal: init, raster image, feed, cut, drawer kick. Avoid vendor extensions |
| Scanner emits an unexpected suffix or prefix | Make terminator/prefix/min-length configurable in Settings, not hardcoded |
| Raster receipts print slowly | Reduce to 1-bit early, cap width at the printer's dot count, and measure. If >3s, drop to 203dpi single-density |
| Windows print queue permissions across user accounts | Install the printer for all users; test under a non-admin Windows account |

## Notes
- **Buy the hardware before this phase starts.** A blocked week waiting for a printer is a quarter of
  the phase. **ASSUMPTION:** an 80mm USB ESC/POS printer and a USB HID scanner; confirm the models.
