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

package fr.cnes.regards.modules.catalog.stac.service;

import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemCollectionResponse;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.RegardsPropertyAccessor;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Item;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Asset;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessorFactory;
import fr.cnes.regards.modules.catalog.stac.service.link.OGCFeatLinkCreator;
import fr.cnes.regards.modules.catalog.stac.service.link.SearchPageLinkCreator;
import fr.cnes.regards.modules.dam.domain.entities.DataObject;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import static fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody.SortBy.Direction.ASC;
import static org.springframework.data.domain.Sort.Order.asc;
import static org.springframework.data.domain.Sort.Order.desc;

/**
 * Implementation for {@link ItemSearchService}
 */
@Service
public class ItemSearchServiceImpl implements ItemSearchService {

    private static final HashSet<String> SEARCH_EXTENSIONS = HashSet.empty();
    private static final Logger LOGGER = LoggerFactory.getLogger(ItemSearchServiceImpl.class);

    private final StacSearchCriterionBuilder critBuilder;
    private final CatalogSearchService catalogSearchService;
    private final ConfigurationAccessorFactory configurationAccessorFactory;
    private final RegardsFeatureToStacItemConverter itemConverter;

    // @formatter:off

    @Autowired
    public ItemSearchServiceImpl(
            StacSearchCriterionBuilder critBuilder,
            CatalogSearchService catalogSearchService,
            ConfigurationAccessorFactory configurationAccessorFactory,
            RegardsFeatureToStacItemConverter itemConverter
    ) {
        this.critBuilder = critBuilder;
        this.catalogSearchService = catalogSearchService;
        this.configurationAccessorFactory = configurationAccessorFactory;
        this.itemConverter = itemConverter;
    }

    @Override
    public Try<ItemCollectionResponse> search(
        ItemSearchBody itemSearchBody,
        Integer page,
        OGCFeatLinkCreator featLinkCreator,
        SearchPageLinkCreator searchPageLinkCreator
    ) {
        List<StacProperty> stacProperties = configurationAccessorFactory.makeConfigurationAccessor().getStacProperties();
        ICriterion crit = critBuilder.buildCriterion(stacProperties, itemSearchBody).getOrElse(ICriterion.all());
        LOGGER.debug("Search request: {}\n\tCriterion: {}", itemSearchBody, crit);

        Pageable pageable = pageable(itemSearchBody, page, stacProperties);

        return Try.of(() -> catalogSearchService.<DataObject>search(crit, SearchType.DATAOBJECTS, null, pageable))
            .flatMap(facetPage -> extractItemCollection(facetPage, stacProperties, featLinkCreator, searchPageLinkCreator));
    }

    private Try<ItemCollectionResponse> extractItemCollection(
            FacetPage<DataObject> facetPage,
            List<StacProperty> stacProperties,
            OGCFeatLinkCreator featLinkCreator,
            SearchPageLinkCreator searchPageLinkCreator
    ) {
        return Try.of(() -> {
            ItemCollectionResponse.Context context = new ItemCollectionResponse.Context(
                    facetPage.getNumberOfElements(),
                    facetPage.getPageable().getPageSize(),
                    facetPage.getTotalElements()
            );
            ItemCollectionResponse response = new ItemCollectionResponse(
                SEARCH_EXTENSIONS,
                extractStacItems(Stream.ofAll(facetPage.get()), stacProperties, featLinkCreator),
                List.empty(), // resolved later
                context
            );
            return response.withLinks(extractLinks(searchPageLinkCreator, response));
        });
    }

    private List<Link> extractLinks(SearchPageLinkCreator searchPageLinkCreator, ItemCollectionResponse response) {
        return List.of(
            searchPageLinkCreator.createSelfPageLink(response)
                .map(uri -> new Link(uri, Link.Relations.SELF, Asset.MediaType.APPLICATION_JSON, "this search page")),
            searchPageLinkCreator.createNextPageLink(response)
                .map(uri -> new Link(uri, Link.Relations.NEXT, Asset.MediaType.APPLICATION_JSON, "next search page")),
            searchPageLinkCreator.createPrevPageLink(response)
                .map(uri -> new Link(uri, Link.Relations.PREV, Asset.MediaType.APPLICATION_JSON, "prev search page"))
        )
        .flatMap(l -> l);
    }

    private List<Item> extractStacItems(
            Stream<DataObject> dataObjectStream,
            List<StacProperty> stacProperties,
            OGCFeatLinkCreator featLinkCreator
    ) {
        return dataObjectStream
            .flatMap(dataObject ->
                itemConverter.convertFeatureToItem(stacProperties, featLinkCreator, dataObject)
            )
            .toList();
    }


    private Pageable pageable(ItemSearchBody itemSearchBody, Integer page, List<StacProperty> stacProperties) {
        return PageRequest.of(page, itemSearchBody.getLimit(), sort(itemSearchBody.getSortBy(), stacProperties));
    }

    private Sort sort(List<ItemSearchBody.SortBy> sortBy, List<StacProperty> stacProperties) {
        return Sort.by(sortBy.map(sb -> order(stacProperties, sb)).toJavaList());
    }

    private Sort.Order order(List<StacProperty> stacProperties, ItemSearchBody.SortBy sb) {
        return sb.getDirection() == ASC
            ? asc(regardsPropName(sb.getField(), stacProperties))
            : desc(regardsPropName(sb.getField(), stacProperties));
    }

    private String regardsPropName(String field, List<StacProperty> stacProperties) {
        return stacProperties.find(sp -> sp.getStacPropertyName().equals(field))
            .map(StacProperty::getRegardsPropertyAccessor)
            .map(RegardsPropertyAccessor::getRegardsAttributeName) // TODO: this does not work with internal JSON properties
            .getOrElse(field);
    }

    // @formatter:on
}
