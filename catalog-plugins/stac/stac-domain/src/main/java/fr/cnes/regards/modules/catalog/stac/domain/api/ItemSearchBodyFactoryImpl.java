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

import com.google.gson.Gson;
import fr.cnes.regards.modules.catalog.stac.domain.spec.geo.BBox;
import io.vavr.collection.List;
import io.vavr.control.Try;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
            Integer page,
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
                            bbox, dt.getOrNull(), null, collections, ids, page, limit, f, q, s
                        )
                    )
                )
            )
        );
    }

    // @formatter:on
}
