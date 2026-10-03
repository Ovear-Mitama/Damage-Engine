package damage.engine.hud;

import damage.engine.DamageEngineConfig;

/**
 * Shared damage number formatting. Applies the configurable thousands
 * separator (1000 -> "1,000") and the optional abbreviation (1500 -> "1.5K").
 */
public final class DamageNumberFormat {

    private static final String[] UNITS = {"K", "M", "B", "T"};

    private DamageNumberFormat() {}

    /**
     * Formats a damage value with the configured decimal places, optionally
     * abbreviated to K/M/B/T and, when not abbreviated, with a thousands
     * separator on the integer part.
     *
     * @param damage        the raw damage value
     * @param decimalPlaces how many decimals to show (<= 0 rounds to integer)
     */
    public static String formatDamage(float damage, int decimalPlaces) {
        DamageEngineConfig config = DamageEngineConfig.getInstance();
        if (config.abbreviateNumbers && Math.abs(damage) >= 1000f) {
            return abbreviate(damage, decimalPlaces);
        }
        String text = formatPlain(damage, decimalPlaces);
        return config.numberSeparator ? addSeparators(text) : text;
    }

    private static String formatPlain(float damage, int decimalPlaces) {
        if (decimalPlaces <= 0) {
            return String.valueOf(Math.round(damage));
        }
        return String.format("%." + decimalPlaces + "f", damage);
    }

    /**
     * Abbreviates a value of 1000 or more: 1500 -> "1.5K", 2000000 -> "2M".
     * Trailing zeros in the fraction are dropped so 2000 reads as "2K".
     */
    private static String abbreviate(float damage, int decimalPlaces) {
        int dp = Math.max(decimalPlaces, 0);
        int unit = 0;
        float scaled = damage / 1000f;
        // 四舍五入后可能进位回 1000(如 999999 -> "1000.0K"),这时再升一级单位
        while (unit < UNITS.length - 1 && roundedAbs(scaled, dp) >= 1000f) {
            scaled /= 1000f;
            unit++;
        }
        return stripTrailingZeros(String.format("%." + dp + "f", scaled)) + UNITS[unit];
    }

    private static float roundedAbs(float value, int decimalPlaces) {
        float factor = (float) Math.pow(10, decimalPlaces);
        return Math.abs(Math.round(value * factor) / factor);
    }

    private static String stripTrailingZeros(String text) {
        if (text.indexOf('.') < 0) {
            return text;
        }
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == '0') {
            end--;
        }
        if (end > 0 && text.charAt(end - 1) == '.') {
            end--;
        }
        return text.substring(0, end);
    }

    /**
     * Inserts ',' every three digits of the integer part, keeping the sign and
     * the fractional part intact. E.g. 1234567.89 -> "1,234,567.89", -1000 -> "-1,000".
     */
    private static String addSeparators(String text) {
        int sign = 0;
        String body = text;
        if (body.startsWith("-")) {
            sign = 1;
            body = body.substring(1);
        }
        int dot = body.indexOf('.');
        String intPart;
        String fracPart;
        if (dot >= 0) {
            intPart = body.substring(0, dot);
            fracPart = body.substring(dot); // includes '.'
        } else {
            intPart = body;
            fracPart = "";
        }

        StringBuilder sb = new StringBuilder();
        int len = intPart.length();
        for (int i = 0; i < len; i++) {
            if (i > 0 && (len - i) % 3 == 0) {
                sb.append(',');
            }
            sb.append(intPart.charAt(i));
        }
        return (sign == 1 ? "-" : "") + sb + fracPart;
    }
}
