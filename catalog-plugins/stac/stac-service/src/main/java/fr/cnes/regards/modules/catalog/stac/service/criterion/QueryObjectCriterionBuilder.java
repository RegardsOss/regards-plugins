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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import fr.cnes.regards.modules.catalog.stac.domain.api.SearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.service.collection.search.eodag.EODagParameters;
import fr.cnes.regards.modules.catalog.stac.service.criterion.query.*;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.model.domain.attributes.AttributeModel;
import fr.cnes.regards.modules.model.dto.properties.PropertyType;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static fr.cnes.regards.modules.catalog.stac.domain.error.StacRequestCorrelationId.info;
import static fr.cnes.regards.modules.catalog.stac.domain.error.StacRequestCorrelationId.warn;

/**
 * Build criteria from query objects.
 */
@Component
public class QueryObjectCriterionBuilder implements CriterionBuilder<Map<String, SearchBody.QueryObject>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(QueryObjectCriterionBuilder.class);

    private static final ImmutableSet<PropertyType> ACCEPTED_INTEGER_TYPES = Sets.immutableEnumSet(PropertyType.INTEGER,
                                                                                                   PropertyType.INTEGER_ARRAY,
                                                                                                   PropertyType.INTEGER_INTERVAL);

    private static final ImmutableSet<PropertyType> ACCEPTED_LONG_TYPES = Sets.immutableEnumSet(PropertyType.LONG,
                                                                                                PropertyType.LONG_ARRAY,
                                                                                                PropertyType.LONG_INTERVAL);

    private static final ImmutableSet<PropertyType> ACCEPTED_DOUBLE_TYPES = Sets.immutableEnumSet(PropertyType.DOUBLE,
                                                                                                  PropertyType.DOUBLE_ARRAY,
                                                                                                  PropertyType.DOUBLE_INTERVAL);

    private static final ImmutableSet<PropertyType> ACCEPTED_DATE_TYPES = Sets.immutableEnumSet(PropertyType.DATE_ISO8601,
                                                                                                PropertyType.DATE_ARRAY,
                                                                                                PropertyType.DATE_INTERVAL);

    @Override
    public Option<ICriterion> buildCriterion(List<StacProperty> properties,
                                             Map<String, SearchBody.QueryObject> queryObjects) {
        if (queryObjects == null || queryObjects.isEmpty()) {
            return Option.none();
        }

        List<ICriterion> criteria = queryObjects.flatMap(kv -> routeQueryObjectCriterion(properties, kv._1(), kv._2()))
                                                .toList();
        return withAll(criteria, ICriterion::and);
    }

    @Override
    public void computeEODagParameters(EODagParameters parameters,
                                       List<StacProperty> properties,
                                       Map<String, SearchBody.QueryObject> queryObjects) {
        if (queryObjects != null && !queryObjects.isEmpty()) {
            for (Tuple2<String, SearchBody.QueryObject> kv : queryObjects.iterator()) {
                routeQueryObjectCriterion(parameters, properties, kv._1(), kv._2());
            }
        }
    }

    private Option<ICriterion> routeQueryObjectCriterion(List<StacProperty> properties,
                                                         String stacPropName,
                                                         SearchBody.QueryObject queryObject) {

        // Retrieve target attribute
        Option<AttributeModel> attr = propertyNameFor(properties, stacPropName);

        if (queryObject instanceof SearchBody.BooleanQueryObject) {
            if (isBooleanQueryAcceptable(attr)) {
                return new BooleanQueryCriterionBuilder(stacPropName).buildCriterion(properties,
                                                                                     (SearchBody.BooleanQueryObject) queryObject);
            } else {
                return ignoreQueryField(attr, stacPropName);
            }
        } else if (queryObject instanceof SearchBody.NumberQueryObject) {
            // Dispatch by number type
            if (isIntegerQueryAcceptable(attr)) {
                return new IntegerQueryCriterionBuilder(stacPropName).buildCriterion(properties,
                                                                                     (SearchBody.NumberQueryObject) queryObject);
            } else if (isLongQueryAcceptable(attr)) {
                return new LongQueryCriterionBuilder(stacPropName).buildCriterion(properties,
                                                                                  (SearchBody.NumberQueryObject) queryObject);
            } else if (isDoubleQueryAcceptable(attr)) {
                return new DoubleQueryCriterionBuilder(stacPropName).buildCriterion(properties,
                                                                                    (SearchBody.NumberQueryObject) queryObject);
            } else {
                return ignoreQueryField(attr, stacPropName);
            }
        } else if (queryObject instanceof SearchBody.DatetimeQueryObject) {
            if (isDateQueryAcceptable(attr)) {
                return new DatetimeQueryCriterionBuilder(stacPropName).buildCriterion(properties,
                                                                                      (SearchBody.DatetimeQueryObject) queryObject);
            } else {
                return ignoreQueryField(attr, stacPropName);
            }
        } else if (queryObject instanceof SearchBody.StringQueryObject) {
            if (isStringQueryAcceptable(attr)) {
                return new StringQueryCriterionBuilder(stacPropName).buildCriterion(properties,
                                                                                    (SearchBody.StringQueryObject) queryObject);
            } else {
                return ignoreQueryField(attr, stacPropName);
            }
        } else {
            warn(LOGGER,
                 String.format("Unknown type for query object: %s, ignoring query field %s",
                               queryObject.getClass(),
                               stacPropName));
            return Option.none();
        }
    }

    private void routeQueryObjectCriterion(EODagParameters parameters,
                                           List<StacProperty> properties,
                                           String stacPropName,
                                           SearchBody.QueryObject queryObject) {

        // Retrieve target attribute
        Option<AttributeModel> attr = propertyNameFor(properties, stacPropName);

        if (queryObject instanceof SearchBody.BooleanQueryObject) {
            if (isBooleanQueryAcceptable(attr)) {
                new BooleanQueryCriterionBuilder(stacPropName).computeEODagParameters(parameters,
                                                                                      properties,
                                                                                      (SearchBody.BooleanQueryObject) queryObject);
            } else {
                ignoreQueryField(attr, stacPropName);
            }
        } else if (queryObject instanceof SearchBody.NumberQueryObject) {
            // Dispatch by number type
            if (isIntegerQueryAcceptable(attr)) {
                new IntegerQueryCriterionBuilder(stacPropName).computeEODagParameters(parameters,
                                                                                      properties,
                                                                                      (SearchBody.NumberQueryObject) queryObject);
            } else if (isLongQueryAcceptable(attr)) {
                new LongQueryCriterionBuilder(stacPropName).computeEODagParameters(parameters,
                                                                                   properties,
                                                                                   (SearchBody.NumberQueryObject) queryObject);
            } else if (isDoubleQueryAcceptable(attr)) {
                new DoubleQueryCriterionBuilder(stacPropName).computeEODagParameters(parameters,
                                                                                     properties,
                                                                                     (SearchBody.NumberQueryObject) queryObject);
            } else {
                ignoreQueryField(attr, stacPropName);
            }
        } else if (queryObject instanceof SearchBody.DatetimeQueryObject) {
            if (isDateQueryAcceptable(attr)) {
                new DatetimeQueryCriterionBuilder(stacPropName).computeEODagParameters(parameters,
                                                                                       properties,
                                                                                       (SearchBody.DatetimeQueryObject) queryObject);
            } else {
                ignoreQueryField(attr, stacPropName);
            }
        } else if (queryObject instanceof SearchBody.StringQueryObject) {
            if (isStringQueryAcceptable(attr)) {
                new StringQueryCriterionBuilder(stacPropName).computeEODagParameters(parameters,
                                                                                     properties,
                                                                                     (SearchBody.StringQueryObject) queryObject);
            } else {
                ignoreQueryField(attr, stacPropName);
            }
        } else {
            warn(LOGGER,
                 String.format("Unknown type for query object: %s, ignoring query field %s",
                               queryObject.getClass(),
                               stacPropName));
        }
    }

    private Option<ICriterion> ignoreQueryField(Option<AttributeModel> attr, String stacPropName) {
        if (attr.isDefined()) {
            warn(LOGGER,
                 String.format("Unacceptable type %s for query field %s. Ignoring it!",
                               attr.get().getType().name(),
                               stacPropName));
        } else {
            info(LOGGER, String.format("Unknown target attribute for query field %s. Ignoring it!", stacPropName));
        }
        return Option.none();
    }

    private Boolean isBooleanQueryAcceptable(Option<AttributeModel> attr) {
        return attr.isDefined() && attr.get().isBooleanAttribute();
    }

    private Boolean isIntegerQueryAcceptable(Option<AttributeModel> attr) {
        return attr.isDefined() && ACCEPTED_INTEGER_TYPES.contains(attr.get().getType());
    }

    private Boolean isLongQueryAcceptable(Option<AttributeModel> attr) {
        return attr.isDefined() && ACCEPTED_LONG_TYPES.contains(attr.get().getType());
    }

    private Boolean isDoubleQueryAcceptable(Option<AttributeModel> attr) {
        return attr.isDefined() && ACCEPTED_DOUBLE_TYPES.contains(attr.get().getType());
    }

    private Boolean isDateQueryAcceptable(Option<AttributeModel> attr) {
        return attr.isDefined() && ACCEPTED_DATE_TYPES.contains(attr.get().getType());
    }

    private Boolean isStringQueryAcceptable(Option<AttributeModel> attr) {
        return attr.isDefined() && attr.get().isTextAttribute();
    }
}
