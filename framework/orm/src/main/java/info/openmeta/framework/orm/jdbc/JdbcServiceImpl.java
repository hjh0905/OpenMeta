package info.openmeta.framework.orm.jdbc;

import com.google.common.collect.Sets;
import info.openmeta.framework.base.exception.VersionException;
import info.openmeta.framework.base.utils.Cast;
import info.openmeta.framework.orm.annotation.SkipPermissionCheck;
import info.openmeta.framework.orm.changelog.ChangeLogPublisher;
import info.openmeta.framework.orm.constant.ModelConstant;
import info.openmeta.framework.orm.domain.Filters;
import info.openmeta.framework.orm.domain.FlexQuery;
import info.openmeta.framework.orm.domain.Page;
import info.openmeta.framework.orm.enums.ConvertType;
import info.openmeta.framework.orm.enums.IdStrategy;
import info.openmeta.framework.orm.jdbc.database.SqlBuilderFactory;
import info.openmeta.framework.orm.jdbc.database.SqlParams;
import info.openmeta.framework.orm.jdbc.database.StaticSqlBuilder;
import info.openmeta.framework.orm.jdbc.pipeline.DataCreatePipeline;
import info.openmeta.framework.orm.jdbc.pipeline.DataReadPipeline;
import info.openmeta.framework.orm.jdbc.pipeline.DataUpdatePipeline;
import info.openmeta.framework.orm.meta.ModelManager;
import info.openmeta.framework.orm.utils.BeanTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * JdbcServiceImpl
 * Don't check permissions at this layer, the upper layer has already checked permissions.
 *
 * @param <K> Primary key type
 */
@Slf4j
@Service
public class JdbcServiceImpl<K extends Serializable> implements JdbcService<K> {

    @Autowired
    private JdbcProxy jdbcProxy;

    @Autowired
    private ChangeLogPublisher changeLogPublisher;

    /**
     * Extracts all values of the List<Map> structure in the order of Fields and returns an array list.
     *
     * @param fields Field name list
     * @param rows Data list
     * @return List of field value arrays
     */
    private List<Object[]> getBatchValues(List<String> fields, List<Map<String, Object>> rows) {
        // The list of field value arrays
        List<Object[]> batchValues = new ArrayList<>(rows.size());
        for (Map<String, Object> value : rows) {
            Object[] values = new Object[fields.size()];
            for (int i = 0; i < fields.size(); i++) {
                values[i] = value.get(fields.get(i));
            }
            batchValues.add(values);
        }
        return batchValues;
    }

    /**
     * Batch create data and return the result.
     *
     * @param modelName Model name
     * @param rows List of data to be created
     * @return List<Map> results
     */
    @SkipPermissionCheck
    public List<Map<String, Object>> insertList(String modelName, List<Map<String, Object>> rows) {
        LocalDateTime insertTime = LocalDateTime.now();
        DataCreatePipeline pipeline = new DataCreatePipeline(modelName);
        // Format the data, fill in the audit fields,
        // and fill in the tenantId field according to whether it is a multi-tenant model.
        rows = pipeline.processCreateData(rows, insertTime);
        List<String> fields = new ArrayList<>(pipeline.getStoredFields());
        // Get SQL
        SqlParams sqlParams = StaticSqlBuilder.getInsertSql(modelName, fields);
        IdStrategy idStrategy = ModelManager.getIdStrategy(modelName);
        if (IdStrategy.DB_AUTO_ID.equals(idStrategy)) {
            // When using database auto-increment id, the JDBCTemplate needs to be created one by one
            rows.forEach(row -> {
                List<Object> valueList = fields.stream().map(row::get).collect(Collectors.toList());
                sqlParams.setArgs(valueList);
                Long id = jdbcProxy.insert(sqlParams);
                row.put(ModelConstant.ID, id);
            });
        } else {
            // When the idStrategy is not DB_AUTO_ID, the id in the data has been assigned a value by default,
            // and the insert can be batch executed without the database returning ids
            List<Object[]> batchValues = getBatchValues(fields, rows);
            jdbcProxy.batchUpdate(sqlParams, batchValues);
        }
        // Collect changeLogs after filling in the id
        changeLogPublisher.publishCreationLog(modelName, rows, insertTime);
        // Because its need to get the ids first, and finally process the OneToMany, ManyToMany parameters,
        // to create associated table or intermediate table rows.
        pipeline.processXToManyData(rows);
        return rows;
    }

