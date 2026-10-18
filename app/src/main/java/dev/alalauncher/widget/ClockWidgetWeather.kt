// SPDX-License-Identifier: GPL-3.0-or-later
package dev.alalauncher.widget

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import java.util.Locale

/**
 * The last weather another app sent us, and whether it is still worth drawing.
 *
 * Ala has no network permission and is not getting one, so there is no fetching here: a
 * weather app that already has the forecast broadcasts it, and this is where what arrived is
 * kept. Nothing asks for a location either — the sender says where it is.
 *
 * Written to disk rather than held in memory because a widget outlives the process that drew
 * it. A launcher gets killed often, and weather that vanishes every time it does is worse than
 * no weather at all.
 */
object ClockWidgetWeather {

    private const val FILE = "clock_widget_weather"
    private const val KEY_TEMP_K = "tempK"
    private const val KEY_CONDITION = "condition"
    private const val KEY_RECEIVED_AT = "receivedAt"
    private const val KEY_FORECAST = "forecast"

    /**
     * Past this, say nothing rather than something old.
     *
     * A forecast is a claim about now. Breezy sends on its own schedule and after a long doze
     * the last one can be hours stale, which is the one case where a blank row is the honest
     * answer.
     */
    private const val FRESH_FOR_MS = 3 * 60 * 60 * 1000L

    /** Kelvin, and wide enough for anywhere on Earth without accepting a parse gone wrong. */
    private val PLAUSIBLE_K = 150..350

    /** Enough for "Partly cloudy"; past this something is wrong with the sender, not the sky. */
    private const val MAX_CONDITION_CHARS = 40

    /**
     * The names a sender has been seen to put the day's condition under.
     *
     * Tried in order and the first non-blank wins, so a payload carrying more than one of
     * them reads as the more specific one rather than as whichever happened to be parsed
     * first.
     */
    private val CONDITION_KEYS = listOf("dayCode", "conditionCode", "condition", "day_condition")

    /**
     * One coming day, as the sender described it.
     *
     * A day's own condition rather than today's, because the pair is what makes a forecast
     * worth drawing: "tomorrow 4°, rain" says something a single temperature cannot.
     */
    data class ForecastDay(
        val condition: String,
        val minTempK: Int,
        val maxTempK: Int,
    )

    /**
     * A row per day is a row that costs height, and the widget's height is shared with every
     * other line on it. Five is what fits without the forecast pushing the agenda off.
     */
    const val MAX_FORECAST_DAYS = 5

    data class Reading(
        val tempK: Int,
        val condition: String,
        /** Empty when the sender sent no forecast, which is a normal thing for it to do. */
        val forecast: List<ForecastDay> = emptyList(),
    )

    /** Null when nothing has arrived, or when what did is too old to repeat. */
    fun current(context: Context): Reading? {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val tempK = prefs.getInt(KEY_TEMP_K, 0)
        if (tempK !in PLAUSIBLE_K) return null
        val receivedAt = prefs.getLong(KEY_RECEIVED_AT, 0L)
        if (receivedAt <= 0L || System.currentTimeMillis() - receivedAt > FRESH_FOR_MS) return null
        return Reading(
            tempK,
            prefs.getString(KEY_CONDITION, null).orEmpty(),
            readForecast(prefs.getString(KEY_FORECAST, null)),
        )
    }

    /** Returns false for anything that does not look like a temperature, having stored nothing. */
    fun store(context: Context, tempK: Int, condition: String?, forecast: List<ForecastDay> = emptyList()): Boolean {
        if (tempK !in PLAUSIBLE_K) return false
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt(KEY_TEMP_K, tempK)
            .putString(KEY_CONDITION, condition?.take(MAX_CONDITION_CHARS)?.trim().orEmpty())
            .putString(KEY_FORECAST, encodeForecast(forecast))
            .putLong(KEY_RECEIVED_AT, System.currentTimeMillis())
            .apply()
        return true
    }

