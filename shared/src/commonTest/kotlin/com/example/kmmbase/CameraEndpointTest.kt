package com.example.kmmbase

import com.example.kmmbase.data.remote.shadow.normalizeShadowOrigin
import kotlin.test.*

class CameraEndpointTest {
    @Test fun localCameraUsesTheSameBackendOrigin() {
        assertEquals("http://localhost:8001", normalizeShadowOrigin(" http://localhost:8001/ "))
        assertEquals("http://127.0.0.1:8001", normalizeShadowOrigin("http://127.0.0.1:8001"))
    }
    @Test fun cameraServerRejectsInsecureRemoteHostsAndHiddenPaths() {
        for (url in listOf("http://example.com", "http://192.168.1.2:8001", "https://user:pass@example.com", "https://example.com/api", "https://example.com?code=private")) {
            assertFailsWith<IllegalArgumentException>(url) { normalizeShadowOrigin(url) }
        }
    }
}
