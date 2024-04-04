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

package fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.extensions;

import com.google.gson.annotations.SerializedName;
import lombok.Value;
import lombok.With;

/**
 * Describes a band object as defined in the eo extension.
 *
 * @see <a href="https://github.com/radiantearth/stac-spec/tree/v1.0.0-beta.2/extensions/eo#band-object">description</a>
 */
@Value
@With
public class EoBand {

    @SerializedName("name")
    String name;

    @SerializedName("common_name")
    CommonBandName commonName;

    @SerializedName("description")
    String description;

    @SerializedName("center_wavelength")
    Double centerWavelength; //NOSONAR

    @SerializedName("full_width_half_max")
    Double fullWidthHalfMax; //NOSONAR

    enum CommonBandName {
        coastal,
        blue,
        green,
        red,
        yellow,
        pan,
        rededge,
        nir,
        nir08,
        nir09,
        cirrus,
        swir16,
        swir22,
        lwir,
        lwir11,
        lwir12,
    }

}
