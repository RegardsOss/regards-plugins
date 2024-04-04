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
package fr.cnes.regards.modules.catalog.stac.service.collection.timeline.builder;

import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.extension.searchcol.FiltersByCollection;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.extension.searchcol.TimelineByCollectionResponse;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.extension.searchcol.TimelineFiltersByCollection;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import io.vavr.collection.List;
import org.springframework.data.domain.Pageable;

import java.time.ZoneId;

public interface TimelineBuilder extends GenericTimelineBuilder {

     TimelineByCollectionResponse.CollectionTimeline buildTimeline(TimelineFiltersByCollection.TimelineMode mode,
                                                                   FiltersByCollection.CollectionFilters collectionFilters,
                                                                   Pageable pageable,
                                                                   List<StacProperty> itemStacProperties,
                                                                   String from,
                                                                   String to,
                                                                   ZoneId zoneId);
}
