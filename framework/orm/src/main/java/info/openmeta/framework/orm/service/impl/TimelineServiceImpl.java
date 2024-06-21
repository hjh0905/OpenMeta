package info.openmeta.framework.orm.service.impl;

import info.openmeta.framework.base.context.ContextHolder;
import info.openmeta.framework.base.enums.Operator;
import info.openmeta.framework.base.utils.Assert;
import info.openmeta.framework.base.utils.DateUtils;
import info.openmeta.framework.orm.constant.ModelConstant;
import info.openmeta.framework.orm.domain.Filters;
import info.openmeta.framework.orm.domain.Orders;
import info.openmeta.framework.orm.domain.FlexQuery;
import info.openmeta.framework.orm.entity.TimelineSlice;
import info.openmeta.framework.orm.jdbc.JdbcService;
import info.openmeta.framework.orm.meta.ModelManager;
import info.openmeta.framework.orm.service.TimelineService;
import info.openmeta.framework.orm.utils.BeanTool;
import info.openmeta.framework.orm.utils.IdUtils;
import info.openmeta.framework.orm.utils.MapUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.*;

/**
 * Timeline Model Service
 * Specific processing methods for the timeline model.
 */
@Slf4j
@Service
public class TimelineServiceImpl<K extends Serializable> implements TimelineService {

    @Autowired
    protected JdbcService<K> jdbcService;

    /**
     * Append `effectiveDate` filters when query the timeline model: `effectiveStart <= effectiveDate <= effectiveEnd`.
     * If the original filters already contain `effectiveStart` or `effectiveEnd`,
     * the `effectiveDate` filters will not be added, which means the query is across time periods.
     *
     * @param modelName model name
     * @param originalFilters original filters
     * @return updated filters
     */
    @Override
    public Filters appendTimelineFilters(String modelName, Filters originalFilters) {
        if (!ModelManager.isTimelineModel(modelName)) {
            return originalFilters;
        }
        LocalDate effectiveDate = ContextHolder.getContext().getEffectiveDate();
        Filters timelineFilters = Filters.of(ModelConstant.EFFECTIVE_START, Operator.LESS_THAN_OR_EQUAL, effectiveDate)
                .and(ModelConstant.EFFECTIVE_END, Operator.GREATER_THAN_OR_EQUAL, effectiveDate);
        if (Filters.isEmpty(originalFilters)) {
            return timelineFilters;
        } else {
            Set<String> filterFields = originalFilters.allFields();
            if (!(filterFields.contains(ModelConstant.EFFECTIVE_START) || filterFields.contains(ModelConstant.EFFECTIVE_END))) {
                return Filters.merge(originalFilters, timelineFilters);
            } else {
                return originalFilters;
            }
        }
    }

    /**
     * Append `effectiveDate` filters when query the timeline model: `effectiveStart <= effectiveDate <= effectiveEnd`.
     * If `isAcrossTimeline = true`, the `originalFilters` will be returned directly,
     * which means the query is across time periods.
     *
     * @param modelName model name
     * @param flexQuery flexQuery object
     * @return updated filters
     */
    @Override
    public Filters appendTimelineFilters(String modelName, FlexQuery flexQuery) {
        Filters originalFilters = flexQuery.getFilters();
        if (!ModelManager.isTimelineModel(modelName) || flexQuery.isAcrossTimeline()) {
            return originalFilters;
        }
        LocalDate effectiveDate = ContextHolder.getContext().getEffectiveDate();
        Filters timelineFilters = Filters.of(ModelConstant.EFFECTIVE_START, Operator.LESS_THAN_OR_EQUAL, effectiveDate)
                .and(ModelConstant.EFFECTIVE_END, Operator.GREATER_THAN_OR_EQUAL, effectiveDate);
        if (Filters.isEmpty(originalFilters)) {
            return timelineFilters;
        } else {
            return Filters.merge(originalFilters, timelineFilters);
        }
    }

    /**
     * Get the TimelineSlice object by `sliceId`.
     *
     * @param modelName model name
     * @param sliceId the sliceId of timeline model
     * @return TimelineSlice object
     */
    @Override
    public TimelineSlice getTimelineSlice(String modelName, Serializable sliceId) {
        Set<String> fields = new HashSet<>(ModelConstant.TIMELINE_FIELDS);
        if (ModelManager.isSoftDeleted(modelName)) {
            fields.add(ModelConstant.SOFT_DELETED_FIELD);
        }
        FlexQuery flexQuery = new FlexQuery(fields, Filters.eq(ModelConstant.SLICE_ID, sliceId)).acrossTimelineData();
        List<Map<String, Object>> rows = jdbcService.selectByFilter(modelName, flexQuery);
        Assert.notEmpty(rows, "Timeline model {0} does not exist data for sliceId={1}.", modelName, sliceId);
        return BeanTool.mapToObject(rows.getFirst(), TimelineSlice.class);
    }

