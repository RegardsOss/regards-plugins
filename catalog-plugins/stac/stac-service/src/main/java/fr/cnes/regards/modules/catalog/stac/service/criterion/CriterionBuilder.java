/*
 * Copyright 2017-2022 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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

import fr.cnes.regards.modules.catalog.stac.domain.properties.RegardsPropertyAccessor;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.model.domain.attributes.AttributeModel;
import io.vavr.collection.List;
import io.vavr.control.Option;

import java.util.function.Function;

/**
 * Generic interface for criterion builder.
 */
public interface CriterionBuilder<T> {

    Option<ICriterion> buildCriterion(List<StacProperty> properties, T value);

    default Option<ICriterion> withAll(List<ICriterion> criteria, Function<Iterable<ICriterion>, ICriterion> combinator) {
        return criteria.isEmpty() ? Option.none() : criteria.size() == 1 ? Option.of(criteria.get(0)) : Option.of(combinator.apply(criteria));
    }

    default Option<ICriterion> andAllPresent(Option<ICriterion>... criteria) {
        return withAll(List.of(criteria).flatMap(opt -> opt), ICriterion::and);
    }

    default Option<AttributeModel> propertyNameFor(List<StacProperty> properties, String attrName) {
        return properties.find(p -> attrName.equals(p.getStacPropertyName())).map(StacProperty::getRegardsPropertyAccessor)
                .map(RegardsPropertyAccessor::getAttributeModel);
    }

    default Option<StacProperty> getStacProperty(List<StacProperty> properties, String attrName) {
        return properties.find(p -> attrName.equals(p.getStacPropertyName()));
    }
}
