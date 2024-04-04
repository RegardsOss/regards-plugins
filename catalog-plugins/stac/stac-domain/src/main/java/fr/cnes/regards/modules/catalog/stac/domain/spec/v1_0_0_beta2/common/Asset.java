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

package fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common;

import com.google.gson.annotations.JsonAdapter;
import fr.cnes.regards.framework.urn.DataType;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.gson.RoleTypeAdapter;
import io.vavr.collection.Set;
import lombok.Value;
import lombok.With;

import java.net.URI;

/**
 * An asset is an object that contains a link to data associated with the Item that can be downloaded or streamed.
 * It is allowed to add additional fields.
 *
 * @see <a href="https://github.com/radiantearth/stac-spec/blob/v1.0.0-beta.2/item-spec/item-spec.md#asset-object">description</a>
 * @see <a href="https://github.com/radiantearth/stac-spec/blob/v1.0.0-beta.2/item-spec/json-schema/item.json#L183">json schema</a>
 */
@Value
@With
public class Asset {

    URI href;

    String title;

    String description;

    /**
     * Media type of the asset
     */
    String type;

    @JsonAdapter(RoleTypeAdapter.class)
    Set<String> roles;

    /**
     * Common STAC media types.
     *
     * @see <a href="https://github.com/radiantearth/stac-spec/blob/v1.0.0-beta.2/item-spec/item-spec.md#media-types"></a>
     */
    public interface MediaType {

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

        static String fromDataType(DataType dataType) {
            switch (dataType) {
                case QUICKLOOK_HD:
                case QUICKLOOK_MD:
                case QUICKLOOK_SD:
                    return Asset.Roles.OVERVIEW;
                case THUMBNAIL:
                    return Asset.Roles.THUMBNAIL;
                case DOCUMENT:
                case DESCRIPTION:
                    return Asset.Roles.METADATA;
                case RAWDATA:
                case AIP:
                case OTHER:
                default:
                    return Asset.Roles.DATA;
            }
        }
    }

}