    /**
     * Read multiple rows by ids.
     * If the fields is not specified, all accessible fields as the default.
     *
     * @param modelName Model name
     * @param ids    List of data ids
     * @param fields Field list
     * @param convertType Data convert type
     * @return List<Map> of multiple data
     */
    @SkipPermissionCheck
    public List<Map<String, Object>> selectByIds(String modelName, List<K> ids, List<String> fields, ConvertType convertType) {
        String primaryKey = ModelManager.getModelPrimaryKey(modelName);
        if (CollectionUtils.isEmpty(fields)) {
            fields = ModelManager.getModelStoredFields(modelName);
        } else if (!fields.contains(primaryKey)) {
            fields.add(primaryKey);
        }
        SqlParams sqlParams = StaticSqlBuilder.getSelectSql(modelName, fields, ids);
        List<Map<String, Object>> rows = jdbcProxy.queryForList(sqlParams);
        // Whether to format the data, including data decryption, convert data, and fill in relation fields
        if (!ConvertType.NONE.equals(convertType)) {
            FlexQuery flexQuery = new FlexQuery(fields);
            flexQuery.setConvertType(convertType);
            DataReadPipeline dataPipeline = new DataReadPipeline(modelName, flexQuery);
            dataPipeline.processReadData(rows);
        }
        return rows;
    }

    /**
     * Read multiple rows by flexQuery object.
     *
     * @param modelName Model name
     * @param flexQuery flexQuery
     * @return List<Map> of multiple data
     */
    @SkipPermissionCheck
    public List<Map<String, Object>> selectByFilter(String modelName, FlexQuery flexQuery) {
        SqlParams sqlParams = SqlBuilderFactory.buildSelectSql(modelName, flexQuery);
        List<Map<String, Object>> rows = jdbcProxy.queryForList(sqlParams);
        if (CollectionUtils.isEmpty(rows)) {
            return Collections.emptyList();
        }
        if (!ConvertType.NONE.equals(flexQuery.getConvertType())) {
            DataReadPipeline dataPipeline = new DataReadPipeline(modelName, flexQuery);
            dataPipeline.processReadData(rows);
        }
        return rows;
    }

    /**
     * Get the ids by flexQuery object.
     *
     * @param modelName Model name
     * @param fieldName Default to get the ids of the current model,
     *                  or specify a ManyToOne or OneToOne fieldName to get the ids of this field.
     * @param flexQuery flexQuery
     * @return List<K>
     */
    @SkipPermissionCheck
    public List<K> getIds(String modelName, String fieldName, FlexQuery flexQuery) {
        flexQuery.setFields(Sets.newHashSet(fieldName));
        SqlParams sqlParams = SqlBuilderFactory.buildSelectSql(modelName, flexQuery);
        List<Map<String, Object>> rows = jdbcProxy.queryForList(sqlParams);
        return Cast.of(rows.stream().map(row -> row.get(fieldName)).collect(Collectors.toList()));
    }

    /**
     * Determine if the id physically exists, without permission check.
     *
     * @param modelName Model name
     * @param id Data id
     * @return true or false
     */
    public boolean exist(String modelName, Serializable id) {
        Long count = this.count(modelName, new FlexQuery(Filters.eq(ModelConstant.ID, id)));
        return count != null && count > 0;
    }

