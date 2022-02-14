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

package fr.cnes.regards.modules.catalog.stac.service.collection.dyncoll;

import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.DynCollDef;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.DynCollVal;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Collection;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.link.OGCFeatLinkCreator;
import io.vavr.collection.List;
import io.vavr.control.Try;

/**
 * This interface defines methods to build dynamic collections from their ID.
 */
public interface DynamicCollectionService {

    /**
     * Provide an optional dynamic collection definition from the configured
     * STAC properties.
     *
     * @param properties the stac properties defined by the user
     * @return empty if no property is configured to be a dynamic collection level
     */
    DynCollDef dynamicCollectionsDefinition(List<StacProperty> properties);

    String representDynamicCollectionsValueAsURN(DynCollVal val);
    Try<DynCollVal> parseDynamicCollectionsValueFromURN(String urn, ConfigurationAccessor config);
    boolean isDynamicCollectionValueURN(String urn);

    ItemSearchBody toItemSearchBody(DynCollVal value);

    Try<Collection> buildCollection(
            DynCollVal dynCollVal,
            OGCFeatLinkCreator linkCreator,
            ConfigurationAccessor config
    );

}
