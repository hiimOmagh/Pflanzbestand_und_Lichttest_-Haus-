package de.oabidi.pflanzenbestandundlichttest.common.util;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;

import java.util.Locale;

/**
 * Utility methods for working with the application's locale selection.
 */
public final class LocaleHelper {
    private static final String DEFAULT_LANGUAGE = Locale.ENGLISH.getLanguage();

    private LocaleHelper() {
        // Utility class
    }

    /**
     * Applies the persisted locale to the supplied {@link Context}.
     *
     * @param context the base context
     * @return a context configured with the persisted locale
     */
    public static Context applyLocale(Context context) {
        String language = getPersistedLanguage(context);
        return updateResources(context, language);
    }

    /**
     * Persists and applies the provided language code to the supplied {@link Context}.
     *
     * @param context      the base context
     * @param languageCode the ISO language code to apply
     * @return a context configured with the provided locale
     */
    public static Context applyLocale(Context context, String languageCode) {
        String normalized = normalizeLanguage(context, languageCode);
        persistLanguage(context, normalized);
        return updateResources(context, normalized);
    }

    /**
     * Recreates the supplied activity to apply any pending locale changes.
     *
     * @param activity the activity to recreate
     */
    public static void recreateActivity(Activity activity) {
        activity.recreate();
    }

    private static void persistLanguage(Context context, String languageCode) {
        SharedPreferences prefs = context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(SettingsKeys.KEY_LANGUAGE, languageCode).apply();
    }

    private static String getPersistedLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE);
        String persisted = prefs.getString(SettingsKeys.KEY_LANGUAGE, null);
        return normalizeLanguage(context, persisted);
    }

    private static String normalizeLanguage(Context context, String languageCode) {
        if (languageCode == null || languageCode.trim().isEmpty()) {
            String systemLanguage = getSystemLanguage(context);
            return systemLanguage != null ? systemLanguage : DEFAULT_LANGUAGE;
        }
        return languageCode.trim().toLowerCase(Locale.ROOT);
    }

    private static String getSystemLanguage(Context context) {
        Resources resources = context.getResources();
        Configuration configuration = resources.getConfiguration();
        Locale locale;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            LocaleList locales = configuration.getLocales();
            locale = !locales.isEmpty() ? locales.get(0) : Locale.getDefault();
        } else {
            locale = configuration.locale;
        }

        if (locale == null) {
            locale = Locale.getDefault();
        }

        if (locale == null) {
            return null;
        }

        String language = locale.getLanguage();
        if (language == null || language.trim().isEmpty()) {
            return null;
        }

        return language.trim().toLowerCase(Locale.ROOT);
    }

    private static Context updateResources(Context context, String languageCode) {
        Locale locale = toLocale(languageCode);
        return updateResources(context, locale);
    }

    private static Locale toLocale(String languageCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return Locale.forLanguageTag(languageCode);
        }
        return new Locale(languageCode);
    }

    @SuppressWarnings("deprecation")
    private static Context updateResources(Context context, Locale locale) {
        Locale.setDefault(locale);
        Resources resources = context.getResources();
        Configuration configuration = new Configuration(resources.getConfiguration());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocale(locale);
            configuration.setLayoutDirection(locale);
            configuration.setLocales(new LocaleList(locale));
            Context localizedContext = context.createConfigurationContext(configuration);
            resources.updateConfiguration(configuration, resources.getDisplayMetrics());
            return localizedContext;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            configuration.setLocale(locale);
            configuration.setLayoutDirection(locale);
            Context localizedContext = context.createConfigurationContext(configuration);
            resources.updateConfiguration(configuration, resources.getDisplayMetrics());
            return localizedContext;
        } else {
            configuration.locale = locale;
            resources.updateConfiguration(configuration, resources.getDisplayMetrics());
            return context;
        }
    }
}