    /**
     * Delete a slice of timeline model by `sliceId`.
     *
     * @param modelName model name
     * @param timelineSlice the slice object of timeline model
     * @return true / Exception
     */
    @Override
    public boolean deleteSlice(String modelName, TimelineSlice timelineSlice) {
        // Update the `effectiveEnd` of the previous slice
        LocalDate previousEnd = DateUtils.dateToLocalDate(timelineSlice.getEffectiveStart()).minusDays(1);
        LocalDate currentEnd = DateUtils.dateToLocalDate(timelineSlice.getEffectiveEnd());
        this.correctPreviousEndDate(modelName, timelineSlice.getId(), previousEnd, currentEnd);
        return jdbcService.deleteBySliceId(modelName, timelineSlice.getSliceId());
    }

    /**
     * Create multiple slices of timeline model, and return the data list with `sliceId`.
     *
     * @param modelName model name
     * @param rows the data list to be created
     * @return the data list with `sliceId`
     */
    @Override
    public List<Map<String, Object>> createSlices(String modelName, List<Map<String, Object>> rows) {
        LocalDate effectiveDate = ContextHolder.getContext().getEffectiveDate();
        rows.forEach(row -> {
            row.putIfAbsent(ModelConstant.EFFECTIVE_START, effectiveDate);
            if (row.get(ModelConstant.ID) != null && jdbcService.exist(modelName, (Serializable) row.get(ModelConstant.ID))) {
                // id already exists, copy adjacent slice to insert a new one.
                createSlice(modelName, row);
            } else {
                // No id is provided, insert the row data as the first slice.
                row.put(ModelConstant.EFFECTIVE_END, ModelConstant.MAX_EFFECTIVE_END);
                jdbcService.insertList(modelName, Collections.singletonList(row));
            }
        });
        return rows;
    }

    /**
     * Create a timeline slice. If the ID exists, copy from adjacent slice.
     *
     * @param modelName model name
     * @param sliceRow the slice data
     */
    private void createSlice(String modelName, Map<String, Object> sliceRow) {
        TimelineSlice currentSlice = BeanTool.mapToObject(sliceRow, TimelineSlice.class);
        // Get the slice that overlaps with the current timeline slice.
        Set<String> copyFields = ModelManager.getModelUpdatableFields(modelName);
        copyFields.removeAll(sliceRow.keySet());
        copyFields.addAll(ModelConstant.TIMELINE_FIELDS);
        Map<String, Object> overlappedRow = this.getOverlappedSlice(modelName, currentSlice, copyFields);
        if (!overlappedRow.isEmpty()) {
            this.createSliceWithOverlapped(modelName, sliceRow, overlappedRow, currentSlice);
        } else {
            // Fetch the next slice as the data source for the new slice.
            Map<String, Object> nextRow = this.getNextSlice(modelName, currentSlice, copyFields);
            this.createSliceWithNext(modelName, sliceRow, nextRow);
        }
    }
    
    /**
     * Create a new slice based on the overlapped slice data.
     * If the `effectiveStart` dates are the same, updates the current slice directly;
     * otherwise, copies the overlapped slice data to create a new timeline slice.
     *
     * @param modelName model name
     * @param sliceRow the row data of the current slice
     * @param overlappedRow the row data of the overlapped slice
     * @param currentSlice the current slice object
     */
    private void createSliceWithOverlapped(String modelName, Map<String, Object> sliceRow, Map<String, Object> overlappedRow, TimelineSlice currentSlice) {
        TimelineSlice overlappedSlice = BeanTool.mapToObject(overlappedRow, TimelineSlice.class);
        if (currentSlice.getEffectiveStart().equals(overlappedSlice.getEffectiveStart())) {
            // When the `effectiveStart` dates are the same, update the existing slice.
            // In integration scenarios where 'sliceId' is not provided, perform a `Correct` operation on the slice
            // by assigning the overlapped `sliceId` to the current data.
            sliceRow.put(ModelConstant.SLICE_ID, overlappedSlice.getSliceId());
            this.updateCurrentSlice(modelName, sliceRow, false);
        } else {
            // Update the `effectiveEnd` of the overlapped record: overlapEnd = currentStart - 1 day
            this.correctSliceEndDate(modelName, overlappedSlice.getSliceId(), currentSlice.getEffectiveStart().minusDays(1));
            // Set currentEnd = overlapEnd
            this.copyFromNearSlice(modelName, sliceRow, overlappedRow, overlappedSlice.getEffectiveEnd());
        }
    }

