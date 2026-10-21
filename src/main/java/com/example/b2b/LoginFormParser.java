package com.example.b2b;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal, dependency-free extractor for HTML login forms. It finds the form that contains a
 * password field and collects its action, method and pre-populated inputs (hidden CSRF tokens,
 * session ids, etc.) so they can be re-submitted with the user's credentials.
 *
 * <p>This is deliberately not a full HTML parser; it handles the server-rendered login pages
 * produced by common IdPs (Keycloak, PingFederate, ForgeRock, Azure AD B2C custom pages, etc.).</p>
 */
public final class LoginFormParser {

    private static final Pattern FORM = Pattern.compile("<form\\b([^>]*)>(.*?)</form\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern INPUT = Pattern.compile("<input\\b([^>]*)>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "([a-zA-Z_:][-a-zA-Z0-9_:.]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))",
            Pattern.DOTALL);
    private static final Pattern BOOLEAN_CHECKED = Pattern.compile("(?i)(^|\\s)checked(\\s|=|$)");
    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x?)([0-9a-fA-F]+);");
    private static final Set<String> SKIPPED_TYPES = Set.of("submit", "button", "reset", "image", "file");

    /** A login form ready to be submitted. */
    public record HtmlForm(String action, String method, Map<String, String> fields) {
    }

    /**
     * Returns the most likely login form: the first form containing a password input, or else
     * the first form containing an input named {@code usernameField}.
     */
    public Optional<HtmlForm> parse(String html, String usernameField) {
        if (html == null) {
            return Optional.empty();
        }
        HtmlForm fallback = null;
        Matcher formMatcher = FORM.matcher(html);
        while (formMatcher.find()) {
            Map<String, String> formAttrs = attributes(formMatcher.group(1));
            List<Map<String, String>> inputs = inputs(formMatcher.group(2));
            HtmlForm form = toForm(formAttrs, inputs);
            boolean hasPassword = inputs.stream()
                    .anyMatch(attrs -> "password".equalsIgnoreCase(attrs.getOrDefault("type", "")));
            if (hasPassword) {
                return Optional.of(form);
            }
            if (fallback == null && inputs.stream().anyMatch(attrs -> usernameField.equals(attrs.get("name")))) {
                fallback = form;
            }
        }
        return Optional.ofNullable(fallback);
    }

    private static HtmlForm toForm(Map<String, String> formAttrs, List<Map<String, String>> inputs) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (Map<String, String> attrs : inputs) {
            String name = attrs.get("name");
            String type = attrs.getOrDefault("type", "text").toLowerCase(Locale.ROOT);
            if (name == null || name.isEmpty() || SKIPPED_TYPES.contains(type)) {
                continue;
            }
            if (("checkbox".equals(type) || "radio".equals(type)) && !attrs.containsKey("checked")) {
                continue;
            }
            String value = attrs.get("value");
            fields.putIfAbsent(name, value == null ? ("checkbox".equals(type) ? "on" : "") : value);
        }
        String method = formAttrs.getOrDefault("method", "GET").toUpperCase(Locale.ROOT);
        return new HtmlForm(formAttrs.getOrDefault("action", ""), method, fields);
    }

    private static List<Map<String, String>> inputs(String formBody) {
        List<Map<String, String>> result = new ArrayList<>();
        Matcher inputMatcher = INPUT.matcher(formBody);
        while (inputMatcher.find()) {
            String raw = inputMatcher.group(1);
            Map<String, String> attrs = attributes(raw);
            if (BOOLEAN_CHECKED.matcher(stripQuotedValues(raw)).find()) {
                attrs.putIfAbsent("checked", "checked");
            }
            result.add(attrs);
        }
        return result;
    }

    private static Map<String, String> attributes(String tagContent) {
        Map<String, String> attrs = new LinkedHashMap<>();
        Matcher m = ATTRIBUTE.matcher(tagContent);
        while (m.find()) {
            String value = m.group(2) != null ? m.group(2) : m.group(3) != null ? m.group(3) : m.group(4);
            attrs.putIfAbsent(m.group(1).toLowerCase(Locale.ROOT), decodeEntities(value));
        }
        return attrs;
    }

    private static String stripQuotedValues(String tagContent) {
        return tagContent.replaceAll("\"[^\"]*\"|'[^']*'", "\"\"");
    }

    /** Decodes the HTML entities that commonly appear in attribute values (notably {@code &amp;} in URLs). */
    static String decodeEntities(String value) {
        if (value == null || value.indexOf('&') < 0) {
            return value;
        }
        Matcher m = NUMERIC_ENTITY.matcher(value);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            int codePoint = Integer.parseInt(m.group(2), m.group(1).isEmpty() ? 10 : 16);
            m.appendReplacement(sb, Matcher.quoteReplacement(new String(Character.toChars(codePoint))));
        }
        m.appendTail(sb);
        return sb.toString()
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
    }
}
