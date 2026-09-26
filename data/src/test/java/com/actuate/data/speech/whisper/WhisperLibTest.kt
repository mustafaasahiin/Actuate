package com.actuate.data.speech.whisper

import org.junit.Assert.assertNotNull
import org.junit.Test

class WhisperLibTest {

    @Test
    fun whisperLibObjectLoadsSafelyWithoutException() {
        assertNotNull(WhisperLib)
    }
}
