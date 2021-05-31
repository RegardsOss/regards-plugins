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
package fr.cnes.regards.modules.catalog.stac.service.collection.search;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.CollectionsResponse;
import fr.cnes.regards.modules.dam.domain.entities.StaticProperties;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.opensearch.service.exception.OpenSearchUnknownParameter;
import fr.cnes.regards.modules.search.domain.plugin.SearchType;
import fr.cnes.regards.modules.search.service.ICatalogSearchService;
import fr.cnes.regards.modules.search.service.SearchException;
import io.vavr.control.Try;

/**
 *
 * FIXME WIP
 * @author Marc SORDI
 *
 */
@Service
public class CollectionSearchServiceImpl implements CollectionSearchService {

    private static final String DATASET_PREFIX = "URN:AIP:DATASET";

    @Autowired
    private ICatalogSearchService catalogSearchService;

    // FIXME remove exception ... use trying
    @Override
    public Try<CollectionsResponse> search() throws SearchException, OpenSearchUnknownParameter {
        // TODO

        // Build collection search criteria
        ICriterion collectionCriteria = null;
        // TODO ICriterion collectionCriteria = critBuilder.buildCriterion(stacProperties, itemSearchBody).getOrElse(ICriterion.all());
        // Build item search criteria
        ICriterion itemCriteria = null;
        // TODO  = critBuilder.buildCriterion(stacProperties, itemSearchBody).getOrElse(ICriterion.all());
        // Build page parameters
        // TODO Pageable pageable = pageable(itemSearchBody, page, stacProperties);

        // FIXME test d'algo
        if (!itemCriteria.isEmpty()) {
            // Search for all matching collections with item query parameters
            //            FacetPage<Dataset> datasets = catalogSearchService.search(itemCriteria, SearchType.DATAOBJECTS_RETURN_DATASETS,
            //                                                                      null, null);
            String propertyPath = "tags";
            String partialText = "URN:AIP:DATASET";
            // FIXME optimize tag search
            List<String> matchingKeywords = catalogSearchService
                    .retrieveEnumeratedPropertyValues(itemCriteria, SearchType.DATAOBJECTS, StaticProperties.FEATURE_TAGS,
                                                      500, DATASET_PREFIX);
            // TODO filter to get only DATASET TAGS! Maybe elastic can do that directly?
            List<String> matchingDatasets = matchingKeywords.stream().filter(k -> k.startsWith(DATASET_PREFIX))
                    .collect(Collectors.toList());
            // Add it to the collection search criteria
            // TODO : use elastic _id or ipId?
        }

        // Search for all matching collections with collection query parameters
        // possibly incorporating the filter on the collections from the previous search
        // TODO
        return null;
    }

    //    List<StacProperty> stacProperties = configurationAccessorFactory.makeConfigurationAccessor().getStacProperties();
    //    ICriterion crit = critBuilder.buildCriterion(stacProperties, itemSearchBody).getOrElse(ICriterion.all());
    //    debug(LOGGER, "Search request: {}\n\tCriterion: {}", itemSearchBody, crit);
    //
    //    Pageable pageable = pageable(itemSearchBody, page, stacProperties);
    //    return trying(() -> catalogSearchService.<AbstractEntity<? extends EntityFeature>>search(crit, SearchType.DATAOBJECTS, null, pageable))
    //        .mapFailure(SEARCH, () -> format("Search failure for page %d of %s", page, itemSearchBody))
    //        .flatMap(facetPage -> extractItemCollection(facetPage, stacProperties, featLinkCreator, searchPageLinkCreator));

}
