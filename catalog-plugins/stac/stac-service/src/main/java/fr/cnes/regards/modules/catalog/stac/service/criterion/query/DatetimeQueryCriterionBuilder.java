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

import fr.cnes.regards.framework.gson.adapters.OffsetDateTimeAdapter;
import fr.cnes.regards.modules.catalog.stac.domain.StacSpecConstants;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.SearchBody.DatetimeQueryObject;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.service.collection.search.eodag.EODagParameters;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.model.domain.attributes.AttributeModel;
import io.vavr.collection.List;
import io.vavr.control.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static fr.cnes.regards.modules.dam.domain.entities.criterion.IFeatureCriterion.*;
import static fr.cnes.regards.modules.indexer.domain.criterion.ICriterion.not;

/**
 * Criterion builder for a {@link DatetimeQueryObject}
 * <p>
 * TODO: convert this class to the same model as for NumberQueryCriterionBuilder, using custom datetime intervals
 */
public class DatetimeQueryCriterionBuilder extends AbstractQueryObjectCriterionBuilder<DatetimeQueryObject> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatetimeQueryCriterionBuilder.class);

    public DatetimeQueryCriterionBuilder(String stacPropName) {
        super(stacPropName);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Option<ICriterion> buildCriterion(AttributeModel attr,
                                             List<StacProperty> properties,
                                             DatetimeQueryObject queryObject) {
        return andAllPresent(Option.of(queryObject.getEq()).map(eq -> eq(attr, eq)),
                             Option.of(queryObject.getNeq()).map(neq -> not(eq(attr, neq))),
                             Option.of(queryObject.getLt()).map(lt -> lt(attr, lt)),
                             Option.of(queryObject.getLte()).map(le -> le(attr, le)),
                             Option.of(queryObject.getGt()).map(gt -> gt(attr, gt)),
                             Option.of(queryObject.getGte()).map(ge -> ge(attr, ge)),
                             Option.of(queryObject.getIn())
                                   .flatMap(in -> in.map(d -> eq(attr, d)).reduceLeftOption(ICriterion::or)));
    }

    /**
     * Trying to translate incoming request, available combinations :
     * - start_datetime <= or < becomes "end"
     * - start_datetime > or >= becomes "start"
     * - end_datetime <= or < becomes "end"
     * - end_datetime > or >= becomes "start"
     */
    @Override
    public void buildEODagParameters(AttributeModel attr,
                                     EODagParameters parameters,
                                     List<StacProperty> properties,
                                     DatetimeQueryObject queryObject) {
        if (StacSpecConstants.PropertyName.START_DATETIME_PROPERTY_NAME.equals(stacPropName)
            || StacSpecConstants.PropertyName.END_DATETIME_PROPERTY_NAME.equals(stacPropName)) {
            if (queryObject.getLt() != null) {
                parameters.setEnd(OffsetDateTimeAdapter.format(queryObject.getLt()));
            } else if (queryObject.getLte() != null) {
                parameters.setEnd(OffsetDateTimeAdapter.format(queryObject.getLte()));
            } else if (queryObject.getGt() != null) {
                parameters.setStart(OffsetDateTimeAdapter.format(queryObject.getGt()));
            } else if (queryObject.getGte() != null) {
                parameters.setStart(OffsetDateTimeAdapter.format(queryObject.getGte()));
            }
        }
        if (queryObject.getEq() != null) {
            parameters.addExtras(stacPropName, attr.getType(), OffsetDateTimeAdapter.format(queryObject.getEq()));
        } else {
            LOGGER.warn(EODAG_RESTRICTION_MESSAGE, stacPropName);
        }
    }
}
