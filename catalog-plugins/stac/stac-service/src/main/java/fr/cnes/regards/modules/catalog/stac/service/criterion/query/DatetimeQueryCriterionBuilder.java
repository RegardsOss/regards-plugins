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

import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody.DatetimeQueryObject;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import io.vavr.collection.List;
import io.vavr.control.Option;

import static fr.cnes.regards.modules.indexer.domain.criterion.ICriterion.eq;
import static fr.cnes.regards.modules.indexer.domain.criterion.ICriterion.not;

/**
 * Criterion builder for a {@link DatetimeQueryObject}
 *
 * TODO: convert this class to the same model as for NumberQueryCriterionBuilder, using custom datetime intervals
 */
public class DatetimeQueryCriterionBuilder extends AbstractQueryObjectCriterionBuilder<DatetimeQueryObject> {

    public DatetimeQueryCriterionBuilder(String stacPropName) {
        super(stacPropName);
    }

    @Override
    public Option<ICriterion> buildCriterion(String attr, List<StacProperty> properties, DatetimeQueryObject queryObject) {
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
}
