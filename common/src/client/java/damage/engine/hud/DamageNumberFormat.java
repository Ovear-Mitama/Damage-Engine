package damage.engine.hud;

import damage.engine.DamageEngineConfig;

/**
 * Shared damage number formatting. Applies the configurable thousand
 * separator: 1000 -> "1,000" when numberSeparator is enabled.
 */
public final class DamageNumberFormat {

    private DamageNumberFormat() {}

    /**
     * Formats a damage value with the configured decimal places and, when
     * enabled in config, a thousands separator on the integer part.
     *
     * @param damage        the raw damage value
     * @param decimalPlaces how many decimals to show (<= 0 rounds to integer)
     */
    public static String formatDamage(float damage, int decimalPlaces) {
        String text;
        if (decimalPlaces <= 0) {
            text = String.valueOf(Math.round(damage));
        } else {
            text = String.format("%." + decimalPlaces + "f", damage);
        }
        return DamageEngineConfig.getInstance().numberSeparator ? addSeparators(text) : text;
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
