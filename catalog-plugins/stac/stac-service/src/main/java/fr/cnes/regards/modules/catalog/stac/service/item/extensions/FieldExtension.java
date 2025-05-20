/*
 * Copyright 2017-2024 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
 *
 * This file is part of REGARDS.
 *
 * REGARDS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * REGARDS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with REGARDS. If not, see <http://www.gnu.org/licenses/>.
 */
package fr.cnes.regards.modules.catalog.stac.service.item.extensions;

import fr.cnes.regards.modules.catalog.stac.domain.api.Fields;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Field extensions helper.
 *
 * @author Marc SORDI
 */
public class FieldExtension {

    static final String ITEM_TYPE_FIELD = "type";

    static final String ITEM_STAC_VERSION = "stac_version";

    static final String ITEM_STAC_EXTENSION = "stac_extensions";

    static final String ITEM_ID_FIELD = "id";

    static final String ITEM_GEOMETRY_FIELD = "geometry";

    static final String ITEM_BBOX_FIELD = "bbox";

    static final String ITEM_PROPERTIES_FIELD = "properties";

    static final String ITEM_PROPERTY_PREFIX = ITEM_PROPERTIES_FIELD + ".";

    static final String ITEM_LINKS_FIELD = "links";

    static final String ITEM_ASSETS_FIELD = "assets";

    static final String ITEM_COLLECTION_FIELD = "collection";

    /**
     * Allows to enable/disable this extension
     */
    private final boolean extensionDisabled;

    /**
     * Include standard type field
     */
    private boolean typeIncluded = true;

    /**
     * Include standard STAC version field
     */
    private boolean stacVersionIncluded = true;

    /**
     * Include standard STAC extensions field
     */
    private boolean stacExtensionsIncluded = true;

    /**
     * Include standard id field
     */
    private boolean idIncluded = true;

    /**
     * Include standard geometry field
     */
    private boolean geometryIncluded = true;

    /**
     * Include standard bbox field
     */
    private boolean bboxIncluded = true;

    /**
     * Include standard properties field
     */
    private boolean propertiesIncluded = true;

    /**
     * Include standard links field
     */
    private boolean linksIncluded = true;

    /**
     * Include standard assets field
     */
    private boolean assetsIncluded = true;

    /**
     * Include standard collection field
     */
    private boolean collectionIncluded = true;

    /**
     * STAC properties to include : key = STAC property name, value = true if included, false otherwise
     */
    private Map<String, Boolean> includedStacProperties;

    private FieldExtension(boolean extensionDisabled) {
        // Use builder instead
        this.extensionDisabled = extensionDisabled;
    }

    public static FieldExtension disable() {
        return new FieldExtension(true);
    }

    /**
     * Build a field extensions from search body fields and STAC properties.
     * See
     * <a href="https://github.com/stac-api-extensions/fields?tab=readme-ov-file#null-vs-empty-vs-missing">Reference behavior</a>
     * DEFAULT and ALL are considered equivalent.
     */
    public static FieldExtension build(Fields inputFields, List<StacProperty> properties) {
        // Create a valid not null fields object
        Fields fields = initFields(inputFields);

        FieldExtension extension;
        // If at least one excluded or included field is provided, manage them
        if (!fields.getIncludes().isEmpty() || !fields.getExcludes().isEmpty()) {
            // Initialize extension
            extension = new FieldExtension(false);
            // Manage use cases
            if (fields.getIncludes().isEmpty()) {
                extension.buildWithOnlyExcludes(fields, properties);
            } else if (fields.getExcludes().isEmpty()) {
                extension.buildWithOnlyIncludes(fields, properties);
            } else {
                extension.buildWithBothIncludesAndExcludes(fields, properties);
            }
        } else {
            // Disable extension
            extension = FieldExtension.disable();
        }
        return extension;
    }

    private void buildWithOnlyExcludes(Fields fields, List<StacProperty> properties) {
        // Only excludes are provided
        // - Initialize default STAC properties inclusion
        includeDefaultProperties(true);
        // - Initialize STAC properties inclusion
        includeStacProperties(properties, true);
        // - Exclude provided fields
        excludeAllProperties(fields);
        // > If properties are excluded, all sub properties are also excluded
        // even if not explicitly marked as excluded here.
    }

