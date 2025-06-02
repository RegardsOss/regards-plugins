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

import static fr.cnes.regards.modules.catalog.stac.domain.StacProperties.ID_PROPERTY_NAME;

/**
 * Build criteria for list of IDs.
 */
@Component
public class IdentitiesCriterionBuilder implements CriterionBuilder<List<String>> {

    @Override
    public Option<ICriterion> buildCriterion(List<StacProperty> properties, List<String> ids) {
        // If no condition has been set: no criterion
        if (ids == null) {
            return Option.none();
        }
        // If no IDs should be matched: return a criterion that never matches
        // (this can happen if the ID parameter refers to items/collections that do not exist:
        // the resolved list of URNs is empty), and no item should be returned at all.
        if (ids.isEmpty()) {
            return Option.of(ICriterion.not(ICriterion.all()));
        }
        return withAll(ids.map(id -> ICriterion.eq(ID_PROPERTY_NAME, id, StringMatchType.KEYWORD)), ICriterion::or);
    }

    @Override
    public void computeEODagParameters(EODagParameters parameters, List<StacProperty> properties, List<String> value) {
        // Nothing to do : no id selection with EODag at the moment
    }
}
