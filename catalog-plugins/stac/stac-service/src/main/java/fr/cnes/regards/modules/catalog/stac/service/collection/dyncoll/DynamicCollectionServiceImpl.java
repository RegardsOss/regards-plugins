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

package fr.cnes.regards.modules.catalog.stac.service.collection.dyncoll;

import static fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType.COLLECTION_CONSTRUCTION;
import static fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link.Relations.ANCESTOR;
import static fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link.Relations.CHILD;
import static fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link.Relations.ITEMS;
import static fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link.Relations.PARENT;
import static fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link.Relations.SELF;
import static fr.cnes.regards.modules.catalog.stac.domain.utils.TryDSL.trying;
import static java.lang.String.format;

import io.vavr.collection.HashSet;
import io.vavr.collection.Set;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import fr.cnes.regards.modules.catalog.stac.domain.StacSpecConstants;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.DynCollDef;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.DynCollVal;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.level.DynCollLevelDef;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Collection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection.Extent;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection.Provider;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link;
import fr.cnes.regards.modules.catalog.stac.service.collection.EsAggregationHelper;
import fr.cnes.regards.modules.catalog.stac.service.collection.ExtentSummaryService;
import fr.cnes.regards.modules.catalog.stac.service.collection.dyncoll.helpers.DynCollLevelDefParser;
import fr.cnes.regards.modules.catalog.stac.service.collection.dyncoll.helpers.DynCollLevelValToQueryObjectConverter;
import fr.cnes.regards.modules.catalog.stac.service.collection.dyncoll.helpers.DynCollValNextSublevelHelper;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.criterion.StacSearchCriterionBuilder;
import fr.cnes.regards.modules.catalog.stac.service.link.OGCFeatLinkCreator;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import io.vavr.control.Try;

/**
 * Base implementation for {@link DynamicCollectionService}.
 */
@Service
public class DynamicCollectionServiceImpl implements DynamicCollectionService {

