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
import fr.cnes.regards.modules.catalog.stac.service.collection.statcoll.IStaticCollectionService;
import fr.cnes.regards.modules.catalog.stac.service.collection.dyncoll.DynamicCollectionService;
import fr.cnes.regards.modules.catalog.stac.service.collection.dyncoll.helpers.DynCollValNextSublevelHelper;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessorFactory;
import fr.cnes.regards.modules.catalog.stac.service.item.ItemSearchService;
import fr.cnes.regards.modules.catalog.stac.service.link.OGCFeatLinkCreator;
import fr.cnes.regards.modules.catalog.stac.service.link.SearchPageLinkCreator;
import fr.cnes.regards.modules.catalog.stac.service.link.StacLinkCreator;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Try;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.function.Function;

import static fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType.COLLECTIONSRESPONSE_CONSTRUCTION;
import static fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType.COLLECTION_CONSTRUCTION;
import static fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link.Relations.CHILD;
import static fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link.Relations.SELF;
import static fr.cnes.regards.modules.catalog.stac.domain.utils.TryDSL.trying;
import static fr.cnes.regards.modules.catalog.stac.service.collection.dyncoll.DynamicCollectionServiceImpl.DEFAULT_DYNAMIC_ID;
import static java.lang.String.format;

/**
 * Base implementation for {@link CollectionService}.
 */
@Service
public class CollectionServiceImpl implements CollectionService, StacLinkCreator {

    public static final String DEFAULT_STATIC_ID = "static";

    private final ConfigurationAccessorFactory configurationAccessorFactory;
    private final DynamicCollectionService dynCollService;
    private final DynCollValNextSublevelHelper sublevelHelper;
    private final ItemSearchBodyFactory itemSearchBodyFactory;
    private final ItemSearchService itemSearchService;
    private final IStaticCollectionService staticCollectionService;

    @Autowired
    public CollectionServiceImpl(
            ConfigurationAccessorFactory configurationAccessorFactory,
            DynamicCollectionService dynCollService,
            DynCollValNextSublevelHelper sublevelHelper,
            ItemSearchBodyFactory itemSearchBodyFactory,
            ItemSearchService itemSearchService,
            IStaticCollectionService staticCollectionService
    ) {
        this.configurationAccessorFactory = configurationAccessorFactory;
        this.dynCollService = dynCollService;
        this.sublevelHelper = sublevelHelper;
        this.itemSearchBodyFactory = itemSearchBodyFactory;
        this.itemSearchService = itemSearchService;
        this.staticCollectionService = staticCollectionService;
    }

    @Override
    public boolean hasDynamicCollections(List<StacProperty> properties) {
        return properties.find(StacProperty::isDynamicCollectionLevel).isDefined();
    }

    @Override
    public Collection buildRootDynamicCollection(OGCFeatLinkCreator linkCreator, ConfigurationAccessor config) {
        String name = config.getRootDynamicCollectionName();
        DynCollDef def = dynCollService.dynamicCollectionsDefinition(config.getStacProperties());
        return new Collection(
                StacSpecConstants.Version.STAC_SPEC_VERSION,
                List.empty(),
                name, DEFAULT_DYNAMIC_ID, name,
                dynamicCollectionLinks(linkCreator, DEFAULT_DYNAMIC_ID , name, new DynCollVal(def, List.empty())),
                List.empty(),
                "",
                List.empty(),
                Extent.maximalExtent(), // no extent at this level
                HashMap.empty() // no summaries at this level
        );
    }

