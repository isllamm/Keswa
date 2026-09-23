package com.alsoug.keswa.core.designsystem

import androidx.compose.runtime.Immutable

/**
 * Something to tell the person at the till, before it has been put into words.
 *
 * A ViewModel cannot read [Strings]: it is not a composable, it has no access to the theme, and it
 * would be choosing a language on behalf of a screen it cannot see. So it names the message and
 * the screen — which is inside the theme — resolves it.
 *
 * That is the whole of the change. Every effect used to carry a `String`, which meant every
 * snackbar in the app was written in English at the moment the decision was made, several layers
 * away from anyone who could have known what language the shop reads.
 */
@Immutable
sealed interface Message {

    /** One of the app's own words, chosen by name. */
    data class Of(val read: (Strings) -> String) : Message

    /**
     * Text that is already the shop's own and belongs to no language of ours — a SKU, a receipt
     * number, a product the shop named itself.
     */
    data class Literal(val text: String) : Message
}

fun message(read: (Strings) -> String): Message = Message.Of(read)

fun literal(text: String): Message = Message.Literal(text)

/**
 * Plain, not composable: the screen collects effects inside a `LaunchedEffect`, which is a
 * coroutine and cannot call composable functions. It reads `KeswaTheme.strings` once and passes it
 * in.
 */
fun Message.resolve(strings: Strings): String = when (this) {
    is Message.Of -> read(strings)
    is Message.Literal -> text
}
