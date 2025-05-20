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
package fr.cnes.regards.modules.catalog.stac.service.collection.statcoll;

import fr.cnes.regards.modules.catalog.stac.domain.spec.Collection;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.configuration.collection.CollectionConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.link.OGCFeatLinkCreator;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Try;

/**
 * Translate regards collection to stac standard
 */
public interface StaticCollectionService {

    /**
     * Get stac collection from elastic
     *
     * @param urn         uniform resource number of the collection
     * @param linkCreator utility class to create links
     * @param config      {@link ConfigurationAccessor}
     * @return the stac collection
     */
    Try<Collection> convertRequest(String urn, OGCFeatLinkCreator linkCreator, ConfigurationAccessor config);

    /**
     * Get stac collection from elastic
     *
     * @param urn         uniform resource number of the collection
     * @param linkCreator utility class to create links
     * @param config      {@link CollectionConfigurationAccessor}
     * @return the stac collection
     */
    Try<Collection> convertRequest(String urn, OGCFeatLinkCreator linkCreator, CollectionConfigurationAccessor config);

    /**
     * Identify the collections/datasets with no parent.
     *
     * @return the ids and labels of the collections/datasets which have no parent collection
     */
    List<Tuple2<String, String>> staticRootCollectionsIdsAndLabels();

    /**
     * Build static root collections
     *
     * @param linkCreator link creator
     * @param config      configuration accessor
     * @return list of static collections
     */
    List<Collection> staticRootCollections(OGCFeatLinkCreator linkCreator, ConfigurationAccessor config);

    /**
     * Build static root collections using the collection configuration accessor
     *
     * @param linkCreator                     link creator
     * @param collectionConfigurationAccessor collection configuration accessor
     * @return list of static collections
     */
    List<Collection> staticRootCollections(OGCFeatLinkCreator linkCreator,
                                           CollectionConfigurationAccessor collectionConfigurationAccessor);
}
