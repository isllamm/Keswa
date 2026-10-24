package com.alsoug.keswa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/**
 * One activity, and nothing in it.
 *
 * Everything the handheld shows is the same `App()` the till shows — which is the point of adding
 * the target at all: a second consumer of `commonMain` proves there were no desktop assumptions
 * hiding in it.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}
