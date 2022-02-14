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

package fr.cnes.regards.modules.catalog.stac.service.collection.dyncoll.helpers;

import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.SearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.level.DynCollLevelVal;
import io.vavr.Tuple2;
import io.vavr.control.Option;

/**
 * Methods allowing to convert level values to QueryObjects.
 * This will allow to build an ItemSearchBody from the dynamic colection values,
 * and thus reuse the search mechanism with this query object.
 */
public interface DynCollLevelValToQueryObjectConverter {

    Option<Tuple2<String, SearchBody.QueryObject>> toQueryObject(DynCollLevelVal leveVal);

}