    @SuppressWarnings("unused")
    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicCollectionServiceImpl.class);

    public static final String DEFAULT_DYNAMIC_ID = "dynamic";

    public static final int MAX_ITEMS_IN_DYNAMIC_COLLECTION = 100;

    private final RestDynCollValSerdeService restDynCollValSerdeService;

    private final DynCollLevelDefParser dynCollLevelDefParser;

    private final DynCollLevelValToQueryObjectConverter levelValToQueryObjectConverter;

    private final DynCollValNextSublevelHelper nextSublevelHelper;

    private final StacSearchCriterionBuilder criterionBuilder;

    private final ExtentSummaryService extentSummaryService;

    private final EsAggregationHelper aggregagtionHelper;

    @Autowired
    public DynamicCollectionServiceImpl(RestDynCollValSerdeService restDynCollValSerdeService,
            DynCollLevelDefParser dynCollLevelDefParser,
            DynCollLevelValToQueryObjectConverter levelValToQueryObjectConverter,
            DynCollValNextSublevelHelper nextSublevelHelper, StacSearchCriterionBuilder criterionBuilder,
            ExtentSummaryService extentSummaryService, EsAggregationHelper aggregagtionHelper) {
        this.restDynCollValSerdeService = restDynCollValSerdeService;
        this.dynCollLevelDefParser = dynCollLevelDefParser;
        this.levelValToQueryObjectConverter = levelValToQueryObjectConverter;
        this.nextSublevelHelper = nextSublevelHelper;
        this.criterionBuilder = criterionBuilder;
        this.extentSummaryService = extentSummaryService;
        this.aggregagtionHelper = aggregagtionHelper;
    }

    @Override
    public String representDynamicCollectionsValueAsURN(DynCollVal val) {
        return restDynCollValSerdeService.toUrn(restDynCollValSerdeService.fromDomain(val));
    }

    @Override
    public Try<DynCollVal> parseDynamicCollectionsValueFromURN(String urn, ConfigurationAccessor config) {
        DynCollDef dynCollDef = dynamicCollectionsDefinition(config.getStacProperties());
        return restDynCollValSerdeService.fromUrn(urn).flatMap(val -> restDynCollValSerdeService.toDomain(dynCollDef, val));
    }

    @Override
    public boolean isDynamicCollectionValueURN(String urn) {
        return restDynCollValSerdeService.isListOfDynCollLevelValues(urn);
    }

    @Override
    public DynCollDef dynamicCollectionsDefinition(List<StacProperty> properties) {
        List<StacProperty> levelProperties = properties.filter(StacProperty::isDynamicCollectionLevel)
                .sortBy(StacProperty::getDynamicCollectionLevel);
        List<DynCollLevelDef<?>> levelDefs = levelProperties.map(dynCollLevelDefParser::parse);
        return new DynCollDef(levelDefs);
    }

    @Override
    public ItemSearchBody toItemSearchBody(DynCollVal value) {
        return ItemSearchBody.builder().limit(MAX_ITEMS_IN_DYNAMIC_COLLECTION)
                .query(value.getLevels().flatMap(levelValToQueryObjectConverter::toQueryObject).toMap(kv -> kv)).build();
    }

    @Override
    public Try<Collection> buildCollection(DynCollVal val, OGCFeatLinkCreator linkCreator, ConfigurationAccessor config) {
        return trying(() -> {
            String selfUrn = representDynamicCollectionsValueAsURN(val);
            List<Link> baseLinks = List
                    .of(linkCreator.createRootLink(),
                        linkCreator.createCollectionLinkWithRel(DEFAULT_DYNAMIC_ID, config.getRootDynamicCollectionName(),
                                                                ANCESTOR),
                        linkCreator.createCollectionLinkWithRel(selfUrn, val.toLabel(), SELF),
                        getParentLink(val, linkCreator))
                    .flatMap(t -> t);

            ItemSearchBody itemSearchBody = toItemSearchBody(val);
            ICriterion criterion = criterionBuilder.toCriterion(config.getStacProperties(), itemSearchBody);
            List<AggregationBuilder> aggDefs = extentSummaryService
                    .extentSummaryAggregationBuilders(config.getDatetimeStacProperty(), config.getStacProperties());
            List<Aggregation> aggVals = List.ofAll(aggregagtionHelper.getAggregationsFor(criterion, aggDefs).asList());
            Map<StacProperty, Aggregation> aggsMap = extentSummaryService.toAggregationMap(config.getStacProperties(),
                                                                                           aggVals);
            Extent extent = extentSummaryService.extractExtent(aggsMap);
            Map<String, Object> summary = extentSummaryService.extractSummary(aggsMap);

            if (val.isFullyValued()) {
                return createCollectionFrom(val, extent, summary, baseLinks, createItemsLink(selfUrn, linkCreator));
            } else {
                List<DynCollVal> nextVals = nextSublevelHelper.nextSublevels(val);
                return createCollectionFrom(val, extent, summary, baseLinks, createChildLinks(nextVals, linkCreator));
            }
        }).mapFailure(COLLECTION_CONSTRUCTION, () -> format("Failed to build dynamic collection for %s", val));
    }

    private Collection createCollectionFrom(DynCollVal val, Extent extent, Map<String, Object> summary, List<Link> baseLinks,
            List<Link> childLinks) {
        Set<String> extensions = HashSet.empty();
        List<String> keywords = List.empty();
        String license = "";
        List<Provider> providers = List.empty();

        return new Collection(StacSpecConstants.Version.STAC_SPEC_VERSION, extensions, val.getLowestLevelLabel(),
                representDynamicCollectionsValueAsURN(val), val.toLabel(), baseLinks.appendAll(childLinks), keywords,
                license, providers, extent, summary,null,null);
    }

    private List<Link> createChildLinks(List<DynCollVal> nextVals, OGCFeatLinkCreator linkCreator) {
        return nextVals.flatMap(val -> linkCreator.createCollectionLinkWithRel(representDynamicCollectionsValueAsURN(val),
                                                                               val.toLabel(), CHILD));
    }

    private List<Link> createItemsLink(String selfUrn, OGCFeatLinkCreator linkCreator) {
        return List.of(linkCreator.createCollectionItemsLinkWithRel(selfUrn, ITEMS)).flatMap(t -> t);
    }

    private Option<Link> getParentLink(DynCollVal val, OGCFeatLinkCreator linkCreator) {
        return val.parentValue().flatMap(parent -> {
            String parentUrn = representDynamicCollectionsValueAsURN(parent);
            return linkCreator.createCollectionLinkWithRel(parentUrn, parent.toLabel(), PARENT).toOption();
        });
    }

}
