package damage.engine.hud.rating;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 加分表达式求值。
 *
 * <p>支持数字、{@code + - * /}、括号与占位符，例如：</p>
 * <pre>
 *   {combo} * 2
 *   {kill} * 50
 *   {kill} * {meter} * 5
 *   {damage:1}
 * </pre>
 *
 * <p>支持比较与条件表达式，例如 {@code {kill_meter} > 5 ? {kill_meter} * 2 : 0}。</p>
 *
 * <p>占位符格式为 <code>{变量名}</code> 或 <code>{变量名:倍率}</code>，
 * 例如 {@code {kill:25}} 表示击杀时取 25，{@code {damage:1}} 表示伤害值 ×1；
 * 变量不存在时按 0 处理。</p>
 */
public final class ScoreExpression {
    private static final Map<String, Optional<Node>> CACHE = new ConcurrentHashMap<>();

    private ScoreExpression() {}

    /** 计算表达式；语法错误或表达式为空时返回 0。 */
    public static float evaluate(String expression, Map<String, Float> variables) {
        if (expression == null || expression.trim().isEmpty()) return 0f;
        Optional<Node> node = CACHE.computeIfAbsent(expression,
            text -> Optional.ofNullable(parse(text)));
        if (!node.isPresent()) return 0f;
        try {
            float value = node.get().eval(variables);
            return Float.isFinite(value) ? value : 0f;
        } catch (RuntimeException e) {
            return 0f;
        }
    }

    /** 表达式是否可解析。 */
    public static boolean isValid(String expression) {
        if (expression == null || expression.trim().isEmpty()) return false;
        return CACHE.computeIfAbsent(expression, text -> Optional.ofNullable(parse(text))).isPresent();
    }