    /**
     * Query data based on FlexQuery with pagination.
     *
     * @param modelName Model name
     * @param flexQuery flexQuery
     * @return Page<Map> of multiple data
     */
    @SkipPermissionCheck
    public Page<Map<String, Object>> selectByPage(String modelName, FlexQuery flexQuery, Page<Map<String, Object>> page) {
        if (page.isCount()) {
            Long total = this.count(modelName, flexQuery);
            if (total == 0) {
                return page;
            }
            page.setTotal(total);
        }
        SqlParams sqlParams = SqlBuilderFactory.buildPageSql(modelName, flexQuery, page);
        List<Map<String, Object>> rows = jdbcProxy.queryForList(sqlParams);
        // Whether to format the data, including data decryption, convert data, and fill in relation fields
        if (!ConvertType.NONE.equals(flexQuery.getConvertType())) {
            DataReadPipeline dataPipeline = new DataReadPipeline(modelName, flexQuery);
            dataPipeline.processReadData(rows);
        }
        return page.setRows(rows);
    }

    /**
     * Update one row by id, not including data conversion, fill in audit fields, and send changelog.
     * Only for limited use.
     *
     * @param modelName Model name
     * @param rowMap Update data
     * @return Number of affected rows
     */
    public Integer updateOne(String modelName, Map<String, Object> rowMap) {
        SqlParams sqlParams = StaticSqlBuilder.getUpdateSql(modelName, rowMap);
        if (ModelManager.isVersionControl(modelName) && rowMap.containsKey(ModelConstant.VERSION)) {
            // Check the optimistic lock version control
            Object nextVersion = rowMap.get(ModelConstant.VERSION);
            Integer oldVersion = (Integer) nextVersion - 1;
            sqlParams.setSql(sqlParams.getSql() + " AND version = ?");
            sqlParams.addArgValue(oldVersion);
            int result = jdbcProxy.update(sqlParams);
            if (result == 0) {
                throw new VersionException("""
                        Data version does not match, may have been modified, please refresh and try again!
                        Provided value: {0}, database value: {1}, the two are not equal.""", oldVersion, nextVersion);
            }
            return result;
        } else {
            return jdbcProxy.update(sqlParams);
        }
    }

    /**
     * Batch update data by id, support different keys in different Map.
     *
     * @param modelName Model name
     * @param rows List of data to be updated
     * @return Number of affected rows
     */
    @SkipPermissionCheck
    public Integer updateList(String modelName, List<Map<String, Object>> rows, Set<String> toUpdateFields) {
        LocalDateTime updatedTime = LocalDateTime.now();
        DataUpdatePipeline pipeline = new DataUpdatePipeline(modelName, toUpdateFields);
        // TODO: Process according to the `enableChangeLog` config, referring the TODO in ChangeLogPublisher.class
        //  if enableChangeLog = false, there is no need to get original data, compare differences and collect changeLogs.
        Map<Serializable, Map<String, Object>> originalRowsMap = this.getOriginalRowMap(modelName, rows, pipeline.getDifferFields());
        // Get the list of changed data, keeping only the fields and row data that have changed.
        List<Map<String, Object>> differRows = pipeline.processUpdateData(rows, originalRowsMap, updatedTime);
        if (differRows.isEmpty()) {
            return 0;
        }
        Integer count = differRows.stream().mapToInt(row -> updateOne(modelName, row)).sum();
        // After updating the main table, update the sub-table to avoid the sub-table cascade field being the old value.
        pipeline.processXToManyData(rows);
        // Collect changeLogs
        changeLogPublisher.publishUpdateLog(modelName, differRows, originalRowsMap, updatedTime);
        return count;
    }

    /**
     * Get the original data value.
     *
     * @param modelName Model name
     * @param rows Data list
     * @param differFields Fields that have changed
     * @return Map of original data
     */
    private Map<Serializable, Map<String, Object>> getOriginalRowMap(String modelName, List<Map<String, Object>> rows, Set<String> differFields) {
        // TODO: Extract to the upper layer, perform permission check in this method, and query the database one less time.
        // Get the original value
        String primaryKey = ModelManager.getModelPrimaryKey(modelName);
        List<K> pKeys = Cast.of(rows.stream().map(row -> row.get(primaryKey)).collect(Collectors.toList()));
        List<String> readFields = new ArrayList<>(differFields);
        List<Map<String, Object>> originalRows = this.selectByIds(modelName, pKeys, readFields, ConvertType.NONE);
        // Get the original data map with the primary key as the key
        return originalRows.stream().collect(Collectors.toMap(map -> (Serializable) map.get(primaryKey), Function.identity()));
    }

