package info.openmeta.framework.orm.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * ID generation strategy.
 */
@Getter
@AllArgsConstructor
public enum IdStrategy {
    // Database Auto-increment ID: 64-bit numeric ID, as default
    DB_AUTO_ID("DbAutoID", "DB Auto-increment ID"),
    // Unique Lexicographically Sortable Identifier: 128-bit, with 48-bit timestamp and 80-bit random value
    // 26-character string with 10-character timestamp and 16-character random value
    ULID("ULID", "ULID"),
    // Time-Sorted Unique Identifier (Combined SnowflakeID and ULID): 64-bit, with 42-bit timestamp,
    // 10-bit server ID and 12-bit sequence number by default.
    // Numeric TSID
    TSID_LONG("TSIDLong", "Long Time-Sorted ID"),
    // 13-character string TSID
    TSID_STRING("TSIDString", "String Time-Sorted ID"),
    // Standard UUID: 128-bit, 32-character hexadecimal string
    UUID("UUID", "UUID"),
    // External ID: external input ID
    EXTERNAL_ID("ExternalID", "External ID");

    @JsonValue
    private final String type;

    private final String name;

}
