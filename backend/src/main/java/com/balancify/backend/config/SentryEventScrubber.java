package com.balancify.backend.config;

import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.Message;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryException;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * The last word on what a Sentry event carries: nothing that names a member. The anonymous board
 * must never give its writer away, and an account deleted here cannot be deleted from Sentry, so
 * an event keeps the error and where it happened, never who was involved.
 *
 * - no user and no server name;
 * - of the request, the method and the URL path only (ids, no names): headers, cookies, the query
 *   string (search words), the body and environment are dropped;
 * - email addresses in messages, exception texts and breadcrumbs are masked.
 *
 * Sentry's Spring Boot integration picks this bean up as its before-send callback.
 */
@Component
public class SentryEventScrubber implements SentryOptions.BeforeSendCallback {

    static final String MASK = "[email]";
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    @Override
    public SentryEvent execute(SentryEvent event, Hint hint) {
        event.setUser(null);
        event.setServerName(null);

        Request request = event.getRequest();
        if (request != null) {
            request.setHeaders(null);
            request.setCookies(null);
            request.setQueryString(null);
            request.setData(null);
            request.setEnvs(null);
            request.setOthers(null);
            request.setUrl(withoutQuery(request.getUrl()));
        }

        Message message = event.getMessage();
        if (message != null) {
            message.setMessage(mask(message.getMessage()));
            message.setFormatted(mask(message.getFormatted()));
            message.setParams(null);
        }

        List<SentryException> exceptions = event.getExceptions();
        if (exceptions != null) {
            for (SentryException exception : exceptions) {
                exception.setValue(mask(exception.getValue()));
            }
        }

        List<Breadcrumb> breadcrumbs = event.getBreadcrumbs();
        if (breadcrumbs != null) {
            for (Breadcrumb breadcrumb : breadcrumbs) {
                breadcrumb.setMessage(mask(breadcrumb.getMessage()));
                breadcrumb.getData().replaceAll((key, value) -> value instanceof String text ? mask(withoutQuery(text)) : value);
            }
        }
        return event;
    }

    static String mask(String text) {
        return text == null ? null : EMAIL.matcher(text).replaceAll(MASK);
    }

    private static String withoutQuery(String url) {
        if (url == null) {
            return null;
        }
        int query = url.indexOf('?');
        return query < 0 ? url : url.substring(0, query);
    }
}
