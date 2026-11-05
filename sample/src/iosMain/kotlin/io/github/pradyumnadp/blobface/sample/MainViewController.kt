package io.github.pradyumnadp.blobface.sample

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** Entry point used by iosApp/ContentView.swift. */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
