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
package fr.cnes.regards.modules.catalog.stac.service.collection.timeline.builder;

import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.extension.searchcol.CollectionSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.extension.searchcol.FiltersByCollection;
import fr.cnes.regards.modules.catalog.stac.domain.error.StacException;
import fr.cnes.regards.modules.catalog.stac.domain.error.StacFailureType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.service.collection.IdMappingService;
import fr.cnes.regards.modules.catalog.stac.service.criterion.StacSearchCriterionBuilder;
import fr.cnes.regards.modules.dam.domain.entities.StaticProperties;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.indexer.domain.criterion.StringMatchType;
import io.vavr.collection.List;
import org.springframework.stereotype.Service;

@Service
public class TimelineCriteriaHelper {

    private final StacSearchCriterionBuilder searchCriterionBuilder;

    private final IdMappingService idMappingService;

    private TimelineCriteriaHelper(StacSearchCriterionBuilder searchCriterionBuilder,
                                   IdMappingService idMappingService) {
        this.searchCriterionBuilder = searchCriterionBuilder;
        this.idMappingService = idMappingService;
    }

    public ICriterion getTimelineCriteria(FiltersByCollection.CollectionFilters collectionFilters,
                                           List<StacProperty> itemStacProperties) {

        CollectionSearchBody.CollectionItemSearchBody collectionItemSearchBody = collectionFilters.getFilters()
                                                                                 == null ?
            CollectionSearchBody.CollectionItemSearchBody.builder().build() :
            collectionFilters.getFilters();

        String urn_tag = idMappingService.getUrnByStacId(collectionFilters.getCollectionId());
        if (urn_tag == null) {
            throw new StacException(String.format("Unknown collection identifier %s",
                                                  collectionFilters.getCollectionId()),
                                    null,
                                    StacFailureType.MAPPING_ID_FAILURE);
        }

        return ICriterion.and(ICriterion.eq(StaticProperties.FEATURE_TAGS, urn_tag, StringMatchType.KEYWORD),
                              searchCriterionBuilder.buildCriterion(itemStacProperties, collectionItemSearchBody)
                                                    .getOrElse(ICriterion.all()));

    }
}
