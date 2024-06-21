package info.openmeta.framework.orm.jdbc.pipeline.processor;

import com.google.common.collect.Sets;
import info.openmeta.framework.base.constant.StringConstant;
import info.openmeta.framework.base.enums.AccessType;
import info.openmeta.framework.base.enums.Operator;
import info.openmeta.framework.base.exception.IllegalArgumentException;
import info.openmeta.framework.base.utils.Cast;
import info.openmeta.framework.orm.constant.ModelConstant;
import info.openmeta.framework.orm.domain.Filters;
import info.openmeta.framework.orm.domain.FlexQuery;
import info.openmeta.framework.orm.domain.SubQuery;
import info.openmeta.framework.orm.enums.ConvertType;
import info.openmeta.framework.orm.enums.FieldType;
import info.openmeta.framework.orm.meta.MetaField;
import info.openmeta.framework.orm.meta.ModelManager;
import info.openmeta.framework.orm.utils.IdUtils;
import info.openmeta.framework.orm.utils.ReflectTool;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * OneToMany field processor.
 */
@Slf4j
public class OneToManyProcessor extends BaseProcessor {

    private final FlexQuery flexQuery;
    private SubQuery subQuery;

    /**
     * Constructor of the OneToMany field processor object.
     *
     * @param metaField Field metadata object
     * @param flexQuery flexQuery object
     */
    public OneToManyProcessor(MetaField metaField, FlexQuery flexQuery) {
        super(metaField);
        this.flexQuery = flexQuery;
        if (flexQuery != null) {
            this.subQuery = flexQuery.extractSubQuery(metaField.getFieldName());
        }
    }

    /**
     * Batch processing of OneToMany input data
     *
     * @param rows Data list
     * @param accessType Access type, such as CREATE, UPDATE
     */
    @Override
    public void batchProcessInputRows(List<Map<String, Object>> rows, AccessType accessType) {
        if (AccessType.CREATE.equals(accessType)) {
            createWithRelatedRows(rows);
        } else if (AccessType.UPDATE.equals(accessType)) {
            updateWithRelatedRows(rows);
        }
    }

