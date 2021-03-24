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

import fr.cnes.regards.modules.catalog.stac.domain.StacSpecConstants;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.CollectionsResponse;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemCollectionResponse;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBodyFactory;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.DynCollDef;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.DynCollVal;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Collection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection.Extent;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.geo.BBox;
import fr.cnes.regards.modules.catalog.stac.service.collection.dynamic.DynamicCollectionService;
import fr.cnes.regards.modules.catalog.stac.service.collection.dynamic.RestDynCollValSerdeService;
import fr.cnes.regards.modules.catalog.stac.service.collection.dynamic.helpers.DynCollValNextSublevelHelper;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessorFactory;
import fr.cnes.regards.modules.catalog.stac.service.item.ItemSearchService;
import fr.cnes.regards.modules.catalog.stac.service.link.OGCFeatLinkCreator;
import fr.cnes.regards.modules.catalog.stac.service.link.SearchPageLinkCreator;
import fr.cnes.regards.modules.catalog.stac.service.link.StacLinkCreator;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.function.Function;

import static fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link.Relations.COLLECTION;
import static fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link.Relations.SELF;
import static fr.cnes.regards.modules.catalog.stac.service.collection.dynamic.DynamicCollectionServiceImpl.DEFAULT_DYNAMIC_ID;

/**
 * Base implementation for {@link CollectionService}.
 */
@Service
public class CollectionServiceImpl implements CollectionService, StacLinkCreator {

    private static final Logger LOGGER = LoggerFactory.getLogger(CollectionServiceImpl.class);

    public static final String DEFAULT_STATIC_ID = "static";

    private final ConfigurationAccessorFactory configurationAccessorFactory;
    private final DynamicCollectionService dynCollService;
    private final DynCollValNextSublevelHelper sublevelHelper;
    private final RestDynCollValSerdeService restDynCollValSerdeService;
    private final ItemSearchBodyFactory itemSearchBodyFactory;
    private final ItemSearchService itemSearchService;

    @Autowired
    public CollectionServiceImpl(
            ConfigurationAccessorFactory configurationAccessorFactory,
            DynamicCollectionService dynCollService,
            DynCollValNextSublevelHelper sublevelHelper,
            RestDynCollValSerdeService restDynCollValSerdeService,
            ItemSearchBodyFactory itemSearchBodyFactory,
            ItemSearchService itemSearchService
    ) {
        this.configurationAccessorFactory = configurationAccessorFactory;
        this.dynCollService = dynCollService;
        this.sublevelHelper = sublevelHelper;
        this.restDynCollValSerdeService = restDynCollValSerdeService;
        this.itemSearchBodyFactory = itemSearchBodyFactory;
        this.itemSearchService = itemSearchService;
    }

    @Override
    public boolean hasDynamicCollections(List<StacProperty> properties) {
        return properties.find(p -> Objects.nonNull(p.getDynamicCollectionLevel())).isDefined();
    }

    @Override
    public Collection buildRootDynamicCollection(OGCFeatLinkCreator linkCreator, ConfigurationAccessor config) {
        String name = config.getRootDynamicCollectionName();
        DynCollDef def = dynCollService.dynamicCollectionsDefinition(config.getStacProperties());
        return new Collection(
                StacSpecConstants.Version.STAC_SPEC_VERSION,
                List.empty(),
                name, DEFAULT_DYNAMIC_ID, "Dynamic collections",
                dynamicCollectionLinks(linkCreator, DEFAULT_DYNAMIC_ID, new DynCollVal(def, List.empty())),
                List.empty(),
                "",
                List.empty(),
                Extent.maximalExtent(), // no extent at this level
                HashMap.empty() // no summaries at this level
        );
    }

