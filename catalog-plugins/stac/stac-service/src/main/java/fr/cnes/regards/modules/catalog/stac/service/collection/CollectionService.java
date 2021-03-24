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

package fr.cnes.regards.modules.catalog.stac.service.collection;

import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.CollectionsResponse;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemCollectionResponse;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Collection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.geo.BBox;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.link.OGCFeatLinkCreator;
import fr.cnes.regards.modules.catalog.stac.service.link.SearchPageLinkCreator;
import io.vavr.collection.List;
import io.vavr.control.Try;

import java.util.function.Function;

/**
 * Allows to make decisions on how to route collections on the first level.
 */
public interface CollectionService {

    boolean hasDynamicCollections(List<StacProperty> properties);

    Collection buildRootDynamicCollection(OGCFeatLinkCreator linkCreator, ConfigurationAccessor config);

    Collection buildRootStaticCollection(OGCFeatLinkCreator linkCreator, ConfigurationAccessor config);

    Try<CollectionsResponse> buildRootCollectionsResponse(
        OGCFeatLinkCreator linkCreator,
        ConfigurationAccessor config
    );

    Try<Collection> buildCollection(
        String collectionId,
        OGCFeatLinkCreator linkCreator,
        ConfigurationAccessor config
    );

    Try<ItemCollectionResponse> getItemsForCollection(
        String collectionId,
        Integer limit,
        BBox bbox,
        String datetime,
        OGCFeatLinkCreator ogcFeatLinkCreator,
        Function<ItemSearchBody, SearchPageLinkCreator> searchPageLinkCreatorMaker
    );
}
