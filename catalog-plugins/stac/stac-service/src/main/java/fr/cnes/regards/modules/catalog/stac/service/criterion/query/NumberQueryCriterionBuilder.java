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

package fr.cnes.regards.modules.catalog.stac.service.criterion.query;

import fr.cnes.regards.modules.catalog.stac.domain.api.SearchBody.NumberQueryObject;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.conversion.AbstractPropertyConverter;
import fr.cnes.regards.modules.catalog.stac.domain.properties.conversion.IdentityPropertyConverter;
import fr.cnes.regards.modules.catalog.stac.service.collection.search.eodag.EODagParameters;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.model.domain.attributes.AttributeModel;
import io.vavr.collection.List;
import io.vavr.control.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Criterion builder for a {@link NumberQueryObject}
 */
public abstract class NumberQueryCriterionBuilder<T> extends AbstractQueryObjectCriterionBuilder<NumberQueryObject> {

    private static final Logger LOGGER = LoggerFactory.getLogger(NumberQueryCriterionBuilder.class);

    public NumberQueryCriterionBuilder(String stacPropName) {
        super(stacPropName);
    }

    @Override
    public Option<ICriterion> buildCriterion(AttributeModel attr,
                                             List<StacProperty> properties,
                                             NumberQueryObject queryObject) {

        // Get converter
        AbstractPropertyConverter<Double, Double> converter = getStacProperty(properties,
                                                                              stacPropName).map(StacProperty::getConverter)
                                                                                           .getOrElse(new IdentityPropertyConverter<>(
                                                                                               StacPropertyType.NUMBER));

        if (queryObject.getEq() != null) {
            return extractConvertedValue(converter, queryObject.getEq()).map(v -> eq(attr, v));
        } else if (queryObject.getNeq() != null) {
            return extractConvertedValue(converter, queryObject.getNeq()).map(v -> neq(attr, v));
        } else if (queryObject.getIn() != null && !queryObject.getIn().isEmpty()) {
            return extractConvertedValue(converter, queryObject.getIn()).map(in -> in(attr, in));
        } else {
            // Combine operators
            Option<Double> lower = Option.none(), upper = Option.none();
            boolean lowerInclusive = false, upperInclusive = false;
            if (queryObject.getGt() != null) {
                lower = extractConvertedValue(converter, queryObject.getGt());
            }
            if (queryObject.getGte() != null) {
                lower = extractConvertedValue(converter, queryObject.getGte());
                lowerInclusive = true;
            }
            if (queryObject.getLt() != null) {
                upper = extractConvertedValue(converter, queryObject.getLt());
            }
            if (queryObject.getLte() != null) {
                upper = extractConvertedValue(converter, queryObject.getLte());
                upperInclusive = true;
            }
            return Option.of(redispatchBetween(attr, lower, lowerInclusive, upper, upperInclusive));
        }
    }

    private ICriterion redispatchBetween(AttributeModel attr,
                                         Option<Double> lower,
                                         boolean lowerInclusive,
                                         Option<Double> upper,
                                         boolean upperInclusive) {
        if (lower.isDefined() && upper.isDefined()) {
            return between(attr, lower.get(), lowerInclusive, upper.get(), upperInclusive);
        } else if (lower.isDefined()) {
            return lowerInclusive ? gte(attr, lower.get()) : gt(attr, lower.get());
        } else if (upper.isDefined()) {
            return upperInclusive ? lte(attr, upper.get()) : lt(attr, upper.get());
        } else {
            throw new IllegalArgumentException(String.format("At least one criterion must be set for property %s",
                                                             stacPropName));
        }
    }

    protected abstract T convert(Double value);

    protected abstract ICriterion eq(AttributeModel attr, Double value);

    protected abstract ICriterion neq(AttributeModel attr, Double value);

    protected abstract ICriterion in(AttributeModel attr, List<Double> in);

    protected abstract ICriterion between(AttributeModel attr,
                                          Double lower,
                                          boolean lowerInclusive,
                                          Double upper,
                                          boolean upperInclusive);

    protected abstract ICriterion gt(AttributeModel attr, Double value);

    protected abstract ICriterion gte(AttributeModel attr, Double value);

    protected abstract ICriterion lt(AttributeModel attr, Double value);

    protected abstract ICriterion lte(AttributeModel attr, Double value);

    private Option<Double> extractConvertedValue(AbstractPropertyConverter<Double, Double> converter, Double lt) {
        return Option.of(lt).toTry().flatMap(converter::convertStacToRegards).toOption();
    }

    private Option<List<Double>> extractConvertedValue(AbstractPropertyConverter<Double, Double> converter,
                                                       List<Double> lt) {
        return Option.of(lt.flatMap(v -> extractConvertedValue(converter, v)).toList());
    }

    @Override
    public void buildEODagParameters(AttributeModel attr,
                                     EODagParameters parameters,
                                     List<StacProperty> properties,
                                     NumberQueryObject queryObject) {

        // Get converter
        AbstractPropertyConverter<Double, Double> converter = getStacProperty(properties,
                                                                              stacPropName).map(StacProperty::getConverter)
                                                                                           .getOrElse(new IdentityPropertyConverter<>(
                                                                                               StacPropertyType.NUMBER));

        if (queryObject.getEq() != null) {
            parameters.addExtras(stacPropName, attr.getType(), convert(queryObject.getEq()));
        } else {
            LOGGER.warn(EODAG_RESTRICTION_MESSAGE, stacPropName);
        }
    }
}
