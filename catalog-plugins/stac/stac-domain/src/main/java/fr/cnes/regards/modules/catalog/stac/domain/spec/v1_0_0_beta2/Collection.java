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

package fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2;

import com.google.gson.annotations.SerializedName;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.Context;
import fr.cnes.regards.modules.catalog.stac.domain.common.LinkCollection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection.Extent;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Asset;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link;
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
 * @see <a href="https://github.com/radiantearth/stac-spec/blob/v1.0.0-beta.2/collection-spec/collection-spec.md">Description</a>
 * @see <a href="https://github.com/radiantearth/stac-spec/blob/v1.0.0-beta.2/collection-spec/json-schema/collection.json">JSON schema</a>
 */
@Value
@With
public class Collection implements LinkCollection<Collection> {

    @SerializedName("stac_version")
    String stacVersion;

    @SerializedName("stac_extensions")
    Set<String> stacExtensions;

    String title;

    String id;

    public enum TypeEnum {
        @SerializedName("Collection") COLLECTION
    }

    TypeEnum type = TypeEnum.COLLECTION;

    String description;

    List<Link> links;

    List<String> keywords;

    String license;

    Object providers;

    Extent extent;

    Map<String, Object> summaries;

    Map<String, Asset> assets;

    Context context;
}
