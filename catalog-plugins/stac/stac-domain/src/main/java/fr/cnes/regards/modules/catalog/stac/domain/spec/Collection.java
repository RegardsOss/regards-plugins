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

package fr.cnes.regards.modules.catalog.stac.domain.spec;

import com.google.gson.annotations.SerializedName;
import fr.cnes.regards.modules.catalog.stac.domain.api.Context;
import fr.cnes.regards.modules.catalog.stac.domain.common.LinkCollection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.collection.Extent;
import fr.cnes.regards.modules.catalog.stac.domain.spec.common.Asset;
import fr.cnes.regards.modules.catalog.stac.domain.spec.common.Link;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import lombok.Value;
import lombok.With;

/**
 * The STAC Collection specification is a set of JSON fields to describe a set of Items in a STAC Catalog,
 * to help enable discovery.
 * It builds on the Catalog Spec, using the flexible structure specified there to further define
 * and explain logical groups of Items.
 * It shares the same fields and therefore every Collection is also a valid Catalog
 * - the JSON structure extends the core Catalog definition.
 * Collections can have both parent Catalogs and Collections and child Items, Catalogs and Collections.
 *
 * @see <a href="https://github.com/radiantearth/stac-spec/blob/master/collection-spec/collection-spec.md">Description</a>
 * @see <a href="https://schemas.stacspec.org/v1.1.0/collection-spec/json-schema/collection.json">JSON schema</a>
 */
@Value
public class Collection implements LinkCollection<Collection> {

    /**
     * Required type
     */
    STACType type = STACType.COLLECTION;

    /**
     * Required STAC version
     */
    @SerializedName("stac_version")
    String stacVersion;

    /**
     * Optional STAC extensions identifiers the catalog implements
     */
    @SerializedName("stac_extensions")
    Set<String> stacExtensions;

    /**
     * Required catalog identifier
     */
    String id;

    /**
     * Optional catalog title
     */
    String title;

    /**
     * Required multi-line description to fully explain the Catalog.
     */
    String description;

    /**
     * List of keywords describing the collection.
     */
    List<String> keywords;

    /**
     * Required license identifier for the collection.
     */
    String license;

    /**
     * List of providers of the collection.
     */
    Object providers;

    /**
     * Required spatial and temporal extents for the collection.
     */
    Extent extent;

    /**
     * Strongly recommended fields that should be included in the collection.
     */
    Map<String, Object> summaries;

    /**
     * Required link objects
     */
    @With
    List<Link> links;

    /**
     * Dictionary of asset objects that can be downloaded or streamed.
     */
    Map<String, Asset> assets;

    /**
     * Deprecated extension
     */
    Context context;

    /**
     * Number of items that match in the collection
     */
    Long numberMatched;
}
