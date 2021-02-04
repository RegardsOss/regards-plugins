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

import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody.*;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static fr.cnes.regards.modules.catalog.stac.service.criterion.CriterionBuilderHelper.andAllPresent;
import static fr.cnes.regards.modules.indexer.domain.criterion.ICriterion.*;

/**
 * Build criteria from query objects.
 */
@Component
public class QueryObjectCriterionBuilder implements CriterionBuilder<Map<String, QueryObject>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(QueryObjectCriterionBuilder.class);
    public static final double DOUBLE_COMPARISON_PRECISION = 1e-6;

    @Override
    public Option<ICriterion> buildCriterion(List<StacProperty> properties, Map<String, QueryObject> queryObjects) {
        if (queryObjects == null || queryObjects.isEmpty()) { return Option.none(); }

        return queryObjects.flatMap(kv -> routeQueryObjectCrit(properties, kv._1(), kv._2()))
            .reduceLeftOption(ICriterion::and);
    }

    private Option<ICriterion> routeQueryObjectCrit(
            List<StacProperty> properties,
            String s,
            QueryObject queryObject
    ) {
        if (queryObject instanceof BooleanQueryObject) {
            return booleanQueryObjectCrit(properties, s, (BooleanQueryObject) queryObject);
        }
        else if (queryObject instanceof NumberQueryObject) {
            return numberQueryObjectCrit(properties, s, (NumberQueryObject) queryObject);
        }
        else if (queryObject instanceof DatetimeQueryObject) {
            return dateQueryObjectCrit(properties, s, (DatetimeQueryObject) queryObject);
        }
        else if (queryObject instanceof StringQueryObject) {
            return stringQueryObjectCrit(properties, s, (StringQueryObject) queryObject);
        }
        else {
            LOGGER.warn("Unknown type for QueryObject: {}, ignoring query field {}", queryObject.getClass(), s);
            return Option.none();
        }
    }

    private Option<ICriterion> booleanQueryObjectCrit(
            List<StacProperty> properties,
            String stacPropName,
            BooleanQueryObject queryObject
    ) {
        String attr = propertyNameFor(properties, stacPropName);
        return andAllPresent(
                Option.of(queryObject.getEq()).map(eq -> eq(attr, eq)),
                Option.of(queryObject.getNeq()).map(neq -> not(eq(attr, neq)))
        );
    }

    private Option<ICriterion> numberQueryObjectCrit(
            List<StacProperty> properties,
            String stacPropName,
            NumberQueryObject queryObject
    ) {
        String attr = propertyNameFor(properties, stacPropName);
        return andAllPresent(
                Option.of(queryObject.getEq()).map(eq -> eq(attr, eq, DOUBLE_COMPARISON_PRECISION)),
                Option.of(queryObject.getNeq()).map(neq -> not(eq(attr, neq, DOUBLE_COMPARISON_PRECISION))),
                Option.of(queryObject.getLt()).map(lt -> ICriterion.lt(attr, lt)),
                Option.of(queryObject.getLte()).map(le -> ICriterion.le(attr, le)),
                Option.of(queryObject.getGt()).map(gt -> ICriterion.gt(attr, gt)),
                Option.of(queryObject.getGte()).map(ge -> ICriterion.ge(attr, ge)),
                Option.of(queryObject.getIn()).flatMap(in ->
                        in.map(d -> eq(attr, d, DOUBLE_COMPARISON_PRECISION))
                                .reduceLeftOption(ICriterion::or))
        );
    }

    private Option<ICriterion> stringQueryObjectCrit(
            List<StacProperty> properties,
            String stacPropName,
            StringQueryObject queryObject
    ) {
        String attr = propertyNameFor(properties, stacPropName);
        return andAllPresent(
                Option.of(queryObject.getEq()).map(eq -> eq(attr, eq)),
                Option.of(queryObject.getNeq()).map(neq -> not(eq(attr, neq))),
                Option.of(queryObject.getStartsWith()).map(st -> startsWith(attr, st)),
                Option.of(queryObject.getEndsWith()).map(en -> endsWith(attr, en)),
                Option.of(queryObject.getContains()).map(en -> contains(attr, en)),
                Option.of(queryObject.getIn()).flatMap(in ->
                        in.map(d -> eq(attr, d)).reduceLeftOption(ICriterion::or))
        );

    }

    private Option<ICriterion> dateQueryObjectCrit(
            List<StacProperty> properties,
            String stacPropName,
            DatetimeQueryObject queryObject
    ) {
        String attr = propertyNameFor(properties, stacPropName);
        return andAllPresent(
                Option.of(queryObject.getEq()).map(eq -> eq(attr, eq)),
                Option.of(queryObject.getNeq()).map(neq -> not(eq(attr, neq))),
                Option.of(queryObject.getLt()).map(lt -> ICriterion.lt(attr, lt)),
                Option.of(queryObject.getLte()).map(le -> ICriterion.le(attr, le)),
                Option.of(queryObject.getGt()).map(gt -> ICriterion.gt(attr, gt)),
                Option.of(queryObject.getGte()).map(ge -> ICriterion.ge(attr, ge)),
                Option.of(queryObject.getIn()).flatMap(in ->
                        in.map(d -> eq(attr, d)).reduceLeftOption(ICriterion::or))
        );
    }
    
    private String propertyNameFor(List<StacProperty> properties, String attrName) {
        return properties.find(p -> attrName.equals(p.getStacPropertyName()))
                .map(StacProperty::getModelAttributeName)
                .getOrElse(attrName);
    }
}
