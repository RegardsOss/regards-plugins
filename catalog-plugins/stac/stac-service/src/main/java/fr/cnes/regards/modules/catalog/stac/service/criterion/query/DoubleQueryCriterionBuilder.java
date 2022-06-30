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

public class DoubleQueryCriterionBuilder extends NumberQueryCriterionBuilder<Double> {

    public static final double DOUBLE_COMPARISON_PRECISION = 1e-6;

    public DoubleQueryCriterionBuilder(String stacPropName) {
        super(stacPropName);
    }

    @Override
    protected Double convert(Double value) {
        return value;
    }

    @Override
    protected ICriterion eq(AttributeModel attr, Double value) {
        return IFeatureCriterion.eq(attr, value, DOUBLE_COMPARISON_PRECISION);
    }

    @Override
    protected ICriterion neq(AttributeModel attr, Double value) {
        return IFeatureCriterion.ne(attr, value, DOUBLE_COMPARISON_PRECISION);
    }

    @Override
    protected ICriterion in(AttributeModel attr, List<Double> in) {
        return IFeatureCriterion.in(attr,
                                    in.toJavaStream().mapToDouble(Double::doubleValue).toArray(),
                                    DOUBLE_COMPARISON_PRECISION);
    }

    @Override
    protected ICriterion between(AttributeModel attr,
                                 Double lower,
                                 boolean lowerInclusive,
                                 Double upper,
                                 boolean upperInclusive) {
        return IFeatureCriterion.between(attr, lower, lowerInclusive, upper, upperInclusive);
    }

    @Override
    protected ICriterion gt(AttributeModel attr, Double value) {
        return IFeatureCriterion.gt(attr, value);
    }

    @Override
    protected ICriterion gte(AttributeModel attr, Double value) {
        return IFeatureCriterion.ge(attr, value);
    }

    @Override
    protected ICriterion lt(AttributeModel attr, Double value) {
        return IFeatureCriterion.lt(attr, value);
    }

    @Override
    protected ICriterion lte(AttributeModel attr, Double value) {
        return IFeatureCriterion.le(attr, value);
    }
}
