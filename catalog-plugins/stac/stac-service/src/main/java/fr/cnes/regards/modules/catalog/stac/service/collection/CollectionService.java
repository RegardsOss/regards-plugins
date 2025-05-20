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

package fr.cnes.regards.modules.catalog.stac.service.collection;

import fr.cnes.regards.modules.catalog.stac.domain.api.CollectionsResponse;
import fr.cnes.regards.modules.catalog.stac.domain.api.ItemCollectionResponse;
import fr.cnes.regards.modules.catalog.stac.domain.api.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.spec.Collection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.common.Link;
import fr.cnes.regards.modules.catalog.stac.domain.spec.geo.BBox;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.configuration.collection.CollectionConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.link.OGCFeatLinkCreator;
import fr.cnes.regards.modules.catalog.stac.service.link.SearchPageLinkCreator;
import io.vavr.collection.List;
import io.vavr.control.Try;

import java.util.Map;
import java.util.function.Function;

/**
 * Allows making decisions on how to route collections on the first level.
 */
public interface CollectionService {

    boolean hasDynamicCollections(List<StacProperty> properties);

    Collection buildRootDynamicCollection(OGCFeatLinkCreator linkCreator, ConfigurationAccessor config);

    Collection buildRootStaticCollection(OGCFeatLinkCreator linkCreator, ConfigurationAccessor config);

    Try<CollectionsResponse> buildRootCollectionsResponse(OGCFeatLinkCreator linkCreator,
                                                          ConfigurationAccessor config,
                                                          CollectionConfigurationAccessor collectionConfigurationAccessor);

    Try<Collection> buildCollection(String collectionId,
                                    OGCFeatLinkCreator linkCreator,
                                    ConfigurationAccessor config,
                                    CollectionConfigurationAccessor collectionConfigurationAccessor);

    Try<ItemCollectionResponse> getItemsForCollection(String collectionId,
                                                      Integer limit,
                                                      Integer page,
                                                      BBox bbox,
                                                      String datetime,
                                                      OGCFeatLinkCreator ogcFeatLinkCreator,
                                                      Function<ItemSearchBody, SearchPageLinkCreator> searchPageLinkCreatorMaker,
                                                      Map<String, String> headers);

    java.util.List<Link> buildLandingPageLinks(ConfigurationAccessor config, OGCFeatLinkCreator linkCreator);
}
