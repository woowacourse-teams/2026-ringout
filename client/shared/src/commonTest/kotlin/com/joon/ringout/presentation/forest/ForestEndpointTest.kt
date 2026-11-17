package com.joon.ringout.presentation.forest

import kotlin.test.*

class ForestEndpointTest {
    @Test fun `설정 주소에 앱 진입점을 붙이고 같은 문서만 허용한다`() {
        val endpoint = assertNotNull(ForestEndpoint.parse("https://forest.example.com/forest/"))
        assertEquals("https://forest.example.com/forest/?view=app", endpoint.url)
        assertEquals("https://forest.example.com", endpoint.origin)
        assertTrue(endpoint.allowsDocument(endpoint.url))
        listOf("https://forest.example.com/other?view=app", "https://forest.example.com.evil/forest/?view=app", "https://forest.example.com/forest/?view=preview", "javascript:alert(1)", "https://forest.example.com/forest/?view=app#other").forEach { assertFalse(endpoint.allowsDocument(it), it) }
    }
    @Test fun `미설정 주소와 인증정보와 외부 평문 주소를 거부한다`() {
        listOf("", "file:///forest/index.html", "http://example.com/", "https://user:pass@example.com/", "https://example.com/?view=forest", "https://example.com/#fragment").forEach { assertNull(ForestEndpoint.parse(it), it) }
    }
    @Test fun `로컬 개발 주소의 포트를 유지하고 로컬 주소로 표시한다`() {
        val endpoint = assertNotNull(ForestEndpoint.parse("http://127.0.0.1:5173/"))
        assertTrue(endpoint.isLocalHttp)
        assertEquals("http://127.0.0.1:5173", endpoint.origin)
        assertTrue(endpoint.allowsDocument("http://127.0.0.1:5173/?view=app"))
        assertFalse(endpoint.allowsDocument("http://127.0.0.1:5174/?view=app"))
    }
    @Test fun `응답에 포함된 문자열은 실행 코드로 삽입되지 않는다`() {
        val raw = "\";alert(1);//\n"
        val script = ForestProtocol.deliveryScript(raw)
        assertTrue(script.contains("JSON.parse(\"\\\";alert(1);//\\n\")"))
    }
}
