/*
 * Copyright 2017-2020 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
import fr.cnes.regards.framework.geojson.geometry.IGeometry;
import fr.cnes.regards.modules.catalog.stac.domain.StacSpecConstants;
import fr.cnes.regards.modules.catalog.stac.domain.common.LinkCollection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Asset;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.geo.BBox;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.geo.Centroid;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import lombok.Value;
import lombok.With;

/**
 * Items are represented in JSON format and are very flexible.
 * Any JSON object that contains all the required fields is a valid STAC Item.
 *
 * @see <a href="https://github.com/radiantearth/stac-spec/blob/v1.0.0-beta.2/item-spec/item-spec.md">Description</a>
 * @see <a href="https://github.com/radiantearth/stac-spec/blob/v1.0.0-beta.2/item-spec/json-schema/item.json">json schema</a>
 */
@Value @With
public class Item implements LinkCollection<Item> {

    @SerializedName("stac_version")
    String stacVersion = StacSpecConstants.Version.STAC_SPEC_VERSION;

    @SerializedName("stac_extensions")
    Set<String> stacExtensions;

    String id;
    BBox bbox;
    IGeometry geometry;
    Centroid centroid;

    public enum TypeEnum {
        @SerializedName("Feature")
        FEATURE
    }
    TypeEnum type = TypeEnum.FEATURE;

    String collection;

    Map<String, Object> properties;
    List<Link> links;
    Map<String, Asset> assets;

}