    /**
     * Creates a new slice based on the next slice.
     * If there is no next slice, inserts it as the first slice.
     *
     * @param modelName the name of the model
     * @param sliceRow the row data of the current slice
     * @param nextRow the row data of the next slice
     */
    private void createSliceWithNext(String modelName, Map<String, Object> sliceRow, Map<String, Object> nextRow) {
        if (!nextRow.isEmpty()) {
            // Set the `effectiveEnd` of the current slice to one day before the `effectiveStart` of the next slice.
            LocalDate currentNewEnd = DateUtils.dateToLocalDate(nextRow.get(ModelConstant.EFFECTIVE_START)).minusDays(1);
            this.copyFromNearSlice(modelName, sliceRow, nextRow, currentNewEnd);
        } else {
            // If there are no overlapping or nex slice, insert current slice as the first slice directly.
            sliceRow.put(ModelConstant.EFFECTIVE_END, ModelConstant.MAX_EFFECTIVE_END);
            jdbcService.insertList(modelName, Collections.singletonList(sliceRow));
        }
    }

    /**
     * Copies field values from an adjacent slice for insertion.
     *
     * @param modelName the name of the model
     * @param sliceRow the row data of the current slice
     * @param nearRow the row data of the adjacent timeline slice
     * @param currentNewEnd the new `effectiveEnd` for the current slice
     */
    private void copyFromNearSlice(String modelName, Map<String, Object> sliceRow, Map<String, Object> nearRow, LocalDate currentNewEnd) {
        sliceRow.put(ModelConstant.EFFECTIVE_END, currentNewEnd);
        nearRow.remove(ModelConstant.SLICE_ID);
        nearRow.forEach((k, v) -> {
            if (!sliceRow.containsKey(k)) {
                sliceRow.put(k, v);
            }
        });
        jdbcService.insertList(modelName, Collections.singletonList(sliceRow));
    }

    /**
     * Batch update slices by `sliceId` in the list.
     * When updating `effectiveStart`, also check and update the `effectiveEnd` of the affected slices.
     *
     * @param modelName model name
     * @param rows data list to be updated
     * @return the number of rows updated
     */
    @Override
    public Integer updateSlices(String modelName, List<Map<String, Object>> rows) {
        rows.forEach(sliceRow -> {
            Assert.notTrue(sliceRow.containsKey(ModelConstant.SOFT_DELETED_FIELD),
                    "When update slice of timeline model {0}, cannot assign a value to `disabled` field! {1}",
                    modelName, sliceRow);
            // When sliceId is of Integer type, convert it to Long type.
            sliceRow.put(ModelConstant.SLICE_ID, IdUtils.convertIdToLong(sliceRow.get(ModelConstant.SLICE_ID)));
            // When `effectiveStart` changes, update the `effectiveEnd` of the affected slice.
            if (sliceRow.containsKey(ModelConstant.EFFECTIVE_START)) {
                Object effectiveStart = sliceRow.get(ModelConstant.EFFECTIVE_START);
                Assert.notTrue(effectiveStart == null || effectiveStart.equals(""),
                        "`effectiveStart` field of timeline model {0} cannot be set to empty! {1}", modelName, sliceRow);
                this.updateSliceAndCorrectDate(modelName, sliceRow);
            } else {
                this.updateCurrentSlice(modelName, sliceRow, false);
            }
        });
        return rows.size();
    }

    /**
     * Update the timeline slice and correct the dates for adjacent slices.
     *
     * @param modelName the name of the model
     * @param sliceRow the slice data to be updated
     */
    private void updateSliceAndCorrectDate(String modelName, Map<String, Object> sliceRow) {
        TimelineSlice currentSlice = BeanTool.mapToObject(sliceRow, TimelineSlice.class);
        TimelineSlice originalSlice = this.getTimelineSlice(modelName, currentSlice.getSliceId());
        Map<String, Object> overlappedRow = this.getOverlappedSlice(modelName, currentSlice, ModelConstant.TIMELINE_FIELDS);
        if (!overlappedRow.isEmpty()) {
            // Update the `effectiveEnd` of the current slice based on the overlapped slice.
            TimelineSlice overlappedSlice = BeanTool.mapToObject(overlappedRow, TimelineSlice.class);
            this.updateSliceByOverlapped(modelName, sliceRow, currentSlice, originalSlice, overlappedSlice);
        } else {
            // Update the `effectiveEnd` of the current slice based on the next slice.
            this.updateSliceByNext(modelName, sliceRow, currentSlice, originalSlice);
        }
    }

