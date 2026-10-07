package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NoticeImageTokensTest {

    @Test
    void readsTheImagesATextNamesInTheirOrder() {
        String content = "intro\n[[image:12]]\nmiddle [[image:7]] end\n[[image:300]]";

        assertThat(NoticeImageTokens.imageIds(content)).containsExactly(12L, 7L, 300L);
    }

    @Test
    void countsAnImageShownTwiceOnce() {
        assertThat(NoticeImageTokens.imageIds("[[image:4]] again [[image:4]]")).containsExactly(4L);
    }

    @Test
    void findsNothingInTextWithoutMarkers() {
        assertThat(NoticeImageTokens.imageIds("plain text")).isEmpty();
        assertThat(NoticeImageTokens.imageIds("")).isEmpty();
        assertThat(NoticeImageTokens.imageIds(null)).isEmpty();
    }

    @Test
    void ignoresWhatOnlyLooksLikeAMarker() {
        String content = "[[image:]] [[image:abc]] [image:5] [[image:-3]] [[image: 5]] [[IMAGE:5]] "
            + "[[image:1234567890123456]]";

        assertThat(NoticeImageTokens.imageIds(content)).isEmpty();
    }
}
