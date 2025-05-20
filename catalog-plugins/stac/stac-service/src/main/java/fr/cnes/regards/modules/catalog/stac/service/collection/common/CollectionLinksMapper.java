/*
 * Copyright 2017-2025 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.catalog.stac.service.collection.common;

import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.framework.urn.UniformResourceName;
import fr.cnes.regards.modules.catalog.stac.domain.spec.common.Link;
import fr.cnes.regards.modules.catalog.stac.domain.spec.common.Relation;
import fr.cnes.regards.modules.catalog.stac.service.collection.IdMappingService;
import fr.cnes.regards.modules.catalog.stac.service.link.OGCFeatLinkCreator;
import fr.cnes.regards.modules.dam.domain.entities.AbstractEntity;
import fr.cnes.regards.modules.dam.domain.entities.Collection;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.indexer.domain.criterion.StringMatchType;
import fr.cnes.regards.modules.opensearch.service.exception.OpenSearchUnknownParameter;
import fr.cnes.regards.modules.search.service.SearchException;
import io.vavr.collection.List;
import io.vavr.control.Option;
import org.springframework.stereotype.Service;

/**
 * Collection links mapper.
 *
 * @author Marc SORDI
 */
@Service
public class CollectionLinksMapper {

    private final CatalogSearchProxyService catalogSearchProxyService;

    private final IdMappingService idMappingService;

    public CollectionLinksMapper(CatalogSearchProxyService catalogSearchProxyService,
                                 IdMappingService idMappingService) {
        this.catalogSearchProxyService = catalogSearchProxyService;
        this.idMappingService = idMappingService;
    }

    public List<Link> getLinks(UniformResourceName urn,
                               OGCFeatLinkCreator linkCreator,
                               String providerId,
                               boolean disableParentDiscovery) throws SearchException, OpenSearchUnknownParameter {
        final List<Link> links;
        String urnString = urn.toString();
        if (urn.getEntityType().equals(EntityType.COLLECTION)) {
            List<AbstractEntity<?>> children = List.ofAll(catalogSearchProxyService.getSubCollectionsOrDatasets(
                urnString));

            links = List.of(linkCreator.createLandingPageLink(Relation.ROOT),
                            linkCreator.createCollectionLink(Relation.SELF,
                                                             idMappingService.getStacIdByUrn(urnString),
                                                             providerId),
                            getParentCollectionId(urnString, linkCreator, disableParentDiscovery),
                            getItemsLinks(urn, linkCreator),
                            // items link may not be accurate here
                            children.flatMap(child -> linkCreator.createCollectionLink(Relation.CHILD,
                                                                                       child.getIpId().toString(),
                                                                                       child.getLabel())))
                        .flatMap(t -> t);

        } else if (urn.getEntityType().equals(EntityType.DATASET)) {
            links = List.of(linkCreator.createLandingPageLink(Relation.ROOT),
                            linkCreator.createCollectionLink(Relation.SELF,
                                                             idMappingService.getStacIdByUrn(urnString),
                                                             providerId),
                            getParentCollectionId(urnString, linkCreator, disableParentDiscovery),
                            getItemsLinks(urn, linkCreator)).flatMap(t -> t);
        } else {
            links = List.empty();
        }
        return links;
    }

    private Option<Link> getItemsLinks(UniformResourceName resourceName, OGCFeatLinkCreator linkCreator) {
        return linkCreator.createCollectionItemsLink(Relation.ITEMS,
                                                     idMappingService.getStacIdByUrn(resourceName.toString()));
    }

    private List<Link> getParentCollectionId(String urn, OGCFeatLinkCreator linkCreator, boolean disableParentDiscovery)
        throws SearchException, OpenSearchUnknownParameter {
        if (disableParentDiscovery) {
            return List.empty();
        }
        List<String> parentCollectionsId = getParentCollectionsId(urn);
        return parentCollectionsId.map(x -> linkCreator.createCollectionLink(Relation.PARENT, x, "")).flatMap(t -> t);
    }

    private List<String> getParentCollectionsId(String urn) throws SearchException, OpenSearchUnknownParameter {
        ICriterion tags = ICriterion.contains("ipId", urn, StringMatchType.KEYWORD);
        List<Collection> collections = List.ofAll(catalogSearchProxyService.getEntities(tags, EntityType.COLLECTION));
        return collections.map(Collection::getIpId).map(Object::toString).map(idMappingService::getStacIdByUrn);
    }
}
