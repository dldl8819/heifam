package com.balancify.backend.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where a notice's text shows an image it holds a marker, [[image:12]], naming the image by id.
 * The notices page writes the marker when an image is uploaded and draws the image in its place.
 */
final class NoticeImageTokens {

    private static final Pattern TOKEN = Pattern.compile("\\[\\[image:(\\d{1,15})]]");

    private NoticeImageTokens() {
    }

    /** The images a text names, each once, in the order they first appear. */
    static List<Long> imageIds(String content) {
        Set<Long> ids = new LinkedHashSet<>();
        if (content != null) {
            Matcher matcher = TOKEN.matcher(content);
            while (matcher.find()) {
                ids.add(Long.parseLong(matcher.group(1)));
            }
        }
        return new ArrayList<>(ids);
    }
}
