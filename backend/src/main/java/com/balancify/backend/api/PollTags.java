package com.balancify.backend.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * How a page that polls says which copy it already holds. The bell and the regular draft ask again
 * and again, and an ask that brings back the tag of a copy still current is answered without the copy
 * being read again: what the database sends out is what Supabase meters (egress).
 *
 * The site's API calls reach the backend through Vercel's CDN (an external rewrite, since 2026-11-08),
 * and from then on the backend answered no 304 at all: If-None-Match no longer reaches it as the page
 * sent it, or the CDN deals with it on the way. So the pages send the tag under a header of their own
 * and an unchanged copy is answered 204, with no body, which nothing on the way treats as a cache
 * answer. If-None-Match is still read and answered 304, as before, for anything that sends it.
 */
public final class PollTags {

    /** The tag a page sends back, and the tag of the current copy on every answer. */
    public static final String HEADER = "X-Poll-Tag";

    private PollTags() {
    }

    /** The tag the request brings back: HEADER first, else If-None-Match; null when it brings none. */
    public static String known(String pollTag, String ifNoneMatch) {
        if (pollTag != null && !pollTag.isBlank()) {
            return pollTag.trim();
        }
        return ifNoneMatch == null || ifNoneMatch.isBlank() ? null : ifNoneMatch.trim();
    }

    /** A copy still current: 204 to a page that asked with HEADER, 304 to an If-None-Match. */
    public static <T> ResponseEntity<T> unchanged(String pollTag, String tag) {
        HttpStatus status = pollTag != null && !pollTag.isBlank() ? HttpStatus.NO_CONTENT : HttpStatus.NOT_MODIFIED;
        return ResponseEntity.status(status).eTag(tag).header(HEADER, tag).build();
    }

    /** A copy read afresh, with its tag under both names. */
    public static <T> ResponseEntity<T> current(String tag, T body) {
        return ResponseEntity.ok().eTag(tag).header(HEADER, tag).body(body);
    }
}
