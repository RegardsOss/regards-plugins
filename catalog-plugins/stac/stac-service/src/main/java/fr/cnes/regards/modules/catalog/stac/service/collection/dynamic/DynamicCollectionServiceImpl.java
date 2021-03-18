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

package fr.cnes.regards.modules.catalog.stac.service.collection.dynamic;

import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.DynCollDef;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.DynCollVal;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.level.DynCollLevelDef;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Collection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Item;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.link.OGCFeatLinkCreator;
import io.vavr.collection.List;
import io.vavr.control.Try;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Base implementation for {@link DynamicCollectionService}.
 */
@Service
public class DynamicCollectionServiceImpl implements DynamicCollectionService {

    public static final String URN_PREFIX = "URN:DYNCOLL:";

    private final RestDynCollValSerdeService restDynCollValSerdeService;

    @Autowired
    public DynamicCollectionServiceImpl(
            RestDynCollValSerdeService restDynCollValSerdeService
    ) {
        this.restDynCollValSerdeService = restDynCollValSerdeService;
    }

    @Override
    public Try<String> representDynamicCollectionsValueAsURN(
            DynCollVal val
    ) {
        return Try.of(() -> restDynCollValSerdeService.toUrn(restDynCollValSerdeService.fromDomain(val)));
    }

    @Override
    public Try<DynCollVal> parseDynamicCollectionsValueFromURN(
            String urn,
            ConfigurationAccessor config
    ) {
        return dynamicCollectionsDefinition(config.getStacProperties())
                .flatMap(def -> restDynCollValSerdeService.fromUrn(urn)
                        .flatMap(val -> restDynCollValSerdeService.toDomain(def, val)));
    }

    @Override
    public boolean isDynamicCollectionValueURN(
            String urn
    ) {
        return restDynCollValSerdeService.isListOfDynCollLevelValues(urn);
    }

    @Override
    public Try<DynCollDef> dynamicCollectionsDefinition(
            List<StacProperty> properties
    ) {
        return null; // TODO
    }

    @Override
    public DynCollLevelDef parseDynamicCollectionLevelDefinition(
            String levelFormat
    ) {
        return null; // TODO
    }

    @Override
    public boolean hasMoreSublevels(
            DynCollDef def,
            DynCollVal value
    ) {
        return false; // TODO
    }

    @Override
    public List<DynCollVal> sublevels(
            DynCollDef def,
            DynCollVal value
    ) {
        return null; // TODO
    }

    @Override
    public List<Item> searchItemsInDynamicCollection(
            DynCollDef def,
            DynCollVal value
    ) {
        return null; // TODO
    }

    @Override
    public Try<Collection> buildCollection(
            DynCollVal restDynCollVal,
            OGCFeatLinkCreator linkCreator,
            ConfigurationAccessor config
    ) {
        return null; // TODO
    }
}
