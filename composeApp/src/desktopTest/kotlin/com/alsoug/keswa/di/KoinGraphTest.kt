package com.alsoug.keswa.di

import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.core.platform.IPlatformProvider
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import org.koin.core.context.stopKoin

class KoinGraphTest {

    @AfterTest
    fun tearDown() = stopKoin()

    @Test
    fun `composition root resolves every registered dependency`() {
        // Given the real composition root
        val koin = initKoin().koin

        // When the graph is resolved
        val dispatchers = koin.get<DispatcherProvider>()
        val platform = koin.get<IPlatformProvider>()

        // Then nothing is missing
        assertNotNull(dispatchers)
        assertNotNull(platform)
    }
}
