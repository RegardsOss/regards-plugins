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

package fr.cnes.regards.modules.catalog.stac.service.collection.dynamic;

import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import org.springframework.stereotype.Component;

/**
 * Default implementation for {@link DynamicCollectionService}.
 */
@Component
public class DynamicCollectionServiceImpl implements DynamicCollectionService {

    @Override
    public String extractDynamicCollectionName(
            List<StacProperty> properties,
            Map<String, Object> featureStacProperties
    ) {
        // TODO
        return null;
    }

}
