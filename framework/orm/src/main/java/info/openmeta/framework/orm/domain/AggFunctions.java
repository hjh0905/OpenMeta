package info.openmeta.framework.orm.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import info.openmeta.framework.orm.domain.serializer.AggFunctionsDeserializer;
import info.openmeta.framework.orm.enums.AggFunctionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Aggregation functions query object.
 * Support multiple aggregation queries based on different fields, such as [["SUM", "amount"], ["COUNT", "id"]]
 * The field alias of the result uses the camel case string obtained by jointing the function name and the field name,
 * such as `sum + amount -> sumAmount`, then `sumAmount` is the field alias of the aggregation query result.
 * The client can directly specify an alias also, such as ["MAX", "createdTime", "newestTime"].
 */
@Data
@NoArgsConstructor
@Schema(name = "AggFunctions")
@JsonDeserialize(using = AggFunctionsDeserializer.class)
public class AggFunctions {

    private final List<AggFunctionField> functionList = new ArrayList<>(2);

    public static AggFunctions of(AggFunctionType type, String field) {
        AggFunctions aggFunctions = new AggFunctions();
        aggFunctions.add(type, field);
        return aggFunctions;
    }

    public AggFunctions add(AggFunctionType type, String field) {
        this.functionList.add(new AggFunctionField(type, field));
        return this;
    }

    public static boolean isEmpty(AggFunctions aggFunctions) {
        return aggFunctions == null || aggFunctions.getFunctionList().isEmpty();
    }

    public static boolean containAlias(AggFunctions aggFunctions, String alias) {
        return aggFunctions != null && aggFunctions.getFunctionList().stream().anyMatch(aggFunctionField -> aggFunctionField.getAlias().equals(alias));
    }

    @Override
    public String toString() {
        return functionList.stream()
                .map(AggFunctionField::toString)
                .collect(Collectors.joining(", "));
    }
}
