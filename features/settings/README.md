# `:features:settings`

Where the shop's hardware is configured, and where a printer is proven before anyone relies on it.

## What it does

| Section | Why it is configurable |
|---|---|
| **Receipt printer** — host, port, paper width | Every shop's printer has a different address; 80 mm and 58 mm rolls are both common |
| **Label printer** — host, port, label size | Label media varies by supplier, and the size is physical |
| **Scanner** — max gap between keystrokes | Cheap scanners differ, and this is the first thing to tune on real hardware |

Both **Test print** and **Test label** save first, so the button tests what is on screen rather than
what was last stored — otherwise someone changes an address, tests, and gets the old printer.

## Decisions worth knowing

**A dead printer is an outcome, not an error.** `PrintResult` is `Printed` / `NotConfigured` /
`Unreachable(detail)`, all returned inside a successful `Result`. A switched-off printer is an
ordinary Tuesday, not a fault in the program, and the screen can say something specific about it.

**Settings are key/value rows, not typed columns.** They are read once at startup, written rarely,
and every phase adds a few. Typed columns would mean a migration per setting.

**The transport is built per call from the current address.** `TransportFactory` exists so host and
port come from settings at print time, and so the print path is testable with a recording transport
and no hardware.

## Testing without a printer

The whole chain — render, encode, send — is covered by fakes, and `TcpTransport` itself is proven in
`:core` against a real `ServerSocket`. What cannot be faked is whether Arabic is legible at 203 dpi
on the actual head, and whether the label stock survives a shop window. Those wait for hardware.

> Use **thermal transfer with a ribbon** for hang tags, never direct thermal. Direct thermal fades
> in sunlight and dies against fabric, so the tag is unreadable long before the garment sells.
