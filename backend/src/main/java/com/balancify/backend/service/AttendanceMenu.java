package com.balancify.backend.service;

import java.util.Locale;
import java.util.Optional;

/**
 * The menus whose first opening each day earns an attendance point, by the key the pages send:
 * the four boards (notices among them) and six more pages. A day's attendance is a point a menu.
 */
public enum AttendanceMenu {
    NOTICES("notices"),
    FREE_BOARD("free-board"),
    ANONYMOUS_BOARD("anonymous-board"),
    VIDEO_BOARD("video-board"),
    PLAYERS("players"),
    RANKING("ranking"),
    PREDICTIONS("predictions"),
    PARTNERS("partners"),
    RESULTS("results"),
    POINT_POLICY("point-policy");

    private final String key;

    AttendanceMenu(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** The menu a page names, or empty for anything else. */
    public static Optional<AttendanceMenu> fromKey(String value) {
        String key = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        for (AttendanceMenu menu : values()) {
            if (menu.key.equals(key)) {
                return Optional.of(menu);
            }
        }
        return Optional.empty();
    }
}
