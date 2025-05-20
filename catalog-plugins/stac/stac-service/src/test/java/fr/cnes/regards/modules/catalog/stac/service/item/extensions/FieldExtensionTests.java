/*
 * Copyright 2017-2025 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

/**
 * Field extension helper tests
 *
 * @author Marc SORDI
 */
class FieldExtensionTests {

    /**
     * Empty fields
     */
    @Test
    void testEmptyFields() {
        // Given
        // Fields are null
        String eoCloudCover = "eo:cloud_cover";
        List<StacProperty> stacProperties = List.of(buildStacProperty(eoCloudCover));

        // When
        FieldExtension fieldExtension = FieldExtension.build(null, stacProperties);

        // Then
        Assertions.assertTrue(fieldExtension.isTypeIncluded());
        Assertions.assertTrue(fieldExtension.isStacVersionIncluded());
        Assertions.assertTrue(fieldExtension.isStacExtensionsIncluded());
        Assertions.assertTrue(fieldExtension.isIdIncluded());
        Assertions.assertTrue(fieldExtension.isGeometryIncluded());
        Assertions.assertTrue(fieldExtension.isBboxIncluded());
        Assertions.assertTrue(fieldExtension.isPropertiesIncluded());
        Assertions.assertTrue(fieldExtension.isLinksIncluded());
        Assertions.assertTrue(fieldExtension.isAssetsIncluded());
        Assertions.assertTrue(fieldExtension.isCollectionIncluded());
        Assertions.assertTrue(fieldExtension.isPropertyIncluded(eoCloudCover));
    }

    /**
     * Only excludes fields
     */
    @Test
    void testOnlyExcludesFields() {
        // Given
        Fields fields = new Fields(Collections.emptyList(), List.of(FieldExtension.ITEM_GEOMETRY_FIELD));
        List<StacProperty> stacProperties = List.of(buildStacProperty("property"));

        // When
        FieldExtension fieldExtension = FieldExtension.build(fields, stacProperties);

        // Then
        Assertions.assertTrue(fieldExtension.isTypeIncluded());
        Assertions.assertTrue(fieldExtension.isStacVersionIncluded());
        Assertions.assertTrue(fieldExtension.isStacExtensionsIncluded());
        Assertions.assertTrue(fieldExtension.isIdIncluded());
        Assertions.assertFalse(fieldExtension.isGeometryIncluded());
        Assertions.assertTrue(fieldExtension.isBboxIncluded());
        Assertions.assertTrue(fieldExtension.isPropertiesIncluded());
        Assertions.assertTrue(fieldExtension.isLinksIncluded());
        Assertions.assertTrue(fieldExtension.isAssetsIncluded());
        Assertions.assertTrue(fieldExtension.isCollectionIncluded());
    }

    /**
     * Only includes fields
     */
    @Test
    void testOnlyIncludesFields() {
        // Given
        Fields fields = new Fields(List.of(FieldExtension.ITEM_ID_FIELD, FieldExtension.ITEM_TYPE_FIELD),
                                   Collections.emptyList());
        List<StacProperty> stacProperties = List.of(buildStacProperty("property"));

        // When
        FieldExtension fieldExtension = FieldExtension.build(fields, stacProperties);

        // Then
        Assertions.assertTrue(fieldExtension.isTypeIncluded());
        Assertions.assertFalse(fieldExtension.isStacVersionIncluded());
        Assertions.assertFalse(fieldExtension.isStacExtensionsIncluded());
        Assertions.assertTrue(fieldExtension.isIdIncluded());
        Assertions.assertFalse(fieldExtension.isGeometryIncluded());
        Assertions.assertFalse(fieldExtension.isBboxIncluded());
        Assertions.assertFalse(fieldExtension.isPropertiesIncluded());
        Assertions.assertFalse(fieldExtension.isLinksIncluded());
        Assertions.assertFalse(fieldExtension.isAssetsIncluded());
        Assertions.assertFalse(fieldExtension.isCollectionIncluded());
    }

