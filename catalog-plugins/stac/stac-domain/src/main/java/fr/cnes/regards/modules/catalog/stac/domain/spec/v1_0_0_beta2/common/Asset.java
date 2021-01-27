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

package fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common;

import io.vavr.collection.Set;
import lombok.Value;
import lombok.With;

import java.net.URL;
import java.time.OffsetDateTime;

/**
 * An asset is an object that contains a link to data associated with the Item that can be downloaded or streamed.
 * It is allowed to add additional fields.
 *
 * @see <a href="https://github.com/radiantearth/stac-spec/blob/v1.0.0-beta.2/item-spec/item-spec.md#asset-object">description</a>
 * @see <a href="https://github.com/radiantearth/stac-spec/blob/v1.0.0-beta.2/item-spec/json-schema/item.json#L183">json schema</a>
 */
@Value @With
public class Asset {

    URL href;
    String title;
    String description;

    /**
     * Common STAC media types.
     *
     * @see <a href="https://github.com/radiantearth/stac-spec/blob/v1.0.0-beta.2/item-spec/item-spec.md#media-types"></a>
     */
    interface MediaType {
        String IMAGE_TIFF_GEOTIFF = "image/tiff; application=geotiff";
        String IMAGE_TIFF_GEOTIFF_CLOUD_OPTIMIZED = "image/tiff; application=geotiff; profile=cloud-optimized";
        String IMAGE_JPEG2000 = "image/jp2";
        String IMAGE_PNG = "image/png";
        String IMAGE_JPEG = "image/jpeg";
        String TEXT_XML = "text/xml";
        String APPLICATION_JSON = "application/json";
        String TEXT_PLAIN = "text/plain";
        String APPLICATION_GEOJSON = "application/geo+json";
        String APPLICATION_XHDF5 = "application/x-hdf5";
        String APPLICATION_XHDF = "application/x-hdf";
    }
    /** Media type of the asset */
    String type;

    /**
     * Like the Link rel field, the roles field can be given any value, however here are a few standardized role names.
     *
     * @see <a href="https://github.com/radiantearth/stac-spec/blob/v1.0.0-beta.2/item-spec/item-spec.md#asset-role-types">description</a>
     */
    public interface Roles {
        String THUMBNAIL = "thumbnail";
        String OVERVIEW = "overview";
        String DATA = "data";
        String METADATA = "metadata";
    }
    Set<String> roles;

    OffsetDateTime datetime;
    OffsetDateTime created;
    OffsetDateTime updated;


}
