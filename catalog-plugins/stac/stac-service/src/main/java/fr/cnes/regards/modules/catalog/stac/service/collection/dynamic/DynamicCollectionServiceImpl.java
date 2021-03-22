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

import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.DynCollDef;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.DynCollVal;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.level.DynCollLevelDef;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Collection;
import fr.cnes.regards.modules.catalog.stac.service.StacSearchCriterionBuilder;
import fr.cnes.regards.modules.catalog.stac.service.collection.dynamic.helpers.DynCollLevelDefParser;
import fr.cnes.regards.modules.catalog.stac.service.collection.dynamic.helpers.DynCollLevelValToQueryObjectConverter;
import fr.cnes.regards.modules.catalog.stac.service.collection.dynamic.helpers.DynCollValNextSublevelHelper;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.item.ItemSearchService;
import fr.cnes.regards.modules.catalog.stac.service.item.RegardsFeatureToStacItemConverter;
import fr.cnes.regards.modules.catalog.stac.service.link.OGCFeatLinkCreator;
import io.vavr.collection.List;
import io.vavr.control.Try;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Base implementation for {@link DynamicCollectionService}.
 */
@Service
public class DynamicCollectionServiceImpl implements DynamicCollectionService {

    private final RestDynCollValSerdeService restDynCollValSerdeService;
    private final DynCollLevelDefParser dynCollLevelDefParser;
    private final DynCollLevelValToQueryObjectConverter levelValToQueryObjectConverter;
    private final DynCollValNextSublevelHelper nextSublevelHelper;
    private final RegardsFeatureToStacItemConverter featureToItemConverter;
    private final StacSearchCriterionBuilder criterionBuilder;
    private final ItemSearchService itemSearchService;

    @Autowired
    public DynamicCollectionServiceImpl(
            RestDynCollValSerdeService restDynCollValSerdeService,
            DynCollLevelDefParser dynCollLevelDefParser,
            DynCollLevelValToQueryObjectConverter levelValToQueryObjectConverter,
            DynCollValNextSublevelHelper nextSublevelHelper,
            RegardsFeatureToStacItemConverter featureToItemConverter,
            StacSearchCriterionBuilder criterionBuilder,
            ItemSearchService itemSearchService
    ) {
        this.restDynCollValSerdeService = restDynCollValSerdeService;
        this.dynCollLevelDefParser = dynCollLevelDefParser;
        this.levelValToQueryObjectConverter = levelValToQueryObjectConverter;
        this.nextSublevelHelper = nextSublevelHelper;
        this.featureToItemConverter = featureToItemConverter;
        this.criterionBuilder = criterionBuilder;
        this.itemSearchService = itemSearchService;
    }

    @Override
    public String representDynamicCollectionsValueAsURN(DynCollVal val) {
        return restDynCollValSerdeService.toUrn(restDynCollValSerdeService.fromDomain(val));
    }

    @Override
    public Try<DynCollVal> parseDynamicCollectionsValueFromURN(
            String urn,
            ConfigurationAccessor config
    ) {
        DynCollDef dynCollDef = dynamicCollectionsDefinition(config.getStacProperties());
        return restDynCollValSerdeService.fromUrn(urn)
            .flatMap(val -> restDynCollValSerdeService.toDomain(dynCollDef, val));
    }

    @Override
    public boolean isDynamicCollectionValueURN(String urn) {
        return restDynCollValSerdeService.isListOfDynCollLevelValues(urn);
    }

    @Override
    public DynCollDef dynamicCollectionsDefinition(List<StacProperty> properties) {
        List<StacProperty> levelProperties = properties
                .filter(p -> Objects.nonNull(p.getDynamicCollectionLevel()))
                .sortBy(StacProperty::getDynamicCollectionLevel);
        List<DynCollLevelDef<?>> levelDefs = levelProperties.map(dynCollLevelDefParser::parse);
        return new DynCollDef(levelDefs);
    }

    @Override
    public ItemSearchBody toItemSearchBody(DynCollVal value) {
        return ItemSearchBody.builder().query(value.getLevels()
            .flatMap(levelValToQueryObjectConverter::toQueryObject)
            .toMap(kv -> kv)).build();
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