    /**
     * Both includes and excludes fields
     */
    @Test
    void testBothIncludesAndExcludesFields() {
        // Given
        Fields fields = new Fields(List.of(FieldExtension.ITEM_ID_FIELD, FieldExtension.ITEM_TYPE_FIELD),
                                   List.of(FieldExtension.ITEM_GEOMETRY_FIELD));
        List<StacProperty> stacProperties = List.of(buildStacProperty("property"));

        // When
        FieldExtension fieldExtension = FieldExtension.build(fields, stacProperties);

        // Then
        Assertions.assertTrue(fieldExtension.isTypeIncluded());
        Assertions.assertFalse(fieldExtension.isStacVersionIncluded());
        Assertions.assertFalse(fieldExtension.isStacExtensionsIncluded());
        Assertions.assertTrue(fieldExtension.isIdIncluded());
        Assertions.assertFalse(fieldExtension.isGeometryIncluded());
        Assertions.assertFalse(fieldExtension.isBboxIncluded());
        Assertions.assertFalse(fieldExtension.isPropertiesIncluded());
        Assertions.assertFalse(fieldExtension.isLinksIncluded());
        Assertions.assertFalse(fieldExtension.isAssetsIncluded());
        Assertions.assertFalse(fieldExtension.isCollectionIncluded());
    }

    /**
     * Both includes and excludes fields with conflicting properties
     */
    @Test
    void testBothIncludesAndExcludesFieldsWithConflictingProperties() {
        // Given
        Fields fields = new Fields(List.of(FieldExtension.ITEM_ID_FIELD, FieldExtension.ITEM_TYPE_FIELD),
                                   List.of(FieldExtension.ITEM_ID_FIELD));

        // When
        FieldExtension fieldExtension = FieldExtension.build(fields, Collections.emptyList());

        // Then
        Assertions.assertTrue(fieldExtension.isTypeIncluded());
        // Id included because it is both included and excluded so included wins
        Assertions.assertTrue(fieldExtension.isIdIncluded());
    }

    /**
     * Test property inclusion
     */
    @Test
    void testPropertiesExclusionWithInclusion() {
        // Given
        String propertyName = "datetime";
        String mustNotBeIncluded = "mustNotBeIncluded";
        Fields fields = new Fields(List.of(FieldExtension.ITEM_PROPERTY_PREFIX + propertyName),
                                   List.of(FieldExtension.ITEM_PROPERTIES_FIELD));
        List<StacProperty> stacProperties = List.of(buildStacProperty(propertyName),
                                                    buildStacProperty(mustNotBeIncluded));

        // When
        FieldExtension fieldExtension = FieldExtension.build(fields, stacProperties);

        // Then
        // Result must return only the included property
        Assertions.assertTrue(fieldExtension.isPropertiesIncluded());
        Assertions.assertTrue(fieldExtension.isPropertyIncluded(propertyName));
        Assertions.assertFalse(fieldExtension.isPropertyIncluded(mustNotBeIncluded));
    }

    @Test
    void testPropertiesInclusionWithExclusion() {
        // Given
        String propertyName = "datetime";
        String mustNotBeIncluded = "mustNotBeIncluded";
        Fields fields = new Fields(List.of(FieldExtension.ITEM_PROPERTIES_FIELD),
                                   List.of(FieldExtension.ITEM_PROPERTY_PREFIX + mustNotBeIncluded));
        List<StacProperty> stacProperties = List.of(buildStacProperty(propertyName),
                                                    buildStacProperty(mustNotBeIncluded));

        // When
        FieldExtension fieldExtension = FieldExtension.build(fields, stacProperties);

        // Then
        // Result must return only the included property
        Assertions.assertTrue(fieldExtension.isPropertiesIncluded());
        Assertions.assertTrue(fieldExtension.isPropertyIncluded(propertyName));
        Assertions.assertFalse(fieldExtension.isPropertyIncluded(mustNotBeIncluded));
    }

    private StacProperty buildStacProperty(String stacPropertyName) {
        return new StacProperty(null, null, stacPropertyName, null, null, null, null, null, null, null);
    }
}
