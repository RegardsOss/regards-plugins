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

import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.SearchBody.BooleanQueryObject;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.service.collection.search.eodag.EODagParameters;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.model.domain.attributes.AttributeModel;
import io.vavr.collection.List;
import io.vavr.control.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static fr.cnes.regards.modules.dam.domain.entities.criterion.IFeatureCriterion.eq;

/**
 * Criterion builder for a {@link BooleanQueryObject}
 */
public class BooleanQueryCriterionBuilder extends AbstractQueryObjectCriterionBuilder<BooleanQueryObject> {

    private static final Logger LOGGER = LoggerFactory.getLogger(BooleanQueryCriterionBuilder.class);

    public BooleanQueryCriterionBuilder(String stacPropName) {
        super(stacPropName);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Option<ICriterion> buildCriterion(AttributeModel attr,
                                             List<StacProperty> properties,
                                             BooleanQueryObject value) {
        return andAllPresent(Option.of(value.getEq()).map(eq -> eq(attr, eq)),
                             Option.of(value.getNeq()).map(neq -> eq(attr, !neq)));
    }

    @Override
    public void buildEODagParameters(AttributeModel attr,
                                     EODagParameters parameters,
                                     List<StacProperty> properties,
                                     BooleanQueryObject queryObject) {
        if (queryObject.getEq() != null) {
            parameters.addExtras(stacPropName, attr.getType(), queryObject.getEq());
        } else {
            LOGGER.warn(EODAG_RESTRICTION_MESSAGE, stacPropName);
        }
    }
}
