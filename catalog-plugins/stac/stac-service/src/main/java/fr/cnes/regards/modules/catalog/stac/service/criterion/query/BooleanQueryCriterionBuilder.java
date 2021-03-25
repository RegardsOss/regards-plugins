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

import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody.BooleanQueryObject;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.model.domain.attributes.AttributeModel;
import io.vavr.collection.List;
import io.vavr.control.Option;

import static fr.cnes.regards.modules.dam.domain.entities.criterion.IFeatureCriterion.eq;

/**
 * Criterion builder for a {@link BooleanQueryObject}
 */
public class BooleanQueryCriterionBuilder extends AbstractQueryObjectCriterionBuilder<BooleanQueryObject> {

    public BooleanQueryCriterionBuilder(String stacPropName) {
        super(stacPropName);
    }

    @Override
    public Option<ICriterion> buildCriterion(AttributeModel attr, List<StacProperty> properties, BooleanQueryObject value) {
        return andAllPresent(
                Option.of(value.getEq()).map(eq -> eq(attr, eq)),
                Option.of(value.getNeq()).map(neq -> eq(attr, !neq))
        );
    }

}