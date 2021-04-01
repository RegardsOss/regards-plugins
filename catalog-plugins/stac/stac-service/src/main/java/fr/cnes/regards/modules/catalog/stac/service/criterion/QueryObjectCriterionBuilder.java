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
import fr.cnes.regards.modules.catalog.stac.service.criterion.query.BooleanQueryCriterionBuilder;
import fr.cnes.regards.modules.catalog.stac.service.criterion.query.DatetimeQueryCriterionBuilder;
import fr.cnes.regards.modules.catalog.stac.service.criterion.query.NumberQueryCriterionBuilder;
import fr.cnes.regards.modules.catalog.stac.service.criterion.query.StringQueryCriterionBuilder;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static fr.cnes.regards.modules.catalog.stac.domain.error.StacRequestCorrelationId.warn;

/**
 * Build criteria from query objects.
 */
@Component
public class QueryObjectCriterionBuilder implements CriterionBuilder<Map<String, QueryObject>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(QueryObjectCriterionBuilder.class);

    @Override
    public Option<ICriterion> buildCriterion(List<StacProperty> properties, Map<String, QueryObject> queryObjects) {
        if (queryObjects == null || queryObjects.isEmpty()) { return Option.none(); }

        List<ICriterion> crits = queryObjects.flatMap(kv -> routeQueryObjectCrit(properties, kv._1(), kv._2())).toList();
        return withAll(crits, ICriterion::and);
    }

    private Option<ICriterion> routeQueryObjectCrit(
            List<StacProperty> properties,
            String stacPropName,
            QueryObject queryObject
    ) {
        if (queryObject instanceof BooleanQueryObject) {
            return new BooleanQueryCriterionBuilder(stacPropName)
                .buildCriterion(properties, (BooleanQueryObject)queryObject);
        }
        else if (queryObject instanceof NumberQueryObject) {
            return new NumberQueryCriterionBuilder(stacPropName)
                .buildCriterion(properties, (NumberQueryObject)queryObject);
        }
        else if (queryObject instanceof DatetimeQueryObject) {
            return new DatetimeQueryCriterionBuilder(stacPropName)
                .buildCriterion(properties, (DatetimeQueryObject)queryObject);
        }
        else if (queryObject instanceof StringQueryObject) {
            return new StringQueryCriterionBuilder(stacPropName)
                .buildCriterion(properties, (StringQueryObject)queryObject);
        }
        else {
            warn(LOGGER, "Unknown type for QueryObject: {}, ignoring query field {}",
                queryObject.getClass(),
                stacPropName
            );
            return Option.none();
        }
    }

}
