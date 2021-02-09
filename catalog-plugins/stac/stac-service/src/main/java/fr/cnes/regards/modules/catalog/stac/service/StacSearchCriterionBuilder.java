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

package fr.cnes.regards.modules.catalog.stac.service;

import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.service.criterion.CriterionBuilder;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import io.vavr.collection.List;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interface defining the methods for transforming {@link ItemSearchBody} into {@link ICriterion}
 */
public interface StacSearchCriterionBuilder extends CriterionBuilder<ItemSearchBody> {

    Logger LOGGER = LoggerFactory.getLogger(StacSearchCriterionBuilder.class);

    default ICriterion toCriterion(List<StacProperty> properties, ItemSearchBody itemSearchBody) {
        return Try.of(() -> buildCriterion(properties, itemSearchBody))
            .flatMapTry(opt -> opt.toTry())
            .getOrElseGet(e -> {
                LOGGER.error("Could not build criterion for {}", itemSearchBody, e);
                return ICriterion.all();
            });
    }

    Option<ICriterion> buildCriterion(List<StacProperty> properties, ItemSearchBody value);
}
