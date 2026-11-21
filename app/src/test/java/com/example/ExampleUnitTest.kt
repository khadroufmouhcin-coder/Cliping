package com.example

import com.example.data.YouTubeService
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    private val service = YouTubeService()

    @Test
    fun extractVideoId_handlesAllYouTubeFormats() {
        assertEquals("dQw4w9WgXcQ", service.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", service.extractVideoId("https://youtu.be/dQw4w9WgXcQ?si=abc"))
        assertEquals("jfKfPfyJRdk", service.extractVideoId("https://www.youtube.com/live/jfKfPfyJRdk?feature=share"))
        assertEquals("abc123XYZ_-", service.extractVideoId("https://youtube.com/shorts/abc123XYZ_-"))
        assertEquals("", service.extractVideoId("https://example.com/not-youtube"))
    }
}