    private List<Link> dynamicCollectionLinks(OGCFeatLinkCreator linkCreator, String selfId, String selfTitle, DynCollVal currentVal) {

        List<Link> baseLinks = List.of(
            linkCreator.createRootLink(),
            linkCreator.createCollectionLinkWithRel(selfId, selfTitle, SELF)
        )
        .flatMap(l -> l);

        if (!currentVal.isFullyValued()) {
            List<Link> sublevelsLinks = sublevelHelper
                .nextSublevels(currentVal)
                .map(val -> {
                    String urn = dynCollService.representDynamicCollectionsValueAsURN(val);
                    String label = val.getLowestLevelLabel();
                    return linkCreator.createCollectionLinkWithRel(urn, label, CHILD);
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

    private List<Link> staticCollectionLinks(OGCFeatLinkCreator linkCreator) {
        return staticCollectionService.staticRootCollectionsIdsAndLabels()
            .map(idLabel -> linkCreator.createCollectionLink(idLabel._1, idLabel._2))
            .flatMap(l -> l);
    }

    @Override
    public Try<CollectionsResponse> buildRootCollectionsResponse(
        OGCFeatLinkCreator linkCreator,
        ConfigurationAccessor config
    ) {
        return trying(() -> new CollectionsResponse(
                    buildCollectionsLinks(linkCreator),
                    buildRootCollections(linkCreator, config)))
            .mapFailure(
                COLLECTIONSRESPONSE_CONSTRUCTION,
                () -> "Failed to build collections response"
            );
    }

    private List<Collection> buildRootCollections(
        OGCFeatLinkCreator linkCreator,
        ConfigurationAccessor config
    ) {
        return hasDynamicCollections(config.getStacProperties())
            ? List.of(
                buildRootDynamicCollection(linkCreator, config),
                buildRootStaticCollection(linkCreator, config))
            : staticCollectionService.staticRootCollections(linkCreator, config);
    }

    private List<Link> buildCollectionsLinks(OGCFeatLinkCreator linkCreator) {
        return List.of(
            linkCreator.createRootLink(),
            linkCreator.createCollectionsLink()
        ).flatMap(l -> l);
    }

    @Override
    public Try<Collection> buildCollection(String collectionId, OGCFeatLinkCreator linkCreator, ConfigurationAccessor config) {
        if (DEFAULT_STATIC_ID.equals(collectionId)) {
            return trying(() -> buildRootStaticCollection(linkCreator, config))
                .mapFailure(
                    COLLECTION_CONSTRUCTION,
                    () -> format("Failed to build collection %s", collectionId)
                );
        }
        else if (DEFAULT_DYNAMIC_ID.equals(collectionId)) {
            return trying(() -> buildRootDynamicCollection(linkCreator, config))
                .mapFailure(
                    COLLECTION_CONSTRUCTION,
                    () -> format("Failed to build collection %s", collectionId)
                );
        }
        else if (dynCollService.isDynamicCollectionValueURN(collectionId)){
            return dynCollService.parseDynamicCollectionsValueFromURN(collectionId, config)
                    .flatMap(dcv -> dynCollService.buildCollection(dcv, linkCreator, config));
        }
        else {
            return staticCollectionService.convertRequest(collectionId, linkCreator, config);
        }
    }

    @Override
    public Try<ItemCollectionResponse> getItemsForCollection(
            String collectionId,
            Integer limit,
            Integer page,
            BBox bbox,
            String datetime,
            OGCFeatLinkCreator ogcFeatLinkCreator,
            Function<ItemSearchBody, SearchPageLinkCreator> searchPageLinkCreatorMaker
    ) {
        ConfigurationAccessor config = configurationAccessorFactory.makeConfigurationAccessor();

        final Try<ItemSearchBody> tryIsb = dynCollService.isDynamicCollectionValueURN(collectionId)
            ? getDynCollItemSearchBody(collectionId, limit, bbox, datetime, config)
            : getCollectionItemSearchBody(limit, bbox, datetime, List.of(collectionId));

        return tryIsb
            .flatMap(isb -> itemSearchService.search(isb, page, ogcFeatLinkCreator, searchPageLinkCreatorMaker.apply(isb)));
    }

    private Try<ItemSearchBody> getDynCollItemSearchBody(
            String collectionId,
            Integer limit,
            BBox bbox,
            String datetime,
            ConfigurationAccessor config
    ) {
        return dynCollService.parseDynamicCollectionsValueFromURN(collectionId, config)
                .flatMap(val -> getCollectionItemSearchBody(limit, bbox, datetime, null)
                        .map(isb -> isb.withQuery(dynCollService.toItemSearchBody(val).getQuery()))
                );
    }

    private Try<ItemSearchBody> getCollectionItemSearchBody(
            Integer limit,
            BBox bbox,
            String datetime,
            List<String> collections
    ) {
        return itemSearchBodyFactory.parseItemSearch(limit, bbox, datetime, collections, null, null, null, null);
    }


    @Override
    public List<Link> buildRootLinks(ConfigurationAccessor config, OGCFeatLinkCreator linkCreator) {
        return List.of(
            linkCreator.createRootLink(),
            linkCreator.createCollectionLinkWithRel(DEFAULT_STATIC_ID, config.getRootStaticCollectionName(), CHILD),
            linkCreator.createCollectionLinkWithRel(DEFAULT_DYNAMIC_ID, config.getRootDynamicCollectionName(), CHILD)
        )
        .flatMap(t -> t);
    }
}
