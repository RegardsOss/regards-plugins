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

package fr.cnes.regards.modules.catalog.stac.domain.api.extension.searchcol;

import com.google.gson.annotations.SerializedName;
import fr.cnes.regards.modules.catalog.stac.domain.StacConstants;
import fr.cnes.regards.modules.catalog.stac.domain.api.Context;
import fr.cnes.regards.modules.catalog.stac.domain.spec.Collection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.common.Link;
import io.vavr.collection.List;
import io.vavr.collection.Set;

/**
 * List of collections, with navigation links.
 *
 * @see <a href="https://github.com/radiantearth/stac-api-spec/tree/release/v1.0.0/ogcapi-features">Definition</a>
 */
@lombok.Value
public class SearchCollectionsResponse {

    @SuppressWarnings({ "unused", "findbugs:SS_SHOULD_BE_STATIC" }) // field only used for serialization
    @SerializedName("stac_version")
    String stacVersion = StacConstants.STAC_API_VERSION + ".extended";

    @SerializedName("stac_extensions")
    Set<String> stacExtensions;

    List<Collection> collections;

    @lombok.With
    List<Link> links;

    Context context;

    Long numberMatched;

    Long numberReturned;
}
