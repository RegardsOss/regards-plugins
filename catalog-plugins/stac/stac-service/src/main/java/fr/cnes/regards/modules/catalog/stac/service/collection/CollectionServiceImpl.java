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
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Collection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection.Extent;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link;
import fr.cnes.regards.modules.catalog.stac.service.collection.dynamic.DynamicCollectionService;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.link.OGCFeatLinkCreator;
import fr.cnes.regards.modules.catalog.stac.service.link.StacLinkCreator;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Try;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Base implementation for {@link CollectionService}.
 */
@Service
public class CollectionServiceImpl implements CollectionService, StacLinkCreator {

    public static final String DEFAULT_DYNAMIC_ID = "dynamic";
    public static final String DEFAULT_STATIC_ID = "static";

    private final DynamicCollectionService dynCollService;

    @Autowired
    public CollectionServiceImpl(DynamicCollectionService dynCollService) {
        this.dynCollService = dynCollService;
    }

    @Override
    public boolean hasDynamicCollections(List<StacProperty> properties) {
        return properties.find(p -> Objects.nonNull(p.getDynamicCollectionLevel())).isDefined();
    }

    @Override
    public String getRootDynamicCollectionName(ConfigurationAccessor config) {
        return config.getRootDynamicCollectionName();
    }

    @Override
    public String getRootStaticCollectionName(ConfigurationAccessor config) {
        return config.getRootStaticCollectionName();
    }

    @Override
    public Collection buildRootDynamicCollection(OGCFeatLinkCreator linkCreator, ConfigurationAccessor config) {
        String name = getRootDynamicCollectionName(config);
        return new Collection(
                StacSpecConstants.Version.STAC_SPEC_VERSION,
                List.empty(),
                name, name, "Dynamic collections",
                dynamicCollectionLinks(linkCreator),
                List.empty(),
                "",
                List.empty(),
                Extent.maximalExtent(), // no extent at this level
                HashMap.empty() // no summaries at this level
        );
    }

    private List<Link> dynamicCollectionLinks(OGCFeatLinkCreator linkCreator) {
        return List.empty(); // TODO
    }

    @Override
    public Collection buildRootStaticCollection(OGCFeatLinkCreator linkCreator,ConfigurationAccessor config) {
        String name = getRootStaticCollectionName(config);
        return new Collection(
                StacSpecConstants.Version.STAC_SPEC_VERSION,
                List.empty(),
                name, name, "Static collections",
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
}
