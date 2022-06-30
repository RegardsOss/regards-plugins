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

package fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.extension.searchcol;

import com.google.gson.Gson;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.AbstractSearchBodyFactoryImpl;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.geo.BBox;
import io.vavr.collection.List;
import io.vavr.control.Try;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Implementation for the ItemSearchBodyFactory interface.
 */
@Component
public class CollectionItemSearchBodyFactoryImpl extends AbstractSearchBodyFactoryImpl
    implements CollectionItemSearchBodyFactory {

    @Autowired
    public CollectionItemSearchBodyFactoryImpl(Gson gson) {
        super(gson);
    }

    // @formatter:off

    @Override
    public Try<CollectionSearchBody.CollectionItemSearchBody> parseCollectionSearch(
            BBox bbox,
            String datetime,
            List<String> collections,
            List<String> ids,
            String query
    ) {
        return parseDateInterval(datetime).flatMap(dt ->
                parseQuery(query).map(q ->
                        new CollectionSearchBody.CollectionItemSearchBody(
                            bbox, dt.getOrNull(), null, collections, ids,  q
                        )
                )
        );
    }

    // @formatter:on
}
