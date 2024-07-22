package info.openmeta.framework.orm.constant;

import com.google.common.collect.ImmutableSet;

import java.time.LocalDate;
import java.util.Set;

/**
 * Model constant
 */
public interface ModelConstant {

    /** Model attribute constants */
    String ID = "id";
    String UUID = "uuid";
    String CODE = "code";
    // Reversed field: When performing a soft deletion, the field being operated on is set to disabled=true.
    String SOFT_DELETED_FIELD = "disabled";
    // Reserved field: The display name composed of multiple fields, suitable for displaying the value of ManyToOne, OneToOne, OneToMany, ManyToMany.
    String DISPLAY_NAME = "displayName";
    // Reserved field: Multiple fields composed of OR search conditions, suitable for multiple fields of ManyToOne, OneToOne, etc. single search box for multi-field based search.
    String SEARCH_NAME = "searchName";
    // Reserved field: Tenant ID identifier
    String TENANT_ID = "tenantId";
    // Reserved field: Version number identifier, used for optimistic lock control
    String VERSION = "version";
    Integer DEFAULT_VERSION = 1;

    // Default order rule: ID ascending order.
    String DEFAULT_PAGED_ORDER = "id ASC";

    /** Audit fields, which are also reserved fields. */
    String CREATED_TIME = "createdTime";
    String CREATED_ID = "createdId";
    String CREATED_BY = "createdBy";
    String UPDATED_TIME = "updatedTime";
    String UPDATED_ID = "updatedId";
    String UPDATED_BY = "updatedBy";
    Set<String> AUDIT_FIELDS = Set.of(CREATED_TIME, CREATED_ID, CREATED_BY, UPDATED_TIME, UPDATED_ID, UPDATED_BY);
    Set<String> AUDIT_UPDATE_FIELDS = Set.of(UPDATED_TIME, UPDATED_ID, UPDATED_BY);

    // Virtual fields: Fields that are not stored in the database, but are generated by the system.
    Set<String> VIRTUAL_FIELDS = Set.of("count");

    /** Fields of timeline model */
    // Timeline model physical primary key field name, UPDATE timeline row basis.
    String SLICE_ID = "sliceId";
    String EFFECTIVE_START = "effectiveStart";
    String EFFECTIVE_END = "effectiveEnd";
    Set<String> PRIMARY_KEYS = Set.of(ID, SLICE_ID);
    Set<String> TIMELINE_FIELDS = Set.of(ID, SLICE_ID, EFFECTIVE_START, EFFECTIVE_END);
    // Default effective end date: 9999-12-31
    LocalDate MAX_EFFECTIVE_END = LocalDate.of(9999, 12, 31);
    // Column names in database
    String SLICE_ID_COLUMN = "slice_id";
    String EFFECTIVE_START_COLUMN = "effective_start";
    String EFFECTIVE_END_COLUMN = "effective_end";

    Set<String> CLIENT_READONLY_FIELDS = Set.of(CREATED_TIME, CREATED_ID, CREATED_BY, UPDATED_TIME, UPDATED_ID, UPDATED_BY,
            VERSION, SLICE_ID, TENANT_ID);

    /** Reserved field names, cannot be used as custom field names */
    Set<String> RESERVED_KEYWORD = ImmutableSet.<String>builder()
            .add(ID)
            .add(SOFT_DELETED_FIELD)
            .add(DISPLAY_NAME)
            .add(SEARCH_NAME)
            .add(TENANT_ID)
            .add(VERSION)
            .addAll(AUDIT_FIELDS)
            .addAll(VIRTUAL_FIELDS)
            .addAll(TIMELINE_FIELDS)
            .build();
}