    /**
     * Batch create related model rows.
     *
     * @param rows Data list
     */
    private void createWithRelatedRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> createRows = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Serializable id = (Serializable) row.get(ModelConstant.ID);
            Object value = row.get(fieldName);
            if (value instanceof List<?> valueList && !valueList.isEmpty()) {
                List<Map<String, Object>> subRows;
                try {
                    subRows = Cast.of(value);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Failed to cast {0}:{1} value to List<Map<String, Object>>: {2}",
                            metaField.getModelName(), fieldName, valueList, e);
                }
                subRows.forEach(subRow -> subRow.put(metaField.getRelatedField(), id));
                createRows.addAll(subRows);
            }
        }
        ReflectTool.createList(metaField.getRelatedModel(), createRows);
    }

    /**
     * Batch update related model rows.
     * Query the existing related model rows, extract the createRows, updateRows, and deleteIds.
     *
     * @param rows Data list
     */
    private void updateWithRelatedRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> createRows = new ArrayList<>();
        List<Map<String, Object>> updateRows = new ArrayList<>();
        List<Serializable> deleteIds = new ArrayList<>();
        // {mainModelId: Set<relatedModelIds>}: get the existing OneToMany ids mapping.
        Map<Serializable, Set<Serializable>> existOToMIdsMap = this.getExistOneToManyMap(rows);
        for (Map<String, Object> row : rows) {
            Serializable id = (Serializable) row.get(ModelConstant.ID);
            Object value = row.get(fieldName);
            if (value instanceof List<?> valueList) {
                if (valueList.isEmpty() && existOToMIdsMap.containsKey(id)) {
                    // Delete related model rows when set OneToMany field to empty list.
                    deleteIds.addAll(existOToMIdsMap.get(id));
                } else {
                    List<Map<String, Object>> subRows;
                    try {
                        subRows = Cast.of(value);
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Failed to cast {0}:{1} value to List<Map<String, Object>>: {2}",
                                modelName, fieldName, valueList, e);
                    }
                    if (existOToMIdsMap.containsKey(id)) {
                        // Process related model rows, when update the OneToMany field from old value to a new list.
                        processOneToManyUpdate(id, subRows, existOToMIdsMap, createRows, updateRows, deleteIds);
                    } else {
                        // Create related model rows, when update the OneToMany field from empty to a new list.
                        subRows.forEach(subRow -> subRow.put(metaField.getRelatedField(), id));
                        createRows.addAll(subRows);
                    }
                }
            }
        }
        ReflectTool.createList(metaField.getRelatedModel(), createRows);
        ReflectTool.updateList(metaField.getRelatedModel(), updateRows);
        ReflectTool.deleteList(metaField.getRelatedModel(), deleteIds);
    }

    /**
     * Query the existing OneToMany rows and groupBy the main model id.
     *
     * @param rows Data list
     * @return {mainModelId: Set<relatedModelId>}
     */
    private Map<Serializable, Set<Serializable>> getExistOneToManyMap(Collection<Map<String, Object>> rows) {
        List<Serializable> ids = rows.stream().map(r -> (Serializable) r.get(ModelConstant.ID)).collect(Collectors.toList());
        Set<String> fields = Sets.newHashSet(ModelConstant.ID, metaField.getRelatedField());
        FlexQuery previousFlexQuery = new FlexQuery(fields, Filters.of(metaField.getRelatedField(), Operator.IN, ids));
        List<Map<String, Object>> previousOToMRows = ReflectTool.searchMapList(metaField.getRelatedModel(), previousFlexQuery);
        return previousOToMRows.stream().collect(Collectors.groupingBy(
                row -> (Serializable) row.get(metaField.getRelatedField()),
                Collectors.mapping(row -> (Serializable) row.get(ModelConstant.ID), Collectors.toSet())
        ));
    }

    /**
     * Update createRows, updateRows, and deleteIds when updating the OneToMany field.
     *
     * @param id mainModelId
     * @param subRows related model rows
     * @param existOToMIdsMap existing OneToMany ids mapping
     * @param createRows created rows of related model
     * @param updateRows updated rows of related model
     * @param deleteIds deleted ids of related models
     */
    private void processOneToManyUpdate(Serializable id, List<Map<String, Object>> subRows, Map<Serializable, Set<Serializable>> existOToMIdsMap,
                                        List<Map<String, Object>> createRows, List<Map<String, Object>> updateRows, List<Serializable> deleteIds) {
        Set<Serializable> currentSubIds = new HashSet<>();
        Set<Serializable> previousSubIds = existOToMIdsMap.get(id);
        subRows.forEach(subRow -> {
            Serializable subId = (Serializable) subRow.get(ModelConstant.ID);
            subId = IdUtils.convertIdToLong(subId);
            if (previousSubIds.contains(subId)) {
                currentSubIds.add(subId);
                updateRows.add(subRow);
            } else {
                subRow.put(metaField.getRelatedField(), id);
                createRows.add(subRow);
            }
        });
        Set<Serializable> deletedSubIds = new HashSet<>(previousSubIds);
        deletedSubIds.removeAll(currentSubIds);
        deleteIds.addAll(deletedSubIds);
    }

    /**
     * Batch processing of OneToMany field outputting values
     *
     * @param rows Data list
     */
    @Override
    public void batchProcessOutputRows(List<Map<String, Object>> rows) {
        List<Serializable> ids = rows.stream().map(row -> (Serializable) row.get(ModelConstant.ID)).collect(Collectors.toList());
        List<Map<String, Object>> relatedModelRows = getRelatedModelRows(ids);
        // {mainModelId: List<relatedModelRow>}, grouped related model rows by main model id
        Map<Serializable, List<Map<String, Object>>> groupedValues = groupByMainModelId(relatedModelRows);
        MetaField relatedField = ModelManager.getModelField(metaField.getRelatedModel(), metaField.getRelatedField());
        rows.forEach(row -> {
            List<Map<String, Object>> subRows = groupedValues.get((Serializable) row.get(ModelConstant.ID));
            if (CollectionUtils.isEmpty(subRows)) {
                subRows = new ArrayList<>(0);
            } else if (FieldType.TO_ONE_TYPES.contains(relatedField.getFieldType())
                    && ConvertType.EXPAND_TYPES.contains(flexQuery.getConvertType())) {
                // When the `relatedField` defined in related model is ManyToOne field, fill in the displayName of it.
                fillManyToOneDisplayName(relatedField, row, subRows);
            }
            row.put(fieldName, subRows);
        });
    }

    /**
     * When the `relatedField` defined in related model is ManyToOne field, it does not need to
     * query the main model again to get the displayNames, fill in the displayName using the main model rows directly.
     *
     * @param manyToOneField ManyToOne field object corresponding to the OneToMany field
     * @param row Main model row
     * @param subRows OneToMany field, multiple related model rows corresponding to the single main model row
     */
    private void fillManyToOneDisplayName(MetaField manyToOneField, Map<String, Object> row, List<Map<String, Object>> subRows) {
        // Filter out null or empty strings of displayNames
        List<Object> displayValues =  ModelManager.getFieldDisplayName(manyToOneField).stream().map(row::get).filter(v -> v != null && v != "").collect(Collectors.toList());
        String displayName = StringUtils.join(displayValues, StringConstant.DISPLAY_NAME_SEPARATOR);
        if (ConvertType.DISPLAY.equals(flexQuery.getConvertType())) {
            subRows.forEach(r -> r.put(metaField.getRelatedField(), displayName));
        } else if (ConvertType.KEY_AND_DISPLAY.equals(flexQuery.getConvertType())){
            subRows.forEach(r -> r.put(metaField.getRelatedField(), Arrays.asList(r.get(metaField.getRelatedField()), displayName)));
        }
    }

    /**
     * Query the related model rows by the main model ids
     *
     * @param mainModelIds main model ids
     * @return related model rows
     */
    private List<Map<String, Object>> getRelatedModelRows(List<Serializable> mainModelIds) {
        Filters filters = Filters.of(metaField.getRelatedField(), Operator.IN, mainModelIds);
        FlexQuery relatedFlexQuery;
        if (subQuery != null) {
            if (!CollectionUtils.isEmpty(subQuery.getFields()) && !subQuery.getFields().contains(metaField.getRelatedField())) {
                subQuery.getFields().add(metaField.getRelatedField());
            }
            // When there is a subQuery filters, merge them with `AND` logic
            filters.and(subQuery.getFilters());
            relatedFlexQuery = new FlexQuery(subQuery.getFields(), filters, subQuery.getOrders());
        } else {
            relatedFlexQuery = new FlexQuery(Collections.emptyList(), filters);
        }
        relatedFlexQuery.setConvertType(flexQuery.getConvertType());
        // When get the related model rows of OneToMany field, the `relatedField` field of the related model is only
        // needed to obtain the ID for GroupBy, which might be a ManyToOne field defined in the related model.
        relatedFlexQuery.setKeepIdField(metaField.getRelatedField());
        return ReflectTool.searchMapList(metaField.getRelatedModel(), relatedFlexQuery);
    }

    /**
     * Group the related model rows by main model id.
     * The `relatedField` attribute of the OneToMany field is a field of the related model,
     * which stores the ID of the main model.
     *
     * @param relatedRows Related model rows
     * @return {mainModelId: List<relatedModelRow>}, grouped related model rows
     */
    private Map<Serializable, List<Map<String, Object>>> groupByMainModelId(List<Map<String, Object>> relatedRows) {
        String relatedField = metaField.getRelatedField();
        return relatedRows.stream().collect(Collectors.groupingBy(row -> (Serializable) row.get(relatedField)));
    }
}
