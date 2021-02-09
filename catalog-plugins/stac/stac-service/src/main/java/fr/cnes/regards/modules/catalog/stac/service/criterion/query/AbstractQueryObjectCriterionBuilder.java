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

import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody.QueryObject;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.service.criterion.CriterionBuilder;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import io.vavr.collection.List;
import io.vavr.control.Option;

/**
 * Abstract criterion builder
 */
public abstract class AbstractQueryObjectCriterionBuilder<T extends QueryObject> implements CriterionBuilder<T> {

    protected String stacPropName;

    protected AbstractQueryObjectCriterionBuilder(String stacPropName) {
        this.stacPropName = stacPropName;
    }

    public abstract Option<ICriterion> buildCriterion(String attr, List<StacProperty> properties, T queryObject);

    @Override
    public Option<ICriterion> buildCriterion(List<StacProperty> properties, T queryObject) {
        if (queryObject == null) { return Option.none(); }
        String attr = propertyNameFor(properties, stacPropName);
        return buildCriterion(attr, properties, queryObject);
    }

    protected String propertyNameFor(List<StacProperty> properties, String attrName) {
        return getStacProperty(properties, attrName)
                .map(StacProperty::getModelAttributeName)
                .getOrElse(attrName);
    }

    protected Option<StacProperty> getStacProperty(List<StacProperty> properties, String attrName) {
        return properties.find(p -> attrName.equals(p.getStacPropertyName()));
    }
}
