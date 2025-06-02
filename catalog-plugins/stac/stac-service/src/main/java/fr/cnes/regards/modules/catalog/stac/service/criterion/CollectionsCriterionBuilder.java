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

package fr.cnes.regards.modules.catalog.stac.service.criterion;

import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.service.collection.search.eodag.EODagParameters;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.indexer.domain.criterion.StringMatchType;
import io.vavr.collection.List;
import io.vavr.control.Option;
import org.springframework.stereotype.Component;

import static fr.cnes.regards.modules.catalog.stac.domain.StacProperties.TAGS_PROPERTY_NAME;

/**
 * Build criteria for list of collections.
 */
@Component
public class CollectionsCriterionBuilder implements CriterionBuilder<List<String>> {

    @Override
    public Option<ICriterion> buildCriterion(List<StacProperty> properties, List<String> collections) {
        // If no condition has been set: no criterion
        if (collections == null) {
            return Option.none();
        }
        // If no collection should be matched: return a criterion that never matches
        // (this can happen if the collection parameter refers to a collection that does not exist:
        // the resolved list of URNs is empty), and no item should be returned at all.
        if (collections.isEmpty()) {
            return Option.of(ICriterion.not(ICriterion.all()));
        }
        // In the index, collections / dataset a feature belongs to is stored in the "tags" attribute.
        return collections.size() == 1 ?
            Option.of(ICriterion.contains(TAGS_PROPERTY_NAME, collections.get(0), StringMatchType.KEYWORD)) :
            Option.of(ICriterion.or(collections.map(c -> ICriterion.contains(TAGS_PROPERTY_NAME,
                                                                             c,
                                                                             StringMatchType.KEYWORD))));
    }

    @Override
    public void computeEODagParameters(EODagParameters parameters,
                                       List<StacProperty> properties,
                                       List<String> collections) {
        // Nothing to do : EODag search is already done in a collection so this parameter should not be useful
    }

}
