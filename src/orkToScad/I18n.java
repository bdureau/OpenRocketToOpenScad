package orkToScad;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Translations of the application.
 *
 * Texts are in {@code orkToScad/i18n/messages_<language>.properties}, read as UTF-8 (so the Java sources
 * stay plain ASCII, whatever the editor's encoding). English is the reference: a text missing in a
 * language falls back to English, then to its key.
 *
 * By default the language is the operating system's, if it is one of {@link #LANGUAGES}, otherwise
 * English. The user can choose another one ({@link #setLanguage(String)}).
 *
 * Placeholders are {0}, {1}... and are replaced literally (no MessageFormat quoting rules, so
 * translators can write apostrophes freely). Plural forms use the keys {@code <key>.one},
 * {@code <key>.few} and {@code <key>.many} (see {@link #plural(String, long)}).
 */
public final class I18n {

    /** Available languages, in the order shown to the user. */
    public static final List<String> LANGUAGES = List.of("en", "fr", "de", "es", "it", "pt", "nl", "pl", "ru",
            "tr", "hu");

    private static final String DEFAULT = "en";
    private static final Map<String, Properties> CACHE = new HashMap<>();

    /**
     * The operating system's language, read once at start-up: choosing a language later changes Java's
     * default locale (for Swing's own dialogs), which must not change what "automatic" means.
     */
    private static final String SYSTEM = detectSystemLanguage();

    private static String language = SYSTEM;

    private I18n() {
    }

    private static String detectSystemLanguage() {
        String lang = Locale.getDefault(Locale.Category.DISPLAY).getLanguage();
        return LANGUAGES.contains(lang) ? lang : DEFAULT;
    }

    /** The operating system's language if available, otherwise English. */
    public static String systemLanguage() {
        return SYSTEM;
    }

    /** Sets the language; null, empty or unknown means the operating system's language. */
    public static void setLanguage(String lang) {
        language = lang != null && LANGUAGES.contains(lang) ? lang : systemLanguage();
    }

    public static String language() {
        return language;
    }

    public static Locale locale() {
        return Locale.forLanguageTag(language);
    }

    /** Name of a language in that language ("Deutsch", "Polski"...). */
    public static String nativeName(String lang) {
        String name = bundle(lang).getProperty("language.name");
        return name == null ? lang : name;
    }

    /** Translated text, with {0}, {1}... replaced by the arguments (a {@link Msg} argument is translated too). */
    public static String tr(String key, Object... args) {
        String text = bundle(language).getProperty(key);
        if (text == null) {
            text = bundle(DEFAULT).getProperty(key);
        }
        if (text == null) {
            text = key;
        }
        return fill(text, args);
    }

    /**
     * Plural form: uses {@code key.one}, {@code key.few} or {@code key.many} according to the rules of
     * the current language, with {0} replaced by the number.
     */
    public static String plural(String key, long n) {
        String form = pluralForm(language, n);
        String text = bundle(language).getProperty(key + "." + form);
        if (text == null) {
            text = bundle(language).getProperty(key + ".many");
        }
        if (text == null) {
            text = bundle(DEFAULT).getProperty(key + (n == 1 ? ".one" : ".many"));
        }
        return fill(text == null ? key : text, new Object[] { integer(n) });
    }

    /** CLDR plural categories, simplified to one / few / many. */
    static String pluralForm(String lang, long n) {
        long n10 = n % 10, n100 = n % 100;
        switch (lang) {
            case "ru":
                if (n10 == 1 && n100 != 11) {
                    return "one";
                }
                return n10 >= 2 && n10 <= 4 && (n100 < 12 || n100 > 14) ? "few" : "many";
            case "pl":
                if (n == 1) {
                    return "one";
                }
                return n10 >= 2 && n10 <= 4 && (n100 < 12 || n100 > 14) ? "few" : "many";
            case "fr":
            case "pt":
                return n <= 1 ? "one" : "many";
            default:
                return n == 1 ? "one" : "many";
        }
    }

    /** A length in mm with up to 2 decimals and the language's decimal separator ("76.26", "76,26"). */
    public static String mm(double v) {
        DecimalFormat f = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(locale()));
        String s = f.format(v);
        return s.equals("-0") ? "0" : s;
    }

    /** A decimal number with one decimal ("3.1", "3,1"). */
    public static String decimal1(double v) {
        return new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(locale())).format(v);
    }

    /** An integer with the language's thousands separator ("76,000", "76 000", "76.000"). */
    public static String integer(long n) {
        DecimalFormat f = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(locale()));
        return f.format(n).replace('\u202f', '\u00a0');
    }

    private static String fill(String text, Object[] args) {
        if (args == null || args.length == 0) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length() + 32);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{' && i + 2 < text.length() && Character.isDigit(text.charAt(i + 1))
                    && text.charAt(i + 2) == '}') {
                int idx = text.charAt(i + 1) - '0';
                sb.append(idx < args.length ? String.valueOf(args[idx]) : "{" + idx + "}");
                i += 2;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static synchronized Properties bundle(String lang) {
        return CACHE.computeIfAbsent(lang, l -> {
            Properties p = new Properties();
            try (InputStream in = I18n.class.getResourceAsStream("i18n/messages_" + l + ".properties")) {
                if (in != null) {
                    try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                        p.load(r);
                    }
                }
            } catch (IOException | IllegalArgumentException e) {
                // missing or broken file: fall back to English / keys
            }
            return p;
        });
    }

    /** A message to translate when it is displayed (so the report follows a change of language). */
    public static final class Msg {
        private final String key;
        private final Object[] args;

        public Msg(String key, Object... args) {
            this.key = key;
            this.args = args;
        }

        @Override
        public String toString() {
            return tr(key, args);
        }
    }

    /** An I/O error whose message is translated when displayed. */
    public static final class LocalizedIOException extends IOException {
        private static final long serialVersionUID = 1L;
        private final transient Msg msg;

        public LocalizedIOException(Msg msg) {
            super(msg.key);
            this.msg = msg;
        }

        public LocalizedIOException(Msg msg, Throwable cause) {
            super(msg.key, cause);
            this.msg = msg;
        }

        public Msg msg() {
            return msg;
        }

        @Override
        public String getMessage() {
            return msg.toString();
        }
    }
}