    /**
     * Delete a slice of timeline model by `sliceId`.
     *
     * @param modelName Model name
     * @param sliceId Slice id of timeline model
     * @return true / Exception
     */
    public boolean deleteBySliceId(String modelName, Serializable sliceId) {
        // Get the original data before deletion for collecting changeLogs
        FlexQuery flexQuery = new FlexQuery(Filters.eq(ModelConstant.SLICE_ID, sliceId)).acrossTimelineData();
        flexQuery.setConvertType(ConvertType.NONE);
        List<Map<String, Object>> originalRows = this.selectByFilter(modelName, flexQuery);
        // Physical deletion of timeline slice
        SqlParams sqlParams = StaticSqlBuilder.getDeleteTimelineSliceSql(modelName, sliceId);
        boolean result = jdbcProxy.update(sqlParams) > 0;
        // Collect changeLogs, changeLogs are bound to the database primary key
        changeLogPublisher.publishDeletionLog(modelName, originalRows, LocalDateTime.now());
        return result;
    }

    /**
     * Delete multiple rows by ids.
     *
     * @param modelName Model name
     * @param ids Data ids
     * @param deletableRows Deleted data rows, used to send changelog
     * @return true / Exception
     */
    public boolean deleteByIds(String modelName, List<K> ids, List<Map<String, Object>> deletableRows) {
        int count;
        LocalDateTime deleteTime = LocalDateTime.now();
        if (ModelManager.isSoftDeleted(modelName)) {
            // Soft delete data, assemble the update data list according to the ids list, and fill in the audit fields.
            List<Map<String, Object>> rows = ids.stream().map(id -> {
                Map<String, Object> row = new HashMap<>();
                row.put(ModelConstant.ID, id);
                row.put(ModelConstant.SOFT_DELETED_FIELD, true);
                return row;
            }).collect(Collectors.toList());
            AutofillFields.fillAuditFieldsForUpdate(rows, deleteTime);
            count = rows.stream().mapToInt(row -> updateOne(modelName, row)).sum();
        } else {
            // Delete data physically
            SqlParams sqlParams = StaticSqlBuilder.getDeleteSql(modelName, ids);
            count = jdbcProxy.update(sqlParams);
        }
        // Collect changeLogs
        changeLogPublisher.publishDeletionLog(modelName, deletableRows, deleteTime);
        return count > 0;
    }

    /**
     * Count by flexQuery object.
     *
     * @param modelName Model name
     * @param flexQuery flexQuery
     * @return count result
     */
    @SkipPermissionCheck
    public Long count(String modelName, FlexQuery flexQuery) {
        SqlParams sqlParams = SqlBuilderFactory.buildCountSql(modelName, flexQuery);
        return (Long) jdbcProxy.queryForObject(sqlParams, Long.class);
    }

    /**
     * Query data by class object, not using the metadata of modelManager.
     *
     * @param entityClass Entity class
     * @param orderBy Order by
     * @return Object list
     */
    public <T> List<T> selectMetaEntityList(Class<T> entityClass, String orderBy) {
        // TODO: Change to page query
        String modelName = entityClass.getSimpleName();
        SqlParams sqlParams = StaticSqlBuilder.getSelectAllMetaSql(modelName, orderBy);
        List<Map<String, Object>> rows = jdbcProxy.queryForList(sqlParams);
        // Convert the original value of the database to an object
        return BeanTool.originalMapListToObjects(rows, entityClass, false);
    }

    /**
     * Query data by model and class object, not using the metadata of modelManager.
     *
     * @param model Model name
     * @param entityClass Entity class
     * @param orderBy Order by
     * @return Object list
     */
    public <T> List<T> selectMetaEntityList(String model, Class<T> entityClass, String orderBy) {
        SqlParams sqlParams = StaticSqlBuilder.getSelectAllMetaSql(model, orderBy);
        List<Map<String, Object>> rows = jdbcProxy.queryForList(sqlParams);
        // Convert the original value of the database to an object
        return BeanTool.originalMapListToObjects(rows, entityClass, true);
    }
}
