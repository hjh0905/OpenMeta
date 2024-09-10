package info.openmeta.starter.file.excel;

import info.openmeta.framework.orm.domain.Filters;
import info.openmeta.framework.orm.domain.FlexQuery;
import info.openmeta.framework.orm.domain.Orders;
import info.openmeta.framework.orm.meta.ModelManager;
import info.openmeta.framework.orm.utils.ListUtils;
import info.openmeta.framework.web.dto.FileInfo;
import info.openmeta.starter.file.entity.ExportTemplate;
import info.openmeta.starter.file.entity.ExportTemplateField;
import info.openmeta.starter.file.service.ExportTemplateFieldService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Export by template.
 */
@Slf4j
@Component
public class ExportByTemplate extends CommonExport {

    @Autowired
    private ExportTemplateFieldService exportTemplateFieldService;

    /**
     * Export data by exportTemplate configured exported fields.
     * The convertType should be set to DISPLAY to get the display values of the fields.
     * Such as displayName for ManyToOne/OneToOne fields, and itemName for Option fields.
     *
     * @param flexQuery the flex query to be used for data retrieval
     * @param exportTemplate exportTemplate object
     * @return fileInfo object with download URL
     */
    public FileInfo export(FlexQuery flexQuery, ExportTemplate exportTemplate) {
        String modelName = exportTemplate.getModelName();
        // Construct the headers order by sequence of the export fields
        List<String> fieldNames = new ArrayList<>();
        List<List<String>> headerList = new ArrayList<>();
        this.extractFieldsAndLabels(modelName, exportTemplate.getId(), fieldNames, headerList);
        // Get the data to be exported
        flexQuery.setFields(fieldNames);
        List<Map<String, Object>> rows = this.getExportedData(modelName, flexQuery);
        List<List<Object>> rowsTable = ListUtils.convertToTableData(fieldNames, rows);
        // Generate the Excel file
        String fileName = exportTemplate.getFileName();
        String sheetName = StringUtils.hasText(exportTemplate.getSheetName()) ? exportTemplate.getSheetName() : fileName;
        FileInfo fileInfo = this.generateFileAndUpload(fileName, sheetName, headerList, rowsTable);
        // Generate an export history record
        this.generateExportHistory(exportTemplate.getId(), fileInfo.getFileId());
        return fileInfo;
    }

    /**
     * Extract the fields and labels from the export template.
     *
     * @param modelName the model name to be exported
     * @param exportTemplateId the ID of the export template
     * @param fieldNames the list of field names
     * @param headerList the list of header labels
     */
    private void extractFieldsAndLabels(String modelName, Long exportTemplateId,
                                        List<String> fieldNames, List<List<String>> headerList) {
        // Construct the headers order by sequence of the export fields
        Filters filters = Filters.eq(ExportTemplateField::getTemplateId, exportTemplateId);
        Orders orders = Orders.ofAsc(ExportTemplateField::getSequence);
        List<ExportTemplateField> exportFields = exportTemplateFieldService.searchList(new FlexQuery(filters, orders));
        exportFields.forEach(exportField -> {
            fieldNames.add(exportField.getFieldName());
            if (StringUtils.hasText(exportField.getCustomHeader())) {
                headerList.add(Collections.singletonList(exportField.getCustomHeader()));
            } else {
                String labelName = ModelManager.getCascadingFieldLabelName(modelName, exportField.getFieldName());
                headerList.add(Collections.singletonList(labelName));
            }
        });
    }
}
