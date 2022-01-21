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

package fr.cnes.regards.modules.catalog.stac.service.criterion.query;

import static fr.cnes.regards.modules.dam.domain.entities.criterion.IFeatureCriterion.eq;

import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.SearchBody.NumberQueryObject;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.conversion.AbstractPropertyConverter;
import fr.cnes.regards.modules.catalog.stac.domain.properties.conversion.IdentityPropertyConverter;
import fr.cnes.regards.modules.catalog.stac.service.criterion.query.number.DoubleInterval;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.model.domain.attributes.AttributeModel;
import io.vavr.collection.List;
import io.vavr.control.Option;

/**
 * Criterion builder for a {@link NumberQueryObject}
 */
public class NumberQueryCriterionBuilder extends AbstractQueryObjectCriterionBuilder<NumberQueryObject> {

    public static final double DOUBLE_COMPARISON_PRECISION = 1e-6;

    public NumberQueryCriterionBuilder(String stacPropName) {
        super(stacPropName);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Option<ICriterion> buildCriterion(AttributeModel attr, List<StacProperty> properties,
            NumberQueryObject queryObject) {
        AbstractPropertyConverter<Double, Double> converter = getStacProperty(properties, stacPropName)
                .map(StacProperty::getConverter).getOrElse(new IdentityPropertyConverter<>(StacPropertyType.NUMBER));

        return andAllPresent(combineIntervals(attr, List
                .of(extractConvertedValue(converter, queryObject.getEq()).map(DoubleInterval::eq),
                    extractConvertedValue(converter, queryObject.getLt()).map(DoubleInterval::lt),
                    extractConvertedValue(converter, queryObject.getLte()).map(DoubleInterval::lte),
                    extractConvertedValue(converter, queryObject.getGt()).map(DoubleInterval::gt),
                    extractConvertedValue(converter, queryObject.getGte()).map(DoubleInterval::gte))),
                             combineIntervals(attr, List.of(Option.of(queryObject.getNeq()).map(DoubleInterval::eq)))
                                     .map(ICriterion::not),
                             Option.of(queryObject.getIn()).flatMap(in -> in
                                     .map(d -> eq(attr, d, DOUBLE_COMPARISON_PRECISION)).reduceLeftOption(ICriterion::or)));
    }

    public Option<Double> extractConvertedValue(AbstractPropertyConverter<Double, Double> converter, Double lt) {
        return Option.of(lt).toTry().flatMap(converter::convertStacToRegards).toOption();
    }

    private Option<ICriterion> combineIntervals(AttributeModel attr, List<Option<DoubleInterval>> intervals) {
        return intervals.flatMap(opt -> opt).reduceLeftOption(DoubleInterval::combine)
                .map(i -> i.toCriterion(attr.getFullJsonPath()));
    }
}
