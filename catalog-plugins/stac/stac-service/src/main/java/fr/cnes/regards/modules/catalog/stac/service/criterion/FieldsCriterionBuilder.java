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

package fr.cnes.regards.modules.catalog.stac.service.criterion;

import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody.Fields;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import io.vavr.collection.List;
import io.vavr.control.Option;
import org.springframework.stereotype.Component;

import static fr.cnes.regards.modules.catalog.stac.service.criterion.CriterionBuilderHelper.withAll;

/**
 * Build criteria for presence/absence of fields.
 */
@Component
public class FieldsCriterionBuilder implements CriterionBuilder<Fields> {

    @Override
    public Option<ICriterion> buildCriterion(List<StacProperty> properties, Fields fields) {
        Option<ICriterion> includes = fields.getIncludes()
                .map(inc -> propertyNameFor(properties, inc))
                .map(ICriterion::attributeExists)
                .reduceLeftOption(ICriterion::and);
        Option<ICriterion> excludes = fields.getExcludes()
                .map(inc -> propertyNameFor(properties, inc))
                .map(ICriterion::attributeExists)
                .map(ICriterion::not)
                .reduceLeftOption(ICriterion::and);
        return withAll(
                List.of(includes, excludes).flatMap(opt -> opt),
                ICriterion::and
        );
    }

    private String propertyNameFor(List<StacProperty> properties, String attrName) {
        return properties.find(p -> attrName.equals(p.getStacPropertyName()))
                .map(StacProperty::getModelAttributeName)
                .getOrElse(attrName);
    }

}
