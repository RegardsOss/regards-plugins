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

import static fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.SearchBody.SortBy.Direction.ASC;
import static fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.SearchBody.SortBy.Direction.DESC;
import static fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType.FIELDS_PARSING;
import static fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType.SORTBY_PARSING;
import static fr.cnes.regards.modules.catalog.stac.domain.utils.TryDSL.trying;
import static java.lang.String.format;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.SearchBody.Fields;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.SearchBody.SortBy;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.geo.BBox;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Stream;
import io.vavr.control.Option;
import io.vavr.control.Try;

/**
 * Implementation for the ItemSearchBodyFactory interface.
 */
@Component
public class ItemSearchBodyFactoryImpl extends AbstractSearchBodyFactoryImpl implements ItemSearchBodyFactory {

    @Autowired
    public ItemSearchBodyFactoryImpl(Gson gson) {
        super(gson);
    }

    // @formatter:off

    @Override
    public Try<ItemSearchBody> parseItemSearch(
            Integer limit,
            BBox bbox,
            String datetime,
            List<String> collections,
            List<String> ids,
            String fields,
            String query,
            String sortBy
    ) {
        return parseDateInterval(datetime).flatMap(dt ->
            parseFields(fields).flatMap(f ->
                parseQuery(query).flatMap(q ->
                    parseSortBy(sortBy).map(s ->
                        new ItemSearchBody(
                            bbox, dt.getOrNull(), null, collections, ids, limit, f, q, s
                        )
                    )
                )
            )
        );
    }

    // @formatter:on
}
