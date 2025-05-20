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
import fr.cnes.regards.framework.geojson.geometry.IGeometry;
import fr.cnes.regards.modules.catalog.stac.domain.StacConstants;
import fr.cnes.regards.modules.catalog.stac.domain.common.LinkCollection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.common.Asset;
import fr.cnes.regards.modules.catalog.stac.domain.spec.common.Link;
import fr.cnes.regards.modules.catalog.stac.domain.spec.geo.BBox;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import lombok.Value;
import lombok.With;

/**
 * Items are represented in JSON format and are very flexible.
 * Any JSON object that contains all the required fields is a valid STAC Item.
 *
 * @see <a href="https://github.com/radiantearth/stac-spec/blob/master/item-spec/item-spec.md">Description</a>
 * @see <a href="https://schemas.stacspec.org/v1.1.0/item-spec/json-schema/item.json">json schema</a>
 */
@Value
@With
public class Item implements LinkCollection<Item> {

    /**
     * Required type : {@link STACType#FEATURE} but may be null if fields extension excludes it.
     */
    STACType type;

    /**
     * Required STAC version {@link StacConstants#STAC_SPEC_VERSION} but may be null if fields extension excludes it.
     */
    @SerializedName("stac_version")
    String stacVersion;

    /**
     * Optional STAC extensions identifiers the item implements
     */
    @SerializedName("stac_extensions")
    Set<String> stacExtensions;

    /**
     * Required identifier
     */
    String id;

    /**
     * Required geometry (null at least if no geometry is provided)
     */
    IGeometry geometry;

    /**
     * Required if geometry is not null, prohibited otherwise
     */
    BBox bbox;

    /**
     * Required properties
     */
    Map<String, Object> properties;

    /**
     * Required links
     */
    List<Link> links;

    /**
     * Required assets
     */
    Map<String, Asset> assets;

    /**
     * Required collection if a link with rel=collection is provided, not allowed otherwise
     */
    String collection;
}
