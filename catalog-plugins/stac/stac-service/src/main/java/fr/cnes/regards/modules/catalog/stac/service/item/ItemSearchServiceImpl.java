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

package fr.cnes.regards.modules.catalog.stac.service.item;

import fr.cnes.regards.framework.urn.UniformResourceName;
import fr.cnes.regards.modules.catalog.stac.domain.StacConstants;
import fr.cnes.regards.modules.catalog.stac.domain.api.Context;
import fr.cnes.regards.modules.catalog.stac.domain.api.Fields;
import fr.cnes.regards.modules.catalog.stac.domain.api.ItemCollectionResponse;
import fr.cnes.regards.modules.catalog.stac.domain.api.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.spec.Item;
import fr.cnes.regards.modules.catalog.stac.domain.spec.common.Link;
import fr.cnes.regards.modules.catalog.stac.domain.spec.common.Relation;
import fr.cnes.regards.modules.catalog.stac.service.collection.IdMappingService;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessorFactory;
import fr.cnes.regards.modules.catalog.stac.service.criterion.StacSearchCriterionBuilder;
import fr.cnes.regards.modules.catalog.stac.service.link.OGCFeatLinkCreator;
import fr.cnes.regards.modules.catalog.stac.service.link.SearchPageLinkCreator;
import fr.cnes.regards.modules.catalog.stac.service.search.AbstractSearchService;
import fr.cnes.regards.modules.dam.domain.entities.AbstractEntity;
import fr.cnes.regards.modules.dam.domain.entities.feature.EntityFeature;
import fr.cnes.regards.modules.indexer.dao.FacetPage;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.search.domain.plugin.SearchType;
import fr.cnes.regards.modules.search.service.CatalogSearchService;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Stream;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Map;

import static fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType.*;
import static fr.cnes.regards.modules.catalog.stac.domain.error.StacRequestCorrelationId.debug;
import static fr.cnes.regards.modules.catalog.stac.domain.utils.TryDSL.trying;
import static java.lang.String.format;

/**
 * Implementation for {@link ItemSearchService}
 */
@Service
public class ItemSearchServiceImpl extends AbstractSearchService implements ItemSearchService {

    private static final HashSet<String> SEARCH_EXTENSIONS = HashSet.empty();

    private static final Logger LOGGER = LoggerFactory.getLogger(ItemSearchServiceImpl.class);

    private final StacSearchCriterionBuilder searchCriterionBuilder;

    private final CatalogSearchService catalogSearchService;

    private final ConfigurationAccessorFactory configurationAccessorFactory;

    private final RegardsFeatureToStacItemConverter itemConverter;

    private final IdMappingService idMappingService;

    @Autowired
    public ItemSearchServiceImpl(StacSearchCriterionBuilder critBuilder,
                                 CatalogSearchService catalogSearchService,
                                 ConfigurationAccessorFactory configurationAccessorFactory,
                                 RegardsFeatureToStacItemConverter itemConverter,
                                 IdMappingService idMappingService) {
        this.searchCriterionBuilder = critBuilder;
        this.catalogSearchService = catalogSearchService;
        this.configurationAccessorFactory = configurationAccessorFactory;
        this.itemConverter = itemConverter;
        this.idMappingService = idMappingService;
    }