    /**
     * Update the `effectiveEnd` based on overlap:
     * - When the current slice overlaps with itself, update the `effectiveEnd` of the previous slice.
     * - When the current slice overlaps with the previous slice, update the `effectiveEnd` of the previous slice.
     * - When the current slice overlaps with another slice, update the `effectiveEnd` of
     *   the current slice, the overlapped slice, and the previous slice.
     *
     * @param modelName the name of the model
     * @param sliceRow the slice data to be updated
     * @param currentSlice the current slice object
     * @param originalSlice the original slice object
     * @param overlappedSlice the overlapped slice object
     */
    private void updateSliceByOverlapped(String modelName, Map<String, Object> sliceRow, TimelineSlice currentSlice, TimelineSlice originalSlice, TimelineSlice overlappedSlice) {
        boolean updateEndDate = false;
        if (currentSlice.getSliceId().equals(overlappedSlice.getSliceId())) {
            // The current slice overlaps with itself
            if (!currentSlice.getEffectiveStart().equals(overlappedSlice.getEffectiveStart())) {
                // When `effectiveStart` changes, correct the `effectiveEnd` of previous slice: previousEnd = newStart - 1
                this.correctPreviousEndDate(modelName, currentSlice.getId(), overlappedSlice.getEffectiveStart().minusDays(1), currentSlice.getEffectiveStart().minusDays(1));
            }
        } else {
            Assert.notTrue(currentSlice.getEffectiveStart().equals(overlappedSlice.getEffectiveStart()), """
                            Timeline model {0} already has a slice with the same effective start date {1}
                            but a different sliceId, if you do not keep the history, please delete it first!
                            """, modelName, currentSlice.getEffectiveStart());
            if (overlappedSlice.getEffectiveEnd().equals(originalSlice.getEffectiveStart().minusDays(1))) {
                // Current slice overlaps with the previous slice, update the `effectiveEnd` of the previous slice: previousEnd = newStart -1
                this.correctSliceEndDate(modelName, overlappedSlice.getSliceId(), currentSlice.getEffectiveStart().minusDays(1));
            } else {
                // If the overlapped slice is neither the current nor the previous slice, update the `effectiveEnd` of the overlapped slice.
                this.correctSliceEndDate(modelName, overlappedSlice.getSliceId(), currentSlice.getEffectiveStart().minusDays(1));
                // Update the `effectiveEnd` of the slice preceding the original slice: previousEnd = oldEnd WHERE previousEnd = oldStart -1
                this.correctPreviousEndDate(modelName, currentSlice.getId(), originalSlice.getEffectiveStart().minusDays(1), originalSlice.getEffectiveEnd());
                // Set the `effectiveEnd` of current slice: newEnd = overlapEnd
                updateEndDate = true;
                sliceRow.put(ModelConstant.EFFECTIVE_END, overlappedSlice.getEffectiveEnd());
            }
        }
        this.updateCurrentSlice(modelName, sliceRow, updateEndDate);
    }

    /**
     * Set the `effectiveEnd` of the current slice when there is no overlapping slice but the next slice exists.
     * If no next slice exists, it indicates that only the current slice is present, and update it directly.
     *
     * @param modelName the name of the model
     * @param sliceRow the slice data to be updated
     * @param currentSlice the current slice object
     * @param originalSlice the original slice object
     */
    private void updateSliceByNext(String modelName, Map<String, Object> sliceRow, TimelineSlice currentSlice, TimelineSlice originalSlice) {
        boolean updateEndDate = false;
        Map<String, Object> nextRow = this.getNextSlice(modelName, currentSlice, ModelConstant.TIMELINE_FIELDS);
        if (!nextRow.isEmpty()) {
            LocalDate nextStartDate = DateUtils.dateToLocalDate(nextRow.get(ModelConstant.EFFECTIVE_START));
            // Update the `effectiveEnd` of the slice preceding the original slice: previousEnd = oldEnd WHERE previousEnd = oldStart -1
            this.correctPreviousEndDate(modelName, currentSlice.getId(), originalSlice.getEffectiveStart().minusDays(1), originalSlice.getEffectiveEnd());
            // Exclude the scenario where the current slice remains the first slice before and after changes, updating the current slice's `effectiveEnd`.
            if (!originalSlice.getEffectiveEnd().equals(nextStartDate.minusDays(1))) {
                // Set the 'effectiveEnd' of the current slice: newEnd = nextStart
                updateEndDate = true;
                sliceRow.put(ModelConstant.EFFECTIVE_END, nextStartDate.minusDays(1));
            }
        }
        this.updateCurrentSlice(modelName, sliceRow, updateEndDate);
    }

