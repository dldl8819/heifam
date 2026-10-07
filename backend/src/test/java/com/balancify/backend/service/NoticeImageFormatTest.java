package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class NoticeImageFormatTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13};
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};
    static final byte[] WEBP = {'R', 'I', 'F', 'F', 36, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};

    @Test
    void tellsTheThreeKindsFromTheirFirstBytes() {
        assertThat(NoticeImageFormat.detect(PNG)).contains(NoticeImageFormat.PNG);
        assertThat(NoticeImageFormat.detect(JPEG)).contains(NoticeImageFormat.JPEG);
        assertThat(NoticeImageFormat.detect(WEBP)).contains(NoticeImageFormat.WEBP);
        assertThat(NoticeImageFormat.PNG.contentType()).isEqualTo("image/png");
        assertThat(NoticeImageFormat.JPEG.contentType()).isEqualTo("image/jpeg");
        assertThat(NoticeImageFormat.WEBP.contentType()).isEqualTo("image/webp");
    }

    @Test
    void takesNothingElseForAnImage() {
        assertThat(NoticeImageFormat.detect(null)).isEmpty();
        assertThat(NoticeImageFormat.detect(new byte[0])).isEmpty();
        assertThat(NoticeImageFormat.detect("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(NoticeImageFormat.detect("<html><script>1</script></html>".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(NoticeImageFormat.detect("GIF89a".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        // Another RIFF file, such as a WAV, is not a WebP.
        assertThat(NoticeImageFormat.detect("RIFF\0\0\0\0WAVEfmt ".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        // Cut off before the signature ends.
        assertThat(NoticeImageFormat.detect(new byte[] {(byte) 0x89, 'P', 'N', 'G'})).isEmpty();
        assertThat(NoticeImageFormat.detect(new byte[] {(byte) 0xFF, (byte) 0xD8})).isEmpty();
    }
}
