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

import static fr.cnes.regards.modules.catalog.stac.domain.error.StacRequestCorrelationId.error;
import static fr.cnes.regards.modules.catalog.stac.domain.utils.TryDSL.trying;

import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.search.service.accessright.AccessRightFilter;
import io.vavr.collection.List;
import io.vavr.control.Option;

/**
 * Base implementation for {@link StacSearchCriterionBuilder}.
 */
@Component
public class StacSearchCriterionBuilderImpl implements StacSearchCriterionBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(StacSearchCriterionBuilderImpl.class);

    private final AccessRightFilter accessRightFilter;

    private final GeometryCriterionBuilder geometryCriterionBuilder;

    private final IdentitiesCriterionBuilder identitiesCriterionBuilder;

    private final FieldsCriterionBuilder fieldsCriterionBuilder;

    private final DateIntervalCriterionBuilder dateIntervalCriterionBuilder;

    private final CollectionsCriterionBuilder collectionsCriterionBuilder;

    private final BBoxCriterionBuilder bBoxCriterionBuilder;

    private final QueryObjectCriterionBuilder queryObjectCriterionBuilder;

    @Autowired
    public StacSearchCriterionBuilderImpl(AccessRightFilter accessRightFilter,
            GeometryCriterionBuilder geometryCriterionBuilder, IdentitiesCriterionBuilder identitiesCriterionBuilder,
            FieldsCriterionBuilder fieldsCriterionBuilder, DateIntervalCriterionBuilder dateIntervalCriterionBuilder,
            CollectionsCriterionBuilder collectionsCriterionBuilder, BBoxCriterionBuilder bBoxCriterionBuilder,
            QueryObjectCriterionBuilder queryObjectCriterionBuilder) {
        this.accessRightFilter = accessRightFilter;
        this.geometryCriterionBuilder = geometryCriterionBuilder;
        this.identitiesCriterionBuilder = identitiesCriterionBuilder;
        this.fieldsCriterionBuilder = fieldsCriterionBuilder;
        this.dateIntervalCriterionBuilder = dateIntervalCriterionBuilder;
        this.collectionsCriterionBuilder = collectionsCriterionBuilder;
        this.bBoxCriterionBuilder = bBoxCriterionBuilder;
        this.queryObjectCriterionBuilder = queryObjectCriterionBuilder;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Option<ICriterion> buildCriterion(List<StacProperty> properties, ItemSearchBody itemSearchBody) {
        return andAllPresent(bBoxCriterionBuilder.buildCriterion(properties, itemSearchBody.getBbox()),
                             collectionsCriterionBuilder.buildCriterion(properties, itemSearchBody.getCollections()),
                             dateIntervalCriterionBuilder.buildCriterion(properties, itemSearchBody.getDatetime()),
                             fieldsCriterionBuilder.buildCriterion(properties, itemSearchBody.getFields()),
                             identitiesCriterionBuilder.buildCriterion(properties, itemSearchBody.getIds()),
                             geometryCriterionBuilder.buildCriterion(properties, itemSearchBody.getIntersects()),
                             queryObjectCriterionBuilder.buildCriterion(properties, itemSearchBody.getQuery()))
                                     .flatMap(addAccessCriteria());
    }

    public Function<ICriterion, Option<? extends ICriterion>> addAccessCriteria() {
        return c -> trying(() -> accessRightFilter.addAccessRights(c))
                .onFailure(t -> error(LOGGER, "Failed to add access rights to search: {}", t.getMessage(), t)).toOption();
    }

}
