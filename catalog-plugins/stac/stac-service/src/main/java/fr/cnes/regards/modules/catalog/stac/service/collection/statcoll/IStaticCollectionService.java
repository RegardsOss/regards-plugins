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
package fr.cnes.regards.modules.catalog.stac.service.collection.statcoll;

import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Collection;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.link.OGCFeatLinkCreator;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Try;


/**
 * Translate regards collection to stac standard
 */
public interface IStaticCollectionService {

    /**
     * Get stac collection from elsatic
     * @param urn uniform resource number of the collection
     * @param linkCreator
     * @param config
     * @return the stac collection
     */
    Try<Collection> convertRequest(String urn, OGCFeatLinkCreator linkCreator, ConfigurationAccessor config);

    /**
     * Identify the collections/datasets with no parent.
     * @return the ids and labels of the collections/datasets which have no parent collection
     */
    List<Tuple2<String, String>> staticRootCollectionsIdsAndLabels();

    List<Collection> staticRootCollections(OGCFeatLinkCreator linkCreator, ConfigurationAccessor config);
}