    @Override
    public Try<ItemCollectionResponse> search(ItemSearchBody itemSearchBody,
                                              Integer page,
                                              OGCFeatLinkCreator featLinkCreator,
                                              SearchPageLinkCreator searchPageLinkCreator,
                                              Map<String, String> headers) {
        List<StacProperty> stacProperties = configurationAccessorFactory.makeConfigurationAccessor()
                                                                        .getStacProperties();
        ICriterion criterion = searchCriterionBuilder.buildCriterion(stacProperties, itemSearchBody)
                                                     .getOrElse(ICriterion.all());
        debug(LOGGER, "Search request: {}\n\tCriterion: {}", itemSearchBody, criterion);

        Pageable pageable = pageable(itemSearchBody.getLimit(), page, itemSearchBody.getSortBy(), stacProperties);
        return trying(() -> catalogSearchService.<AbstractEntity<? extends EntityFeature>>search(criterion,
                                                                                                 SearchType.DATAOBJECTS,
                                                                                                 null,
                                                                                                 pageable)).mapFailure(
                                                                                                               SEARCH,
                                                                                                               () -> format("Search failure for page %d of %s", page, itemSearchBody))
                                                                                                           .flatMap(
                                                                                                               facetPage -> extractItemCollection(
                                                                                                                   facetPage,
                                                                                                                   stacProperties,
                                                                                                                   itemSearchBody.getFields(),
                                                                                                                   featLinkCreator,
                                                                                                                   searchPageLinkCreator,
                                                                                                                   headers));
    }

    @Override
    public Try<Item> searchById(String featureId, OGCFeatLinkCreator featLinkCreator) {
        String itemId = idMappingService.geItemUrnFromId(featureId);
        List<StacProperty> stacProperties = configurationAccessorFactory.makeConfigurationAccessor()
                                                                        .getStacProperties();
        return trying(() -> UniformResourceName.fromString(itemId)).map(urn -> (AbstractEntity<? extends EntityFeature>) catalogSearchService.get(
                                                                       urn))
                                                                   .mapFailure(SEARCH_ITEM,
                                                                               () -> format(
                                                                                   "Search failure on item id %s",
                                                                                   itemId))
                                                                   .flatMap(entity -> itemConverter.convertFeatureToItem(
                                                                       stacProperties,
                                                                       null,
                                                                       featLinkCreator,
                                                                       entity));
    }

    private Try<ItemCollectionResponse> extractItemCollection(FacetPage<AbstractEntity<? extends EntityFeature>> facetPage,
                                                              List<StacProperty> stacProperties,
                                                              Fields fields,
                                                              OGCFeatLinkCreator featLinkCreator,
                                                              SearchPageLinkCreator searchPageLinkCreator,
                                                              Map<String, String> headers) {
        return trying(() -> {
            Context context = new Context(facetPage.getNumberOfElements(),
                                          facetPage.getPageable().getPageSize(),
                                          facetPage.getTotalElements());
            return new ItemCollectionResponse(SEARCH_EXTENSIONS,
                                              extractStacItems(Stream.ofAll(facetPage.get()),
                                                               stacProperties,
                                                               fields,
                                                               featLinkCreator),
                                              extractItemsLinks(featLinkCreator,
                                                                searchPageLinkCreator,
                                                                facetPage,
                                                                headers),
                                              context,
                                              facetPage.getTotalElements(),
                                              (long) facetPage.getNumberOfElements());
        }).mapFailure(ITEMCOLLECTIONRESPONSE_CONSTRUCTION, () -> "Failed to create ItemCollectionResponse");
    }

    private List<Link> extractItemsLinks(OGCFeatLinkCreator featLinkCreator,
                                         SearchPageLinkCreator searchPageLinkCreator,
                                         FacetPage<AbstractEntity<? extends EntityFeature>> facetPage,
                                         Map<String, String> headers) {
        return List.of(featLinkCreator.createSearchLink(Relation.ROOT))
                   .flatMap(t -> t)
                   .appendAll(extractLinks(searchPageLinkCreator,
                                           facetPage,
                                           StacConstants.APPLICATION_GEO_JSON_MEDIA_TYPE,
                                           headers));
    }

    private List<Item> extractStacItems(Stream<AbstractEntity<? extends EntityFeature>> entityStream,
                                        List<StacProperty> stacProperties,
                                        Fields fields,
                                        OGCFeatLinkCreator featLinkCreator) {
        return entityStream.flatMap(entity -> itemConverter.convertFeatureToItem(stacProperties,
                                                                                 fields,
                                                                                 featLinkCreator,
                                                                                 entity)).toList();
    }
}
