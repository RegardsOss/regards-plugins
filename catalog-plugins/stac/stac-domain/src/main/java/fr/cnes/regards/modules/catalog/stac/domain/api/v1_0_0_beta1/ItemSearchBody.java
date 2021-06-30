/*
 * Copyright 2017-2021 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
import fr.cnes.regards.framework.geojson.geometry.IGeometry;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.geo.BBox;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.time.OffsetDateTime;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Describes the body of "POST /search" request.
 *
 * @see <a href="">Description</a>
 */
@Value @With @Builder
public class ItemSearchBody {

    BBox bbox;
    DateInterval datetime;
    IGeometry intersects;
    List<String> collections;
    List<String> ids;
    Integer limit;
    SearchBody.Fields fields;
    Map<String, SearchBody.QueryObject> query;
    List<SearchBody.SortBy> sortBy;

}
