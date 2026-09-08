package keswa.core.ui.mvi

/** Marker for a screen's complete, immutable render state. See docs/presentation-architecture.md. */
interface MviState

/** Marker for something that happened: a user action, or a result fed back from a use case. */
interface MviIntent

/** Marker for a one-shot side effect (print, navigate, focus) — never state, never replayed. */
interface MviEffect
