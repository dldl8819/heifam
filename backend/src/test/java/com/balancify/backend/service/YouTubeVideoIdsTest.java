package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class YouTubeVideoIdsTest {

    private static final String ID = "dQw4w9WgXcQ";

    @Test
    void readsTheIdFromEveryUsualFormOfLink() {
        for (String link : new String[] {
            "https://www.youtube.com/watch?v=" + ID,
            "https://www.youtube.com/watch?v=" + ID + "&t=42s&list=PL123",
            "https://www.youtube.com/watch?feature=share&v=" + ID,
            "https://youtube.com/watch?v=" + ID,
            "https://m.youtube.com/watch?v=" + ID,
            "https://music.youtube.com/watch?v=" + ID,
            "https://youtu.be/" + ID,
            "https://youtu.be/" + ID + "?si=abcDEF123&t=10",
            "https://www.youtube.com/shorts/" + ID,
            "https://www.youtube.com/live/" + ID + "?feature=shared",
            "https://www.youtube.com/embed/" + ID,
            "https://www.youtube-nocookie.com/embed/" + ID,
            "http://www.youtube.com/watch?v=" + ID,
            "  youtu.be/" + ID + "  ",
            "www.youtube.com/watch?v=" + ID,
            "HTTPS://WWW.YOUTUBE.COM/watch?v=" + ID,
        }) {
            assertThat(YouTubeVideoIds.fromLink(link)).as(link).contains(ID);
        }
    }

    @Test
    void takesNothingThatIsNotOneYouTubeVideo() {
        for (String link : new String[] {
            null,
            "",
            "   ",
            "https://www.youtube.com/",
            "https://www.youtube.com/watch",
            "https://www.youtube.com/watch?v=",
            "https://www.youtube.com/watch?v=short",
            "https://www.youtube.com/watch?v=" + ID + "x",
            "https://www.youtube.com/@channel",
            "https://www.youtube.com/playlist?list=PL123",
            "https://www.youtube.com/results?search_query=" + ID,
            "https://youtu.be/",
            // Somewhere else that only looks like it.
            "https://youtube.com.example.com/watch?v=" + ID,
            "https://example.com/watch?v=" + ID,
            "https://example.com/youtu.be/" + ID,
            "https://notyoutu.be/" + ID,
            "javascript:alert(1)//youtu.be/" + ID,
            "ftp://youtu.be/" + ID,
            "https://youtu.be/" + ID.substring(0, 10) + "\"",
            "https://youtu.be/" + ID.substring(0, 10) + "<",
            "https://youtu.be/" + "x".repeat(600),
        }) {
            assertThat(YouTubeVideoIds.fromLink(link)).as(String.valueOf(link)).isEqualTo(Optional.empty());
        }
    }

    @Test
    void knowsAnIdWhenItSeesOne() {
        assertThat(YouTubeVideoIds.isVideoId(ID)).isTrue();
        assertThat(YouTubeVideoIds.isVideoId("a-b_c-d_e-f")).isTrue();
        for (String value : new String[] {null, "", "short", ID + "x", "dQw4w9WgXc!", "dQw4w9WgX c"}) {
            assertThat(YouTubeVideoIds.isVideoId(value)).as(String.valueOf(value)).isFalse();
        }
    }
}
