package com.careercopilot.jobs.scraper;

import java.util.Locale;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

/** Text helpers shared by the sources. Jsoup never executes scripts, it only builds a tree we read text from. */
final class ScraperText {

    private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");
    private static final Pattern REMOTE = Pattern.compile("\\bremote\\b");
    private static final Pattern HYBRID = Pattern.compile("\\bhybrid\\b");
    // A private-use character survives wholeText() and is swapped for a real newline afterwards.
    private static final String NL = "";

    private ScraperText() {
    }

    /** Greenhouse sends HTML escaped once more (&lt;p&gt;), so it is unescaped first when it holds no real tags. */
    static String unescapedHtmlToText(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String html = content.contains("<") ? content : Parser.unescapeEntities(content, false);
        return htmlToText(html);
    }

    static String htmlToText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Document doc = Jsoup.parse(html);
        doc.select("script, style").remove();
        // wholeText() breaks lines at br but not at block elements, so those get a marker swapped for a newline below.
        for (Element element : doc.select("p, div, ul, ol, h1, h2, h3, h4, h5, h6, tr")) {
            element.before(NL);
            element.after(NL);
        }
        for (Element item : doc.select("li")) {
            item.before(NL + "- ");
        }
        String text = doc.body().wholeText().replace(NL, "\n").replace(' ', ' ');
        return tidy(text);
    }

    static String tidy(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (String line : text.replace("\r", "").split("\n", -1)) {
            out.append(line.strip().replaceAll("[ \\t]+", " ")).append('\n');
        }
        return BLANK_LINES.matcher(out).replaceAll("\n\n").strip();
    }

    /** Only a clear statement counts; an unclear location stays null instead of guessing. */
    static String remoteTypeFromText(String text) {
        if (text == null) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (HYBRID.matcher(lower).find()) {
            return "hybrid";
        }
        return REMOTE.matcher(lower).find() ? "remote" : null;
    }

    /** Trims and cuts to the column length; blank becomes null. */
    static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