    private void buildWithOnlyIncludes(Fields fields, List<StacProperty> properties) {
        // Only includes are provided
        // - Initialize default STAC properties inclusion
        includeDefaultProperties(false);
        // - Initialize STAC properties inclusion
        includeStacProperties(properties, false);
        // - Include provided fields
        boolean stacPropertyIncluded = includeAllProperties(fields);
        // - If properties are included, all sub properties are also included
        if (isPropertiesIncluded()) {
            includeStacProperties(properties, true);
        } else {
            // - If at least one property is included, properties are included
            if (stacPropertyIncluded) {
                propertiesIncluded = true;
            }
        }
    }

    private void buildWithBothIncludesAndExcludes(Fields fields, List<StacProperty> properties) {
        // Both includes and excludes are provided
        // - Make fields disjoint
        Fields disjoint = makeFieldsDisjoint(fields);
        // - Initialize default STAC properties inclusion
        includeDefaultProperties(false);
        // - Initialize STAC properties inclusion
        includeStacProperties(properties, false);
        // - Include provided fields
        if (includeAllProperties(disjoint) || isPropertiesIncluded()) {
            // - Manage sub properties
            if (isPropertiesIncluded()) {
                // If properties are included, all sub properties are also included
                includeStacProperties(properties, true);
                // Except if explicitly marked as excluded
                excludeStacProperties(disjoint);
            } else {
                // At least one property is included so properties are included
                propertiesIncluded = true;
            }
        }
    }

    /**
     * Initialize non null fields.
     *
     * @param fields fields
     * @return fields
     */
    private static Fields initFields(Fields fields) {
        if (fields == null) {
            return new Fields(List.of(), List.of());
        } else {
            return new Fields(fields.getIncludes() == null ? List.of() : fields.getIncludes(),
                              fields.getExcludes() == null ? List.of() : fields.getExcludes());
        }
    }

    /**
     * Make fields disjoint.
     *
     * @param fields fields
     */
    private Fields makeFieldsDisjoint(Fields fields) {
        // If a field is both included and excluded, included wins
        List<String> newExcludes = new ArrayList<>(fields.getExcludes());
        newExcludes.removeAll(fields.getIncludes());
        return new Fields(fields.getIncludes(), newExcludes);
    }

    /**
     * Initialize default STAC properties inclusion
     *
     * @param included true if included, false otherwise
     */
    private void includeDefaultProperties(boolean included) {
        this.typeIncluded = included;
        this.stacVersionIncluded = included;
        this.stacExtensionsIncluded = included;
        this.idIncluded = included;
        this.geometryIncluded = included;
        this.bboxIncluded = included;
        this.propertiesIncluded = included;
        this.linksIncluded = included;
        this.assetsIncluded = included;
        this.collectionIncluded = included;
    }

    /**
     * Initialize STAC properties inclusion
     *
     * @param properties STAC properties
     * @param included   true if included, false otherwise
     */
    private void includeStacProperties(List<StacProperty> properties, boolean included) {
        if (properties != null) {
            // Default to include all properties
            this.includedStacProperties = properties.stream()
                                                    .collect(Collectors.toMap(StacProperty::getStacPropertyName,
                                                                              stacProperty -> included));
        }
    }

    private void excludeAllProperties(Fields fields) {
        this.typeIncluded = !fields.getExcludes().contains(ITEM_TYPE_FIELD);
        this.stacVersionIncluded = !fields.getExcludes().contains(ITEM_STAC_VERSION);
        this.stacExtensionsIncluded = !fields.getExcludes().contains(ITEM_STAC_EXTENSION);
        this.idIncluded = !fields.getExcludes().contains(ITEM_ID_FIELD);
        this.geometryIncluded = !fields.getExcludes().contains(ITEM_GEOMETRY_FIELD);
        this.bboxIncluded = !fields.getExcludes().contains(ITEM_BBOX_FIELD);
        this.propertiesIncluded = !fields.getExcludes().contains(ITEM_PROPERTIES_FIELD);
        this.linksIncluded = !fields.getExcludes().contains(ITEM_LINKS_FIELD);
        this.assetsIncluded = !fields.getExcludes().contains(ITEM_ASSETS_FIELD);
        this.collectionIncluded = !fields.getExcludes().contains(ITEM_COLLECTION_FIELD);
        excludeStacProperties(fields);
    }

