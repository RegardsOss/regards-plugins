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

package fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection;

import com.google.gson.annotations.SerializedName;
import io.vavr.collection.List;
import lombok.Value;
import lombok.With;

import java.net.URL;

/**
 * A provider is any of the organizations that captures or processes the content of the collection
 * and therefore influences the data offered by this collection. May also include information
 * about the final storage provider hosting the data.
 *
 * @see <a href="https://github.com/radiantearth/stac-spec/blob/v1.0.0-beta.2/collection-spec/collection-spec.md#provider-object">description</a>
 * @see <a href="https://github.com/radiantearth/stac-spec/blob/v1.0.0-beta.2/collection-spec/json-schema/collection.json#L57">json schema</a>
 */
@Value @With
public class Provider {

    enum Role {
        @SerializedName("licensor") LICENSOR,
        @SerializedName("producer") PRODUCER,
        @SerializedName("processor") PROCESSOR,
        @SerializedName("host") HOST
    }

    String name;
    String description;
    URL url;
    List<Role> roles;
}