    /**
     * 读取 {@code {变量,time:...}} 里声明的重置时间（毫秒）。
     * 没有声明时返回 -1。
     */
    public static float timeout(String expression, String variable, Map<String, Float> variables) {
        if (expression == null || expression.trim().isEmpty() || variable == null || variable.trim().isEmpty()) {
            return -1f;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("\\{(?i:" + java.util.regex.Pattern.quote(variable) + ")\\s*,\\s*(?i:time)\\s*:\\s*([^},]+)}")
            .matcher(expression);
        if (!matcher.find()) return -1f;
        float timeout = evaluate(matcher.group(1), variables);
        return timeout > 0f ? timeout : -1f;
    }

    private interface Node {
        float eval(Map<String, Float> variables);
    }

    private static Node parse(String text) {
        try {
            Parser parser = new Parser(text);
            Node node = parser.parseTernary();
            parser.skipSpaces();
            return parser.atEnd() ? node : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static final class Parser {
        private final String text;
        private int index;

        Parser(String text) {
            this.text = text;
        }

        /** 条件表达式：condition ? whenTrue : whenFalse */
        Node parseTernary() {
            Node condition = parseComparison();
            skipSpaces();
            if (peek() != '?') return condition;
            index++;
            Node whenTrue = parseTernary();
            skipSpaces();
            if (peek() != ':') throw new IllegalStateException("条件表达式缺少冒号");
            index++;
            Node whenFalse = parseTernary();
            return vars -> condition.eval(vars) != 0f ? whenTrue.eval(vars) : whenFalse.eval(vars);
        }

        /** 比较运算：> >= < <= == !=，结果为 1 或 0。 */
        Node parseComparison() {
            Node left = parseExpression();
            while (true) {
                skipSpaces();
                String op = matchComparisonOperator();
                if (op == null) return left;
                Node right = parseExpression();
                Node lhs = left;
                Node next;
                switch (op) {
                    case ">":
                        next = vars -> lhs.eval(vars) > right.eval(vars) ? 1f : 0f;
                        break;
                    case ">=":
                        next = vars -> lhs.eval(vars) >= right.eval(vars) ? 1f : 0f;
                        break;
                    case "<":
                        next = vars -> lhs.eval(vars) < right.eval(vars) ? 1f : 0f;
                        break;
                    case "<=":
                        next = vars -> lhs.eval(vars) <= right.eval(vars) ? 1f : 0f;
                        break;
                    case "==":
                        next = vars -> Math.abs(lhs.eval(vars) - right.eval(vars)) < 1e-4f ? 1f : 0f;
                        break;
                    default:
                        next = vars -> Math.abs(lhs.eval(vars) - right.eval(vars)) >= 1e-4f ? 1f : 0f;
                        break;
                }
                left = next;
            }
        }

        String matchComparisonOperator() {
            if (atEnd()) return null;
            char c = peek();
            char next = index + 1 < text.length() ? text.charAt(index + 1) : '\0';
            if (c == '>' || c == '<') {
                if (next == '=') {
                    index += 2;
                    return c + "=";
                }
                index++;
                return String.valueOf(c);
            }
            if ((c == '=' || c == '!') && next == '=') {
                index += 2;
                return c + "=";
            }
            return null;
        }

        Node parseExpression() {
            Node left = parseTerm();
            while (true) {
                skipSpaces();
                if (peek() == '+') {
                    index++;
                    Node right = parseTerm();
                    Node lhs = left;
                    left = vars -> lhs.eval(vars) + right.eval(vars);
                } else if (peek() == '-') {
                    index++;
                    Node right = parseTerm();
                    Node lhs = left;
                    left = vars -> lhs.eval(vars) - right.eval(vars);
                } else {
                    return left;
                }
            }
        }

        Node parseTerm() {
            Node left = parseFactor();
            while (true) {
                skipSpaces();
                if (peek() == '*') {
                    index++;
                    Node right = parseFactor();
                    Node lhs = left;
                    left = vars -> lhs.eval(vars) * right.eval(vars);
                } else if (peek() == '/') {
                    index++;
                    Node right = parseFactor();
                    Node lhs = left;
                    left = vars -> {
                        float divisor = right.eval(vars);
                        return Math.abs(divisor) < 1e-6f ? 0f : lhs.eval(vars) / divisor;
                    };
                } else {
                    return left;
                }
            }
        }

        Node parseFactor() {
            skipSpaces();
            char c = peek();
            if (c == '+') {
                index++;
                return parseFactor();
            }
            if (c == '-') {
                index++;
                Node inner = parseFactor();
                return vars -> -inner.eval(vars);
            }
            if (c == '(') {
                index++;
                Node inner = parseTernary();
                skipSpaces();
                if (peek() != ')') throw new IllegalStateException("缺少右括号");
                index++;
                return inner;
            }
            if (c == '{') {
                return parsePlaceholder();
            }
            return parseNumber();
        }

        Node parsePlaceholder() {
            index++; // {
            int start = index;
            while (!atEnd() && peek() != '}') index++;
            if (atEnd()) throw new IllegalStateException("占位符缺少右花括号");
            String raw = text.substring(start, index).trim();
            index++; // }

            // 占位符可带逗号参数：{combo,time:combo_time}；只取第一段作为变量名
            String[] segments = raw.split(",");
            String head = segments[0].trim();
            int multiplierHundredths = 100;
            int colon = head.indexOf(':');
            if (colon >= 0) {
                try {
                    multiplierHundredths = Math.round(Float.parseFloat(head.substring(colon + 1).trim()) * 100f);
                } catch (NumberFormatException ignored) {
                    multiplierHundredths = 100;
                }
                head = head.substring(0, colon).trim();
            }
            String rawKey = head.toLowerCase(Locale.ROOT);
            // 空占位符 {} 视作本次命中的伤害值，方便写 2*{} 这类表达式
            final String key = rawKey.isEmpty() ? "damage" : rawKey;
            final float multiplier = multiplierHundredths / 100f;
            return vars -> {
                Float value = vars.get(key);
                float result = value == null ? 0f : value;
                return result * multiplier;
            };
        }

        Node parseNumber() {
            int start = index;
            boolean dot = false;
            while (!atEnd()) {
                char c = peek();
                if (c >= '0' && c <= '9') {
                    index++;
                } else if (c == '.' && !dot) {
                    dot = true;
                    index++;
                } else {
                    break;
                }
            }
            if (start == index) throw new IllegalStateException("缺少数字");
            float value = Float.parseFloat(text.substring(start, index));
            return vars -> value;
        }

        void skipSpaces() {
            while (!atEnd() && Character.isWhitespace(peek())) index++;
        }

        boolean atEnd() {
            return index >= text.length();
        }

        char peek() {
            return atEnd() ? '\0' : text.charAt(index);
        }
    }
}
