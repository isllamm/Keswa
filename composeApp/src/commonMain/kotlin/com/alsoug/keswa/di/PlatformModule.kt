package com.alsoug.keswa.di

import org.koin.core.module.Module

/**
 * Platform implementations of the `:core` interfaces.
 *
 * `expect`/`actual` rather than a same-named declaration per source set, because [initKoin] lives
 * in `commonMain` and has to reference this by name. ADR-018's preferred mechanism — an interface
 * bound through Koin — is what this module *contains*; the module reference itself is the one thing
 * that cannot be injected, which is exactly when `expect`/`actual` is warranted.
 */
expect val platformModule: Module

/**
 * Qualifier for the application-lifetime [kotlinx.coroutines.CoroutineScope].
 *
 * In `commonMain` because both platforms bind one and both entry points resolve it — it moved here
 * in Phase 6 when Android became the second consumer.
 */
const val APPLICATION_SCOPE = "applicationScope"

