package info.openmeta.framework.orm.service;

import info.openmeta.framework.base.enums.AccessType;
import info.openmeta.framework.orm.domain.Filters;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Permission check service interface
 */
public interface PermissionService {

    /**
     * Check the access permission of multiple models and fields at the same time.
     * Check whether the user has access to the specified {domain: [fields]} model name and its field list.
     *
     * @param model model name
     * @param accessModelFields {modelName: Set(fields)} dictionary structure,
     *                          used to check access permissions when cascading read.
     * @param accessType operation type, default is READ, that is, check whether it has "read operation" permission
     */
    void checkModelCascadeFieldsAccess(String model, Map<String, Set<String>> accessModelFields, AccessType accessType);

    /**
     * Check the ids range and field level operation permission.
     *
     * @param model model name
     * @param ids data ids, check model and field level permissions when empty
     * @param fields field set
     * @param accessType   operation type, default is READ
     */
    void checkIdsFieldsAccess(String model, Collection<? extends Serializable> ids, Set<String> fields, AccessType accessType);

    /**
     * Model permission check
     *
     * @param model model name
     * @param accessType  access type
     */
    void checkModelAccess(String model, AccessType accessType);

    /**
     * Model fields permission check
     *
     * @param model  model name
     * @param fields field set
     * @param accessType   access type
     */
    void checkModelFieldsAccess(String model, Collection<String> fields, AccessType accessType);

    /**
     * Ids data range permission check.
     * When checking the ids operation permission, first query according to the permission,
     * and check whether the ids exist in the database when there is no permission.
     * If the ids exist, report no data access permission,
     * and if the ids do not exist, report that the data to be read does not exist.
     *
     * @param model model name
     * @param ids data ids, check model and field level permissions when empty
     * @param accessType operation type, default is READ
     */
    void checkIdsAccess(String model, Collection<? extends Serializable> ids, AccessType accessType);

    /**
     * Check the route access permission
     *
     * @param route route
     */
    void checkRouteAccess(String route);

    Set<String> getUserBlockedModelFields(String model, AccessType accessType);

    /**
     * Append data permission filters.
     *
     * @param model model name
     * @param originalFilters original filter conditions
     * @return merged filter conditions
     */
    Filters appendScopeAccessFilters(String model, Filters originalFilters);
}