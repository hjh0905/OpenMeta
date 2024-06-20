package info.openmeta.framework.orm.meta;

import info.openmeta.framework.orm.enums.IdStrategy;
import info.openmeta.framework.orm.enums.StorageType;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * MetaModel object
 */
@Data
public class MetaModel implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long appId;

    private String labelName;

    private String modelName;

    private String tableName;

    private StorageType storageType;

    // Model level default orders, such as "name ASC"
    private String defaultOrder;

    // Display name fields
    private List<String> displayName;

    // Search name fields
    private List<String> searchName;

    private String description;

    private Boolean timeline;

    private Boolean softDelete;

    private Boolean versionLock;

    private Boolean multiTenant;

    private IdStrategy idStrategy;

    private List<MetaField> modelFields;

    private String partitionField;
}