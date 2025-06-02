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
 * along with REGARDS. If not, see `<http://www.gnu.org/licenses/>`.
 */
package fr.cnes.regards.modules.catalog.stac.domain.spec;

import com.google.gson.Gson;
import fr.cnes.regards.framework.geojson.geometry.IGeometry;
import fr.cnes.regards.modules.catalog.stac.domain.DomainGsonAwareTest;
import fr.cnes.regards.modules.catalog.stac.domain.spec.geo.BBox;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import net.javacrumbs.jsonunit.assertj.JsonAssert;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.json;

/**
 * Test for serialization of specific attributes in an Item that need a special serialization.
 *
 * @author Julien Canches
 */
public class ItemSpecialAttributesTest implements DomainGsonAwareTest {

    private static final IGeometry GEOMETRY_REGULAR = IGeometry.simplePolygon(0.495917, 13.052768, 0.495917, 14.681994);

    private static final BBox BBOX_REGULAR = new BBox(0.495917, 13.052768, 3.109653, 15.117576);

    protected final Gson gson = gson();

    /**
     * Verify that an item with a regular geometry is correctly serialized
     */
    @Test
    public void geometryRegularSerialization() {
        Item item = new Item(STACType.FEATURE,
                             "1.0.0",
                             HashSet.empty(),
                             "feature-id",
                             GEOMETRY_REGULAR,
                             BBOX_REGULAR,
                             HashMap.of("prop1", "value1", "prop2", "value2"),
                             List.empty(),
                             HashMap.empty(),
                             "theCollection");
        assertThatItem(item).isObject().containsEntry("geometry", json("""
                                                                           {"coordinates":[[[0.495917, 13.052768], [0.495917, 14.681994], [0.495917, 13.052768]]],"type":"Polygon"}"""));
    }

    /**
     * Verify that an item with an "unlocated" geometry is correctly serialized (geometry attribute
     * should be present with null value).
     */
    @Test
    public void geometryUnlocatedSerialization() {
        Item item = new Item(STACType.FEATURE,
                             "1.0.0",
                             HashSet.empty(),
                             "feature-id",
                             IGeometry.unlocated(),
                             null,
                             HashMap.of("prop1", "value1", "prop2", "value2"),
                             List.empty(),
                             HashMap.empty(),
                             "theCollection");
        assertThatItem(item).isObject().containsEntry("geometry", null);
    }

    /**
     * Verify that an item with a null geometry (produced when the field geometry was excluded) is correctly serialized
     * (geometry attribute should be omitted).
     */
    @Test
    public void geometryFilteredSerialization() {
        Item item = new Item(STACType.FEATURE,
                             "1.0.0",
                             HashSet.empty(),
                             "feature-id",
                             null,
                             BBOX_REGULAR,
                             HashMap.of("prop1", "value1", "prop2", "value2"),
                             List.empty(),
                             HashMap.empty(),
                             "theCollection");
        assertThatItem(item).isObject().doesNotContainKey("geometry");
    }

    /**
     * Verify that an item with a regular bbox is correctly serialized.
     */
    @Test
    public void bboxRegularSerialization() {
        Item item = new Item(STACType.FEATURE,
                             "1.0.0",
                             HashSet.empty(),
                             "feature-id",
                             GEOMETRY_REGULAR,
                             BBOX_REGULAR,
                             HashMap.of("prop1", "value1", "prop2", "value2"),
                             List.empty(),
                             HashMap.empty(),
                             "theCollection");
        assertThatItem(item).isObject().containsEntry("bbox", json("""
                                                                       [0.495917, 13.052768, 3.109653, 15.117576]"""));
    }

    /**
     * Verify that an item with a null bbox (either because geometry is unlocated or because the field bbox is
     * excluded) is correctly serialized (i.e. bbox should be omitted is the json object).
     */
    @Test
    public void bboxNullSerialization() {
        Item item = new Item(STACType.FEATURE,
                             "1.0.0",
                             HashSet.empty(),
                             "feature-id",
                             GEOMETRY_REGULAR,
                             null,
                             HashMap.of("prop1", "value1", "prop2", "value2"),
                             List.empty(),
                             HashMap.empty(),
                             "theCollection");
        assertThatItem(item).isObject().doesNotContainKey("bbox");
    }

    /**
     * Check the serialization of an item 1/ as a standalone object, 2/ within an ItemCollectionResponse.
     */
    protected JsonAssert assertThatItem(Item item) {
        return assertThatJson(gson.toJson(item));
    }

}
