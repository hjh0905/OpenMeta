package info.openmeta.app.demo.entity;

import info.openmeta.framework.orm.entity.BaseModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.List;

/**
 * EmpInfo Model
 */
@Data
@Schema(name = "EmpInfo")
@EqualsAndHashCode(callSuper = true)
public class EmpInfo extends BaseModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "Name")
    private String name;

    @Schema(description = "Code")
    private String code;

    @Schema(description = "Email")
    private String email;

    @Schema(description = "Department")
    private Long deptId;

    @Schema(description = "Projects Involved")
    private List<Long> projectIds;

    @Schema(description = "Description")
    private String description;

    @Schema(description = "TenantID")
    private Long tenantId;

    @Schema(description = "Disabled")
    private Boolean disabled;
}