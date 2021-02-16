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

import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Item;
import fr.cnes.regards.modules.catalog.stac.service.link.OGCFeatLinkCreator;
import fr.cnes.regards.modules.dam.domain.entities.DataObject;
import io.vavr.collection.List;
import io.vavr.control.Try;
import org.springframework.stereotype.Component;

/**
 * Default implementation for {@link RegardsFeatureToStacItemConverter} interface.
 */
@Component
public class RegardsFeatureToStacItemConverterImpl implements RegardsFeatureToStacItemConverter {

    @Override
    public Try<Item> convertFeatureToItem(List<StacProperty> properties, OGCFeatLinkCreator linkCreator, DataObject feature) {
        return null; // TODO
    }
}
