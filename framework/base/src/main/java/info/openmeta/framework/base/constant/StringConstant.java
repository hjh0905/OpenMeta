package info.openmeta.framework.base.constant;

public interface StringConstant {
    String EMPTY_STRING = "";
    String DISPLAY_NAME_SEPARATOR = " / ";
    String COMMA_SEPARATOR = ",";
    String ALIAS_SEPARATOR = ".";
    String LEFT_SQUARE_BRACKET = "[";
    String RIGHT_SQUARE_BRACKET = "]";

    // The masking character for masking field.
    String MASKING_SYMBOL = "****";

    // The prefix of reserved field name.
    String RESERVED_PREFIX = "@{";

    // The prefix of variable
    String VARIABLE_PREFIX = "#{";

    // the prefix of formula
    String FORMULA_PREFIX = "${";
}