    private List<Link> dynamicCollectionLinks(OGCFeatLinkCreator linkCreator, String selfId, DynCollVal currentVal) {

        List<Link> baseLinks = List.of(
            linkCreator.createRootLink(),
            linkCreator.createCollectionLink(selfId, "Self").map(l -> l.withRel(SELF)),
            linkCreator.createCollectionItemsLink(selfId)
        )
        .flatMap(l -> l);

        if (!currentVal.isFullyValued()) {
            List<Link> sublevelsLinks = sublevelHelper
                .nextSublevels(currentVal)
                .map(val -> {
                    String urn = dynCollService.representDynamicCollectionsValueAsURN(val);
                    String label = val.getLowestLevelLabel();
                    return linkCreator.createCollectionLink(urn, label).map(l -> l.withRel(COLLECTION));
                })
                .flatMap(t -> t);
            return baseLinks.appendAll(sublevelsLinks);
        }
        else {
            return baseLinks;
        }
    }

    @Override
    public Collection buildRootStaticCollection(OGCFeatLinkCreator linkCreator,ConfigurationAccessor config) {
        String name = config.getRootStaticCollectionName();
        return new Collection(
                StacSpecConstants.Version.STAC_SPEC_VERSION,
                List.empty(),
                name, DEFAULT_STATIC_ID, "Static collections",
                staticCollectionLinks(linkCreator),
                List.empty(),
                "",
                List.empty(),
                Extent.maximalExtent(), // no extent at this level
                HashMap.empty() // no summaries at this level
        );
    }

    private List<Collection> staticCollections(OGCFeatLinkCreator linkCreator) {
        return List.empty(); // TODO
    }

    private List<Link> staticCollectionLinks(OGCFeatLinkCreator linkCreator) {
        return staticCollections(linkCreator)
            .map(linkCreator::createCollectionLink)
            .flatMap(l -> l);
    }

    @Override
    public Try<CollectionsResponse> buildRootCollectionsResponse(
        OGCFeatLinkCreator linkCreator,
        ConfigurationAccessor config
    ) {
        return Try.of(() -> new CollectionsResponse(
                    buildCollectionsLinks(linkCreator),
                    buildRootCollections(linkCreator, config)));
    }

    private List<Collection> buildRootCollections(
        OGCFeatLinkCreator linkCreator,
        ConfigurationAccessor config
    ) {
        return hasDynamicCollections(config.getStacProperties())
            ? List.of(
                buildRootDynamicCollection(linkCreator, config),
                buildRootStaticCollection(linkCreator, config))
            : staticCollections(linkCreator);
    }

    public List<Link> buildCollectionsLinks(OGCFeatLinkCreator linkCreator) {
        return List.of(
            linkCreator.createRootLink(),
            linkCreator.createCollectionsLink()
        ).flatMap(l -> l);
    }

    @Override
    public Try<Collection> buildCollection(String collectionId, OGCFeatLinkCreator linkCreator, ConfigurationAccessor config) {
        if (DEFAULT_STATIC_ID.equals(collectionId)) {
            return Try.of(() -> buildRootStaticCollection(linkCreator, config));
        }
        else if (DEFAULT_DYNAMIC_ID.equals(collectionId)) {
            return Try.of(() -> buildRootDynamicCollection(linkCreator, config));
        }
        else if (dynCollService.isDynamicCollectionValueURN(collectionId)){
            return dynCollService.parseDynamicCollectionsValueFromURN(collectionId, config)
                    .flatMap(dcv -> dynCollService.buildCollection(dcv, linkCreator, config));
        }
        else {
            // TODO: fetch static collection
            return null;
        }
    }

    @Override
    public Try<ItemCollectionResponse> getItemsForCollection(
            String collectionId,
            Integer limit,
            BBox bbox,
            String datetime,
            OGCFeatLinkCreator ogcFeatLinkCreator,
            Function<ItemSearchBody, SearchPageLinkCreator> searchPageLinkCreatorMaker
    ) {
        ConfigurationAccessor config = configurationAccessorFactory.makeConfigurationAccessor();

        return dynCollService.parseDynamicCollectionsValueFromURN(collectionId, config)
            .flatMap(val -> itemSearchBodyFactory
                .parseItemSearch(
                    limit, bbox, datetime, List.of(collectionId),
                    null, null, null, null
                )
                .map(isb ->
                    isb.withQuery(dynCollService.toItemSearchBody(val).getQuery())
                )
            )
            .flatMap(isb ->
                itemSearchService.search(
                    isb, 0, ogcFeatLinkCreator, searchPageLinkCreatorMaker.apply(isb)
                )
            );
    }
}