    /**
     * Exclude sub properties.
     *
     * @param fields fields
     */
    private void excludeStacProperties(Fields fields) {
        // Manage STAC properties
        for (String exclude : fields.getExcludes()) {
            if (exclude.startsWith(ITEM_PROPERTY_PREFIX)) {
                this.includedStacProperties.put(exclude.substring(ITEM_PROPERTY_PREFIX.length()), false);
            }
        }
    }

    /**
     * Include properties.
     *
     * @param fields fields
     * @return true if at least one STAC property is included, false otherwise
     */
    private boolean includeAllProperties(Fields fields) {
        this.typeIncluded = fields.getIncludes().contains(ITEM_TYPE_FIELD);
        this.stacVersionIncluded = fields.getIncludes().contains(ITEM_STAC_VERSION);
        this.stacExtensionsIncluded = fields.getIncludes().contains(ITEM_STAC_EXTENSION);
        this.idIncluded = fields.getIncludes().contains(ITEM_ID_FIELD);
        this.geometryIncluded = fields.getIncludes().contains(ITEM_GEOMETRY_FIELD);
        this.bboxIncluded = fields.getIncludes().contains(ITEM_BBOX_FIELD);
        this.propertiesIncluded = fields.getIncludes().contains(ITEM_PROPERTIES_FIELD);
        this.linksIncluded = fields.getIncludes().contains(ITEM_LINKS_FIELD);
        this.assetsIncluded = fields.getIncludes().contains(ITEM_ASSETS_FIELD);
        this.collectionIncluded = fields.getIncludes().contains(ITEM_COLLECTION_FIELD);
        // Manage STAC properties
        boolean hasPropertyIncluded = false;
        for (String include : fields.getIncludes()) {
            if (include.startsWith(ITEM_PROPERTY_PREFIX)) {
                this.includedStacProperties.put(include.substring(ITEM_PROPERTY_PREFIX.length()), true);
                hasPropertyIncluded = true;
            }
        }
        return hasPropertyIncluded;
    }

    /**
     * Check if STAC type field is included.
     */
    public boolean isTypeIncluded() {
        return extensionDisabled || typeIncluded;
    }

    /**
     * Check if STAC version field is included.
     */
    public boolean isStacVersionIncluded() {
        return extensionDisabled || stacVersionIncluded;
    }

    /**
     * Check if STAC extensions field is included.
     */
    public boolean isStacExtensionsIncluded() {
        return extensionDisabled || stacExtensionsIncluded;
    }

    /**
     * Check if STAC id field is included.
     */
    public boolean isIdIncluded() {
        return extensionDisabled || idIncluded;
    }

    /**
     * Check if STAC geometry field is included.
     */
    public boolean isGeometryIncluded() {
        return extensionDisabled || geometryIncluded;
    }

    /**
     * Check if STAC bbox field is included.
     */
    public boolean isBboxIncluded() {
        return extensionDisabled || bboxIncluded;
    }

    /**
     * Check if STAC properties field is included.
     */
    public boolean isPropertiesIncluded() {
        return extensionDisabled || propertiesIncluded;
    }

    /**
     * Check if STAC links field is included.
     */
    public boolean isLinksIncluded() {
        return extensionDisabled || linksIncluded;
    }

    /**
     * Check if STAC assets field is included.
     */
    public boolean isAssetsIncluded() {
        return extensionDisabled || assetsIncluded;
    }

    /**
     * Check if STAC collection field is included.
     */
    public boolean isCollectionIncluded() {
        return extensionDisabled || collectionIncluded;
    }

    /**
     * Check if a STAC property is included.
     *
     * @param stacPropertyName the STAC property name
     * @return true if included, false otherwise
     */
    public boolean isPropertyIncluded(String stacPropertyName) {
        return extensionDisabled || includedStacProperties == null || includedStacProperties.getOrDefault(
            stacPropertyName,
            true);
    }
}
