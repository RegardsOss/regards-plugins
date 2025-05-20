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

package fr.cnes.regards.modules.catalog.stac.domain.api;

import com.google.gson.annotations.SerializedName;
import fr.cnes.regards.modules.catalog.stac.domain.StacConstants;
import fr.cnes.regards.modules.catalog.stac.domain.common.LinkCollection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.Item;
import fr.cnes.regards.modules.catalog.stac.domain.spec.common.Link;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import lombok.Value;
import lombok.With;

/**
 * This class describes search results for items.
 *
 * @see <a href="https://github.com/radiantearth/stac-api-spec/tree/release/v1.0.0/item-search">definition</a>
 */
@Value
@With
public class ItemCollectionResponse implements LinkCollection<ItemCollectionResponse> {

    @SerializedName("stac_version")
    String stacVersion = StacConstants.STAC_API_VERSION;

    @SerializedName("stac_extensions")
    Set<String> stacExtensions;

    enum TypeEnum {
        @SerializedName("FeatureCollection") FEATURE_COLLECTION
    }

    TypeEnum type = TypeEnum.FEATURE_COLLECTION;

    List<Item> features;

    List<Link> links;

    /**
     * Deprecated extension,
     * Context information is already within the objects in 1.0.0 version
     */
    Context context;

    Long numberMatched;

    Long numberReturned;
}