    /**
     * The coming days out of a Gadgetbridge weather payload.
     *
     * Pure and free of any Android type, so the parsing is testable on its own and a sender
     * that sends something strange is a test rather than a crash on someone's home screen.
     *
     * A day missing either temperature is dropped rather than drawn: "tomorrow, rain, and no
     * number at all" is a row that says nothing, and a row of those is worse than no row.
     */
    fun parseForecast(json: String?): List<ForecastDay> {
        if (json.isNullOrBlank()) return emptyList()
        val days = runCatching {
            val array = org.json.JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val minK = item.optInt("minTemp", Int.MIN_VALUE)
                    val maxK = item.optInt("maxTemp", Int.MIN_VALUE)
                    if (minK !in PLAUSIBLE_K || maxK !in PLAUSIBLE_K) continue
                    // The key for the day's condition is not the one every sender uses.
                    // Gadgetbridge calls it `dayCode`; Breezy, which is the sender this is
                    // mostly for, sends `conditionCode` and leaves `dayCode` absent - so
                    // reading only the documented name silently produced a forecast of five
                    // days with no sky on any of them. Asked of each name in turn, and the
                    // first one that is actually there wins.
                    val code = CONDITION_KEYS.firstNotNullOfOrNull { key ->
                        item.optString(key, "").takeIf { it.isNotBlank() }
                    }.orEmpty()
                    // Senders have been seen with the pair the wrong way round; a row
                    // reading "high 4°, low 19°" is a sender's bug made into a certainty.
                    add(
                        ForecastDay(
                            condition = code,
                            minTempK = minOf(minK, maxK),
                            maxTempK = maxOf(minK, maxK),
                        )
                    )
                    if (size == MAX_FORECAST_DAYS) return@buildList
                }
            }
        }.getOrDefault(emptyList())
        return days
    }

    /**
     * Turns a Gadgetbridge day code into words, the way a person would say it.
     *
     * The forecast carries a numeric code where today's condition carries free text, so the
     * day cannot go through the same substring matching: "2" is not a sky. Naming each one
     * means the row can be drawn with the same rule as everything else on the widget - match
     * the words, draw the glyph - instead of a second table of icons that would have to be
     * kept alongside the first.
     *
     * Past the codes named here the answer is unknown, and unknown is drawn as no glyph
     * rather than as a confident wrong sky, which is the same choice [glyphFor] makes.
     */
    fun conditionWordsForDayCode(code: String): String = when (code.trim()) {
        "0" -> "Clear"
        "1" -> "Mostly clear"
        "2" -> "Partly cloudy"
        "3" -> "Cloudy"
        "4" -> "Rainy"
        "5" -> "Heavy rain"
        "6" -> "Light rain"
        "7" -> "Light rain showers"
        "8" -> "Heavy rain showers"
        "9" -> "Light snow"
        "10" -> "Heavy snow"
        "11" -> "Light snow showers"
        "12" -> "Heavy snow showers"
        "13" -> "Light rain and snow"
        "14" -> "Heavy rain and snow"
        "15" -> "Light rain and snow showers"
        "16" -> "Heavy rain and snow showers"
        "17" -> "Light thunderstorm"
        "18" -> "Heavy thunderstorm"
        "19" -> "Light snow thunderstorm"
        "20" -> "Heavy snow thunderstorm"
        "21" -> "Light rain and snow thunderstorm"
        "22" -> "Heavy rain and snow thunderstorm"
        "23" -> "Light thunderstorm showers"
        "24" -> "Heavy thunderstorm showers"
        "25" -> "Light snow thunderstorm showers"
        "26" -> "Heavy snow thunderstorm showers"
        "27" -> "Light rain and snow thunderstorm showers"
        "28" -> "Heavy rain and snow thunderstorm showers"
        "29" -> "Breezy"
        "30" -> "Windy"
        "31" -> "Light rain and breezy"
        "32" -> "Heavy rain and breezy"
        "33" -> "Light snow and breezy"
        "34" -> "Heavy snow and breezy"
        "35" -> "Light rain and snow and breezy"
        "36" -> "Heavy rain and snow and breezy"
        "37" -> "Light thunderstorm and breezy"
        "38" -> "Heavy thunderstorm and breezy"
        "39" -> "Light snow thunderstorm and breezy"
        "40" -> "Heavy snow thunderstorm and breezy"
        "41" -> "Light rain and snow thunderstorm and breezy"
        "42" -> "Heavy rain and snow thunderstorm and breezy"
        "43" -> "Light thunderstorm showers and breezy"
        "44" -> "Heavy thunderstorm showers and breezy"
        "45" -> "Light snow thunderstorm showers and breezy"
        "46" -> "Heavy snow thunderstorm showers and breezy"
        "47" -> "Fog"
        "48" -> "Light hail"
        "49" -> "Light hail showers"
        "50" -> "Heavy hail showers"
        "51" -> "Light hail and rain"
        "52" -> "Heavy hail and rain"
        // Anything else is somebody's own code, or a version of this table from the future.
        else -> ""
    }

    /** Flat text rather than JSON: this is read on a widget thread with no schema to keep. */
    private fun encodeForecast(days: List<ForecastDay>): String =
        days.take(MAX_FORECAST_DAYS).joinToString(";") { day ->
            // The condition is words, so the separator cannot appear in it.
            "${day.minTempK},${day.maxTempK},${day.condition.replace(';', ' ').replace(',', ' ')}"
        }

    private fun readForecast(stored: String?): List<ForecastDay> {
        if (stored.isNullOrBlank()) return emptyList()
        return stored.split(';').mapNotNull { row ->
            val parts = row.split(',', limit = 3)
            if (parts.size < 3) return@mapNotNull null
            val minK = parts[0].trim().toIntOrNull() ?: return@mapNotNull null
            val maxK = parts[1].trim().toIntOrNull() ?: return@mapNotNull null
            if (minK !in PLAUSIBLE_K || maxK !in PLAUSIBLE_K) return@mapNotNull null
            ForecastDay(parts[2].trim(), minOf(minK, maxK), maxOf(minK, maxK))
        }.take(MAX_FORECAST_DAYS)
    }

    /** The reading on its own, for the row: just how warm it is. */
    fun degrees(reading: Reading, fahrenheit: Boolean): String =
        degreesFromKelvin(reading.tempK, fahrenheit)

    /**
     * One temperature in the unit that was asked for.
     *
     * Shared by today's reading and each forecast day so the two rows cannot disagree about
     * what a degree looks like - a widget reading "21°" and "66°" on the same line is a
     * widget nobody trusts.
     */
    fun degreesFromKelvin(kelvin: Int, fahrenheit: Boolean): String {
        val celsius = kelvin - 273.15
        val value = if (fahrenheit) celsius * 9 / 5 + 32 else celsius
        val unit = if (fahrenheit) "F" else "C"
        return String.format(Locale.getDefault(), "%.0f°%s", value, unit)
    }

    /**
     * Degrees and the condition in words, for anywhere that has room for both.
     *
     * The clock widget itself draws an icon instead of the words (see [glyphFor]) and keeps the
     * condition for the content description, because a glyph nobody can read is not a forecast.
     */
    fun formatted(reading: Reading, fahrenheit: Boolean): String {
        val degrees = degrees(reading, fahrenheit)
        return if (reading.condition.isEmpty()) degrees else "$degrees  ${reading.condition}"
    }

    /**
     * The picture that goes with a condition, if one can be told from the words.
     *
     * The condition is free text written by whichever app chose to send it, so this matches
     * substrings rather than expecting any particular wording. [UNKNOWN] is a real answer and
     * not a failure: a sender is free to send something this does not know, and drawing a
     * confident wrong sky is worse than drawing no sky.
     *
     * Two simplifications worth stating rather than hiding. A sender that reports more than one
     * condition at once ("partly cloudy, light rain later") is drawn as whichever word this
     * finds first, not as the first half of the sentence; parsing that needs a real forecast
     * model, and guessing at one would be worse than taking the first word. And "shower" is
     * matched on its own, so a phrase that uses it for something other than rain would be drawn
     * as rain. Both are recorded here because they are the cases that would otherwise look like
     * bugs in the drawing rather than in the matching.
     *
     * Deliberately free of any resource reference so it can be tested without a device. The
     * glyph-to-drawable step is the provider's, which is the only part that needs an `R`.
     */
    fun glyphFor(condition: String): WeatherGlyph {
        val text = condition.lowercase(Locale.ROOT)
        if (text.isBlank()) return WeatherGlyph.UNKNOWN
        // In this order because the general words are inside the specific ones: a thunderstorm
        // is also rain, sleet is also rain, "mostly cloudy" is also cloudy. Asking the broad
        // question first is how a thunderstorm ends up drawn as a drizzle.
        return when {
            contains(text, "thunder", "lightning", "storm", "thundershower") ->
                WeatherGlyph.THUNDERSTORM
            // Hail before the words it hides behind: "light hail and rain" is hail, and
            // asking the rain question first would draw a raindrop for a hailstorm.
            contains(text, "hail") -> WeatherGlyph.HAIL
            contains(text, "snow", "sleet", "blizzard", "freezing") ->
                WeatherGlyph.SNOW
            contains(text, "drizzle") -> WeatherGlyph.DRIZZLE
            contains(text, "rain", "shower") -> WeatherGlyph.RAIN
            contains(text, "fog", "mist", "haze", "smoke", "dust") -> WeatherGlyph.FOG
            contains(text, "partly", "mostly cloud", "few cloud",
                     "scattered", "broken", "intervals") -> WeatherGlyph.PARTLY_CLOUDY
            contains(text, "cloud", "overcast") -> WeatherGlyph.CLOUDY
            contains(text, "clear", "sunny", "fair", "blue sky") -> WeatherGlyph.CLEAR
            // Wind last, because it is nearly always a modifier on something else - "breezy",
            // "and breezy", "windy" - and the sky it modifies is the more useful half.
            contains(text, "wind", "breezy", "blow", "gust") -> WeatherGlyph.WIND
            else -> WeatherGlyph.UNKNOWN
        }
    }

    private fun contains(text: String, vararg needles: String): Boolean =
        needles.any { text.contains(it) }

    /** What can be drawn. Anything not listed here draws nothing rather than something wrong. */
    enum class WeatherGlyph {
        CLEAR,
        PARTLY_CLOUDY,
        CLOUDY,
        FOG,
        DRIZZLE,
        RAIN,
        SNOW,
        HAIL,
        THUNDERSTORM,
        WIND,
        UNKNOWN,
    }

    /** Where Fahrenheit is what people mean by a temperature, so the first draw is not wrong. */
    fun fahrenheitByDefault(): Boolean =
        Locale.getDefault().country in setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")

    /**
     * Turns the receiver on, or off again once no widget wants weather.
     *
     * It ships disabled. A receiver another app can reach is surface that should not exist
     * while the feature is switched off, and this is the one way to have none at all rather
     * than some that is merely careful.
     */
    fun setReceiverEnabled(context: Context, enabled: Boolean) {
        runCatching {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, ClockWidgetWeatherReceiver::class.java),
                if (enabled) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                },
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
