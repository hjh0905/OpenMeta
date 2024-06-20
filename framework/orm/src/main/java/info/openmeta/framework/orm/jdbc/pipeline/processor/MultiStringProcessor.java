package info.openmeta.framework.orm.jdbc.pipeline.processor;

import info.openmeta.framework.base.enums.AccessType;
import info.openmeta.framework.base.exception.IllegalArgumentException;
import info.openmeta.framework.orm.meta.MetaField;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Processor for multiple string fields. Such as MultiString and MultiOption fields.
 */
public class MultiStringProcessor extends BaseProcessor {

    public MultiStringProcessor(MetaField metaField) {
        super(metaField);
    }

    /**
     * Check if the required field is set to null or empty.
     */
    @Override
    protected void checkRequired(Map<String, Object> row) {
        if (Boolean.TRUE.equals(metaField.getRequired()) && StringUtils.isBlank((String) row.get(fieldName))) {
            throw new IllegalArgumentException("Model required field {0}:{1} cannot be empty!", metaField.getModelName(), fieldName);
        }
    }

    /**
     * Convert the multiple value to a string for storage.
     *
     * @param row Single-row data to be updated
     * @param accessType Access type
     */
    @Override
    public void processInputRow(Map<String, Object> row, AccessType accessType) {
        checkReadonly(row);
        Object value = row.get(fieldName);
        if (value instanceof List) {
            row.put(fieldName, StringUtils.join((List<?>) value, ","));
        } else if (value instanceof String) {
            row.put(fieldName, value);
        } else if (AccessType.CREATE.equals(accessType) || row.containsKey(fieldName)) {
            checkRequired(row);
            row.put(fieldName, "");
        }
    }

    /**
     * Convert the string value of MultiString or MultiOption field to a list.
     *
     * @param row Single-row output data
     */
    @Override
    public void processOutputRow(Map<String, Object> row) {
        if (!row.containsKey(fieldName)) {
            return;
        }
        Object value = row.get(fieldName);
        value = StringUtils.isBlank((String) value) ?
                getFieldTypeDefaultValue() : Arrays.asList(StringUtils.split((String) value, ","));
        row.put(fieldName, value);
    }

}
