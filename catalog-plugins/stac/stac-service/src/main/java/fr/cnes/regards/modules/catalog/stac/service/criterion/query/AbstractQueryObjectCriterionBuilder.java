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

import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.SearchBody.QueryObject;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.service.collection.search.eodag.EODagParameters;
import fr.cnes.regards.modules.catalog.stac.service.criterion.CriterionBuilder;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.model.domain.attributes.AttributeModel;
import io.vavr.collection.List;
import io.vavr.control.Option;

/**
 * Abstract criterion builder
 */
public abstract class AbstractQueryObjectCriterionBuilder<T extends QueryObject> implements CriterionBuilder<T> {

    protected static final String EODAG_RESTRICTION_MESSAGE = "EODag parameter only supports equals operator for property {}. Skipping!";

    protected String stacPropName;

    protected AbstractQueryObjectCriterionBuilder(String stacPropName) {
        this.stacPropName = stacPropName;
    }

    public abstract Option<ICriterion> buildCriterion(AttributeModel attr, List<StacProperty> properties, T queryObject);

    public abstract void buildEODagParameters(AttributeModel attr, EODagParameters parameters, List<StacProperty> properties, T queryObject);

    @Override
    public Option<ICriterion> buildCriterion(List<StacProperty> properties, T queryObject) {
        if (queryObject == null) { return Option.none(); }
        return propertyNameFor(properties, stacPropName)
                .flatMap(attr -> buildCriterion(attr, properties, queryObject));
    }

    @Override
    public void computeEODagParameters(EODagParameters parameters, List<StacProperty> properties, T queryObject) {
        if (queryObject != null) {
            buildEODagParameters(propertyNameFor(properties, stacPropName).get(), parameters, properties, queryObject);
        }
    }
}
