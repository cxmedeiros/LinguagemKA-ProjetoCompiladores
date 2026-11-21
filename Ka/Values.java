package ka;

/*
 * Regras de valor da Ka, usadas pelo TacInterpreter.
 *
 * Tipos de valor em tempo de execucao:
 *   nil -> null | Boolean | Integer (int) | Double (float) | String
 *   KaObject | KaCallable (KaFunction ou nativa)
 *
 * Regras numericas: int op int -> int (divisao trunca); se algum lado e
 * float, o resultado e float (o int e promovido).
 */
final class Values {
    private Values() { }

    static boolean isTruthy(Object object) {
        if (object == null) return false;
        if (object instanceof Boolean) return (Boolean) object;
        return true;
    }

    static boolean isNumber(Object o) {
        return o instanceof Integer || o instanceof Double;
    }

    // Numeros comparam por valor apos promocao (10 == 10.0 e true), coerente
    // com < e >=. Os demais tipos usam equals (objetos/funcoes: identidade).
    static boolean isEqual(Object a, Object b) {
        if (a == null || b == null) return a == b;
        if (isNumber(a) && isNumber(b)) {
            if (a instanceof Integer && b instanceof Integer) return a.equals(b);
            return toDouble(a) == toDouble(b);
        }
        return a.equals(b);
    }

    private static double toDouble(Object o) {
        if (o instanceof Integer) return ((Integer) o).doubleValue();
        return (Double) o;
    }

    // Double mantem o ".0" (6.0), Integer imprime sem ponto: e assim que
    // o teste 12-int-float distingue float de int.
    static String stringify(Object object) {
        if (object == null) return "nil";
        return object.toString();
    }

    // ===================== Operadores =====================

    static Object unary(Operator.Kind kind, SourceLocation where, Object right) {
        switch (kind) {
            case BANG:
                return !isTruthy(right);
            case MINUS:
                if (!isNumber(right)) throw new RuntimeError(where, "Operando deve ser numero.");
                if (right instanceof Integer) {
                    int n = (Integer) right;
                    if (n == Integer.MIN_VALUE) {
                        throw new RuntimeError(where, "Estouro de inteiro.");
                    }
                    return -n;
                }
                return -(Double) right;
            default:
                return null;
        }
    }

    static Object binary(Operator.Kind kind, SourceLocation where, Object left, Object right) {
        switch (kind) {
            case PLUS:
                if (isNumber(left) && isNumber(right)) return arithmetic(kind, where, left, right);
                if (left instanceof String && right instanceof String) {
                    return (String) left + (String) right;
                }
                throw new RuntimeError(where,
                    "Operandos de '+' devem ser dois numeros ou duas strings.");
            case MINUS:
            case STAR:
            case SLASH:
                checkNumberOperands(where, left, right);
                return arithmetic(kind, where, left, right);
            case GREATER:
                checkNumberOperands(where, left, right);
                return compare(left, right) > 0;
            case GREATER_EQUAL:
                checkNumberOperands(where, left, right);
                return compare(left, right) >= 0;
            case LESS:
                checkNumberOperands(where, left, right);
                return compare(left, right) < 0;
            case LESS_EQUAL:
                checkNumberOperands(where, left, right);
                return compare(left, right) <= 0;
            case BANG_EQUAL:
                return !isEqual(left, right);
            case EQUAL_EQUAL:
                return isEqual(left, right);
            default:
                return null;
        }
    }

    private static void checkNumberOperands(SourceLocation where, Object left, Object right) {
        if (isNumber(left) && isNumber(right)) return;
        throw new RuntimeError(where, "Operandos devem ser numeros.");
    }

    // int op int -> int ; qualquer lado float -> float.
    private static Object arithmetic(Operator.Kind kind, SourceLocation where, Object left, Object right) {
        if (left instanceof Integer && right instanceof Integer) {
            int a = (Integer) left, b = (Integer) right;
            try {
                switch (kind) {
                    case PLUS:  return Math.addExact(a, b);
                    case MINUS: return Math.subtractExact(a, b);
                    case STAR:  return Math.multiplyExact(a, b);
                    case SLASH:
                        if (b == 0) throw new RuntimeError(where, "Divisao inteira por zero.");
                        if (a == Integer.MIN_VALUE && b == -1) {
                            throw new ArithmeticException();
                        }
                        return a / b;   // trunca em direcao a zero
                    default: break;
                }
            } catch (ArithmeticException overflow) {
                throw new RuntimeError(where, "Estouro de inteiro.");
            }
        }

        double a = toDouble(left), b = toDouble(right);
        switch (kind) {
            case PLUS:  return a + b;
            case MINUS: return a - b;
            case STAR:  return a * b;
            case SLASH: return a / b;   // float / 0 = Infinity/NaN, como em Java
            default:    throw new IllegalStateException("operador aritmetico inesperado: " + kind);
        }
    }

    private static int compare(Object left, Object right) {
        if (left instanceof Integer && right instanceof Integer) {
            return Integer.compare((Integer) left, (Integer) right);
        }
        return Double.compare(toDouble(left), toDouble(right));
    }

    // ===================== Tipos (padroes de tipo do match) =====================

    static boolean typeTest(String typeName, Object value) {
        switch (typeName) {
            case "Number":   return isNumber(value);
            case "Int":      return value instanceof Integer;
            case "Float":    return value instanceof Double;
            case "String":   return value instanceof String;
            case "Boolean":  return value instanceof Boolean;
            case "Nil":      return value == null;
            case "Object":   return value instanceof KaObject;
            case "Function": return value instanceof KaCallable;
            default:         return false;
        }
    }

    // ===================== Texto para o --trace =====================

    static String typeOf(Object v) {
        if (v == null) return "nil";
        if (v instanceof Integer) return "int";
        if (v instanceof Double) return "float";
        if (v instanceof String) return "string";
        if (v instanceof Boolean) return "boolean";
        if (v instanceof KaObject) return "object";
        if (v instanceof KaCallable) return "function";
        return v.getClass().getSimpleName();
    }

    // Valor + tipo: 10:int, 10.0:float, "oi":string
    static String show(Object v) {
        String text = v instanceof String ? "\"" + v + "\"" : stringify(v);
        return text + ":" + typeOf(v);
    }
}
