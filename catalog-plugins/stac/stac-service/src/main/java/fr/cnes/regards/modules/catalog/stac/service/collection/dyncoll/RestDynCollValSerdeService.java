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

package fr.cnes.regards.modules.catalog.stac.service.collection.dyncoll;

import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.DynCollDef;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.DynCollVal;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.rest.RestDynCollVal;
import io.vavr.control.Try;

/**
 * Interface allowing to serialize/deserialize URNs corresponding to
 * list of dyn collection level values.
 */
public interface RestDynCollValSerdeService {

    RestDynCollVal fromDomain(DynCollVal domain);

    Try<DynCollVal> toDomain(DynCollDef def, RestDynCollVal rest);

    String toUrn(RestDynCollVal values);

    Try<RestDynCollVal> fromUrn(String repr);

    boolean isListOfDynCollLevelValues(String urn);

}
