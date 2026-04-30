package com.balancify.backend.logging;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.List;
import java.util.regex.Pattern;

public class SensitiveDataMaskingPatternLayout extends PatternLayout {

    private static final String REDACTED = "[REDACTED]";
    private static final List<Pattern> MASK_PATTERNS = List.of(
        Pattern.compile("(?i)(authorization\\s*[:=]\\s*bearer\\s+)([^\\s,;]+)"),
        Pattern.compile("(?i)((?:api[-_ ]?key|access[-_ ]?key|secret[-_ ]?key|client[-_ ]?secret|password|passwd|token|jwt)\\s*[:=]\\s*)([^\\s,;]+)"),
        Pattern.compile("(?i)((?:spring\\.datasource\\.password|upbit\\.secret[-_ ]?key|jwt\\.secret)\\s*[:=]\\s*)([^\\s,;]+)")
    );

    @Override
    public String doLayout(ILoggingEvent event) {
        String rendered = super.doLayout(event);
        String masked = rendered;
        for (Pattern pattern : MASK_PATTERNS) {
            masked = pattern.matcher(masked).replaceAll("$1" + REDACTED);
        }
        return masked;
    }
}
