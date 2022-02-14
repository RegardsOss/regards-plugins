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
package fr.cnes.regards.modules.catalog.stac.service.criterion.query;

import fr.cnes.regards.modules.dam.domain.entities.criterion.IFeatureCriterion;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.model.domain.attributes.AttributeModel;
import io.vavr.collection.List;

import java.util.function.Function;

public class IntegerQueryCriterionBuilder extends NumberQueryCriterionBuilder {

    private final Function<Double, Integer> getValueFn = Double::intValue;

    public IntegerQueryCriterionBuilder(String stacPropName) {
        super(stacPropName);
    }

    @Override
    protected ICriterion eq(AttributeModel attr, Double value) {
        return IFeatureCriterion.eq(attr, getValueFn.apply(value));
    }

    @Override
    protected ICriterion neq(AttributeModel attr, Double value) {
        return IFeatureCriterion.ne(attr, getValueFn.apply(value));
    }

    @Override
    protected ICriterion in(AttributeModel attr, List<Double> in) {
        return IFeatureCriterion.in(attr, in.map(getValueFn).toJavaStream().mapToInt(Integer::intValue).toArray());
    }

    @Override
    protected ICriterion between(AttributeModel attr, Double lower, boolean lowerInclusive, Double upper, boolean upperInclusive) {
        return IFeatureCriterion.between(attr, getValueFn.apply(lower), lowerInclusive, getValueFn.apply(upper), upperInclusive);
    }

    @Override
    protected ICriterion gt(AttributeModel attr, Double value) {
        return IFeatureCriterion.gt(attr, getValueFn.apply(value));
    }

    @Override
    protected ICriterion gte(AttributeModel attr, Double value) {
        return IFeatureCriterion.ge(attr, getValueFn.apply(value));
    }

    @Override
    protected ICriterion lt(AttributeModel attr, Double value) {
        return IFeatureCriterion.lt(attr, getValueFn.apply(value));
    }

    @Override
    protected ICriterion lte(AttributeModel attr, Double value) {
        return IFeatureCriterion.le(attr, getValueFn.apply(value));
    }
}
