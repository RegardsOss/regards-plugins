/*
 * Copyright 2017-2022 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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

package fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1;

import com.google.gson.annotations.SerializedName;
import fr.cnes.regards.modules.catalog.stac.domain.StacSpecConstants;
import fr.cnes.regards.modules.catalog.stac.domain.common.LinkCollection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Item;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import lombok.Value;
import lombok.With;

/**
 * This class describes search results, and is not part of the STAC JSON spec, but
 * (since the 1.0.0.beta-1 version) of the STAC API spec.
 *
 * @see <a href="https://api.stacspec.org/v1.0.0-beta.1/item-search/#operation/getItemSearch">definition</a>
 */
@Value @With
public class ItemCollectionResponse implements LinkCollection<ItemCollectionResponse> {

    @SerializedName("stac_version")
    String stacVersion = StacSpecConstants.Version.STAC_API_VERSION;
    @SerializedName("stac_extensions")
    Set<String> stacExtensions;

    enum TypeEnum {
        @SerializedName("FeatureCollection")
        FEATURE_COLLECTION
    }
    TypeEnum type = TypeEnum.FEATURE_COLLECTION;

    List<Item> features;
    List<Link> links;

    Context context;

}
