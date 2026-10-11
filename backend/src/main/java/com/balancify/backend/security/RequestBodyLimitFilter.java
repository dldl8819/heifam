package com.balancify.backend.security;

import com.balancify.backend.service.NoticeImageService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Keeps request bodies small, so that no request can make the backend hold more than a little of
 * it in memory. A body that says it is larger is refused before it is read (413); one that does
 * not say, or says less than it sends, stops being read at the limit, and the request fails: 413,
 * or 400 where the JSON reader turns it into its own error. Every body the API takes is far below
 * the limit: posts, notices, votes, imports. A notice image has its own, larger limit, which its
 * upload checks again.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestBodyLimitFilter extends OncePerRequestFilter {

    static final long MAX_BODY_BYTES = 2L * 1024 * 1024;
    private static final PathPattern NOTICE_IMAGE_UPLOAD =
        PathPatternParser.defaultInstance.parse("/api/groups/{groupId}/notice-images");

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        long limit = limitFor(request);
        if (request.getContentLengthLong() > limit) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }
        try {
            filterChain.doFilter(new LimitedBodyRequest(request, limit), response);
        } catch (BodyTooLargeException tooLarge) {
            // A reader that let the limit through (a notice image does) answers 413 too, not 500.
            if (response.isCommitted()) {
                throw tooLarge;
            }
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        }
    }

    static long limitFor(HttpServletRequest request) {
        if ("POST".equalsIgnoreCase(request.getMethod()) && isNoticeImageUpload(request.getRequestURI())) {
            return NoticeImageService.MAX_IMAGE_BYTES;
        }
        return MAX_BODY_BYTES;
    }

    private static boolean isNoticeImageUpload(String uri) {
        if (uri == null) {
            return false;
        }
        try {
            return NOTICE_IMAGE_UPLOAD.matches(PathContainer.parsePath(uri));
        } catch (IllegalArgumentException malformedPath) {
            return false;
        }
    }

    /** Raised when a body goes on past the limit; whoever reads it stops there. */
    static final class BodyTooLargeException extends IOException {
        BodyTooLargeException(long limit) {
            super("Request body is larger than " + limit + " bytes");
        }
    }

    private static final class LimitedBodyRequest extends HttpServletRequestWrapper {

        private final long limit;
        private ServletInputStream stream;

        LimitedBodyRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                stream = new LimitedInputStream(super.getInputStream(), limit);
            }
            return stream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

    private static final class LimitedInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long limit;
        private long read;

        LimitedInputStream(ServletInputStream delegate, long limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value >= 0) {
                count(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            // Never ask for more than one byte past the limit, so an endless body is cut off there.
            long allowed = Math.max(1, Math.min(length, limit - read + 1));
            int count = delegate.read(buffer, offset, (int) allowed);
            if (count > 0) {
                count(count);
            }
            return count;
        }

        private void count(long bytes) throws IOException {
            read += bytes;
            if (read > limit) {
                throw new BodyTooLargeException(limit);
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }
    }
}
