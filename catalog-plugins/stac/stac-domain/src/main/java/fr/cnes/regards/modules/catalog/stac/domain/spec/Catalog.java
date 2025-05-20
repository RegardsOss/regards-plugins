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
import fr.cnes.regards.modules.catalog.stac.domain.common.LinkCollection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.common.Link;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import lombok.Value;
import lombok.With;

/**
 * Catalogs are not intended to be queried.
 * Their purpose is discovery: to be browsed by people and crawled by machines to build a search index.
 * A Catalog can be represented in JSON format.
 * Any JSON object that contains all the required fields is a valid STAC Catalog.
 *
 * @see <a href="https://github.com/radiantearth/stac-spec/blob/master/catalog-spec/catalog-spec.md">Description</a>
 * @see <a href="https://schemas.stacspec.org/v1.1.0/catalog-spec/json-schema/catalog.json">json schema</a>
 */
@Value
@With
public class Catalog implements LinkCollection<Catalog> {

    /**
     * Required type
     */
    STACType type = STACType.CATALOG;

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
     * Required link objects
     */
    List<Link> links;

    /**
     * This conformsTo field must only be used in landing page.
     */
    java.util.List<String> conformsTo;
}
