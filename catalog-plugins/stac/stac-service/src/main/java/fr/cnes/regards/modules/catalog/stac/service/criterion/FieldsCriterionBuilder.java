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

import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.SearchBody.Fields;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.service.collection.search.eodag.EODagParameters;
import fr.cnes.regards.modules.dam.domain.entities.criterion.IFeatureCriterion;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import io.vavr.collection.List;
import io.vavr.control.Option;
import org.springframework.stereotype.Component;

/**
 * Build criteria for presence/absence of fields.
 */
@Component
public class FieldsCriterionBuilder implements CriterionBuilder<Fields> {

    @Override
    public Option<ICriterion> buildCriterion(List<StacProperty> properties, Fields fields) {
        if (fields == null) {
            return Option.none();
        }
        Option<ICriterion> includes = fields.getIncludes()
                                            .flatMap(inc -> propertyNameFor(properties, inc))
                                            .map(IFeatureCriterion::attributeExists)
                                            .reduceLeftOption(ICriterion::and);
        Option<ICriterion> excludes = fields.getExcludes()
                                            .flatMap(inc -> propertyNameFor(properties, inc))
                                            .map(IFeatureCriterion::attributeExists)
                                            .map(ICriterion::not)
                                            .reduceLeftOption(ICriterion::and);
        return withAll(List.of(includes, excludes).flatMap(opt -> opt), ICriterion::and);
    }

    @Override
    public void computeEODagParameters(EODagParameters parameters, List<StacProperty> properties, Fields value) {
        // Nothing to do here
    }
}