    /**
     * Update the slice data.
     *
     * @param modelName model name
     * @param sliceRow the slice data to be updated
     */
    private void updateCurrentSlice(String modelName, Map<String, Object> sliceRow, boolean updateEndDate) {
        Set<String> toUpdateFields = new HashSet<>(ModelManager.getModelUpdatableFields(modelName));
        toUpdateFields.retainAll(sliceRow.keySet());
        if (!updateEndDate) {
            // `effectiveEnd` is automatically computed and cannot be assigned externally.
            toUpdateFields.remove(ModelConstant.EFFECTIVE_END);
        }
        jdbcService.updateList(modelName, Collections.singletonList(sliceRow), toUpdateFields);
    }

    /**
     * Get the slice that overlaps with the specified slice.
     *
     * @param modelName the name of the model
     * @param currentSlice the current slice object
     * @param fields the fields to read
     * @return a map of the overlapped slice data, or an empty map if no overlap is found
     */
    private Map<String, Object> getOverlappedSlice(String modelName, TimelineSlice currentSlice, Set<String> fields) {
        LocalDate currentStart = currentSlice.getEffectiveStart();
        Filters overlapFilters = Filters.eq(ModelConstant.ID, currentSlice.getId()).andLe(ModelConstant.EFFECTIVE_START, currentStart).andGe(ModelConstant.EFFECTIVE_END, currentStart);
        FlexQuery flexQuery = new FlexQuery(fields, overlapFilters).acrossTimelineData();
        List<Map<String, Object>> overlappedRows = jdbcService.selectByFilter(modelName, flexQuery);
        if (CollectionUtils.isEmpty(overlappedRows)) {
            return Collections.emptyMap();
        } else {
            return overlappedRows.getFirst();
        }
    }

    /**
     * Get the next slice when there is no overlapping slice.
     * @param modelName the name of the model
     * @param currentSlice the current slice object
     * @param fields the fields to read
     * @return a map of the next slice data, or an empty map if no next slice is found
     */
    private Map<String, Object> getNextSlice(String modelName, TimelineSlice currentSlice, Set<String> fields) {
        LocalDate currentStart = currentSlice.getEffectiveStart();
        Filters nextFilters = Filters.eq(ModelConstant.ID, currentSlice.getId()).andGt(ModelConstant.EFFECTIVE_START, currentStart);
        if (currentSlice.getSliceId() != null) {
            nextFilters.andNe(ModelConstant.SLICE_ID, currentSlice.getSliceId());
        }
        FlexQuery flexQuery = new FlexQuery(fields, nextFilters).acrossTimelineData();
        flexQuery.setOrders(Orders.ofAsc(ModelConstant.EFFECTIVE_START));
        flexQuery.setLimitSize(1);
        List<Map<String, Object>> nextRows = jdbcService.selectByFilter(modelName, flexQuery);
        if (CollectionUtils.isEmpty(nextRows)) {
            return Collections.emptyMap();
        } else {
            return nextRows.getFirst();
        }
    }

    /**
     * Correct the `effectiveEnd` of the specified `sliceId`.
     *
     * @param modelName the name of the model
     * @param pKey sliceId of the timeline data
     * @param effectiveEnd the new `effectiveEnd` date
     */
    private void correctSliceEndDate(String modelName, Serializable pKey, LocalDate effectiveEnd) {
        Map<String, Object> value = MapUtils.<String, Object>builder()
                .put(ModelConstant.EFFECTIVE_END, effectiveEnd)
                .put(ModelConstant.SLICE_ID, pKey)
                .build();
        jdbcService.updateOne(modelName, value);
    }

    /**
     * Search for a slice by the `effectiveEnd` and business ID, and update the `effectiveEnd` to a new value:
     *      SET previousEnd = {currentEnd} WHERE previousEnd = {currentStart -1} AND id = {id}
     * @param modelName the name of the model
     * @param id the business ID of the timeline slice
     * @param oldEndDate the old `effectiveEnd`
     * @param newEndDate the new `effectiveEnd`
     */
    private void correctPreviousEndDate(String modelName, Serializable id, LocalDate oldEndDate, LocalDate newEndDate) {
        Filters filters = Filters.eq(ModelConstant.ID, id).andEq(ModelConstant.EFFECTIVE_END, oldEndDate);
        List<K> pks = jdbcService.getIds(modelName, ModelConstant.SLICE_ID, new FlexQuery(filters));
        if (!pks.isEmpty()) {
            correctSliceEndDate(modelName, pks.getFirst(), newEndDate);
        }
    }

}
