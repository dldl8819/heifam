package com.balancify.backend.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The id of a YouTube video, read from a link as a member pastes it. Only the id is ever kept:
 * eleven letters, digits, '-' and '_'. The page builds the player's address from the id alone, so
 * nothing else a link holds can reach the page.
 */
public final class YouTubeVideoIds {

    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final int MAX_LINK_LENGTH = 500;
    private static final Set<String> WATCH_HOSTS = Set.of(
        "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com",
        "youtube-nocookie.com", "www.youtube-nocookie.com"
    );
    // Paths under which the next segment is the id: /shorts/ID, /embed/ID, /live/ID, /v/ID.
    private static final Set<String> ID_PATHS = Set.of("shorts", "embed", "live", "v");

    private YouTubeVideoIds() {
    }

    public static boolean isVideoId(String value) {
        return value != null && VIDEO_ID.matcher(value).matches();
    }

    /** The video a link points at, or empty when it is not a link to one YouTube video. */
    public static Optional<String> fromLink(String link) {
        String text = link == null ? "" : link.trim();
        if (text.isEmpty() || text.length() > MAX_LINK_LENGTH) {
            return Optional.empty();
        }
        if (!text.contains("://")) {
            // Pasted without the scheme, as "youtu.be/..." or "www.youtube.com/watch?v=...".
            text = "https://" + text;
        }
        URI uri;
        try {
            uri = new URI(text);
        } catch (URISyntaxException exception) {
            return Optional.empty();
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !scheme.equals("http")) {
            return Optional.empty();
        }
        String[] segments = (uri.getRawPath() == null ? "" : uri.getRawPath()).split("/");

        String candidate = null;
        if (host.equals("youtu.be") || host.equals("www.youtu.be")) {
            candidate = segments.length > 1 ? segments[1] : null;
        } else if (WATCH_HOSTS.contains(host)) {
            if (segments.length > 1 && segments[1].equals("watch")) {
                candidate = queryValue(uri.getRawQuery(), "v");
            } else if (segments.length > 2 && ID_PATHS.contains(segments[1])) {
                candidate = segments[2];
            }
        }
        return isVideoId(candidate) ? Optional.of(candidate) : Optional.empty();
    }

    private static String queryValue(String rawQuery, String name) {
        if (rawQuery == null) {
            return null;
        }
        for (String pair : rawQuery.split("&")) {
            int equals = pair.indexOf('=');
            if (equals > 0 && pair.substring(0, equals).equals(name)) {
                return pair.substring(equals + 1);
            }
        }
        return null;
    }
}
