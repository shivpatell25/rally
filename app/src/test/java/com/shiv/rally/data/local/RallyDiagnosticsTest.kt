package com.shiv.rally.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RallyDiagnosticsTest {
    @Test
    fun `support report sanitizer removes provider secrets`() {
        val raw = "https://provider.example/live/abc token=secret Bearer private 00:1A:79:AA:BB:CC"
        val sanitized = sanitizeDiagnosticText(raw)

        assertFalse(sanitized.contains("provider.example"))
        assertFalse(sanitized.contains("secret"))
        assertFalse(sanitized.contains("private"))
        assertFalse(sanitized.contains("00:1A:79"))
        assertTrue(sanitized.contains("[redacted-url]"))
        assertTrue(sanitized.contains("[redacted-mac]"))
    }
}
