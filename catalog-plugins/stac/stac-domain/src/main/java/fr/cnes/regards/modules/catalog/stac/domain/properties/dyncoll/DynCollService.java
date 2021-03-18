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

package fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll;

import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Item;
import io.vavr.collection.List;
import io.vavr.control.Option;

/**
 * This interface provides methods to deal with dynamic collections in pretty much all layers
 * of the STAC plugin (parsing configuration, generation of criteria and aggregations, etc.).
 * It is defined in domain, but implemented at the "lowest layer" (stac-plugin adapter).
 */
public interface DynCollService {

    /**
     * Provide an optional dynamic collections definition from the configured
     * STAC properties.
     *
     * @param properties the stac properties defined by the user
     * @return empty if no property is configured to be a dynamic collection level
     */
    Option<DynCollDef> dynamicCollectionsDefinition(List<StacProperty> properties);

    /**
     * Use this method to parse the string representation of the level format.
     * @param levelFormat the format given by the user
     * @return a default definition if unparsable / empty
     */
    DynCollLevelDef parseDynamicCollectionLevelDefinition(String levelFormat);

    Option<String> representDynamicCollectionsValueAsURN(DynCollVal val);
    Option<DynCollVal> parseDynamicCollectionsValueFromURN(String urn);
    boolean isDynamicCollectionValueURN(String urn);

    boolean hasMoreSublevels(DynCollDef def, DynCollVal value);
    List<DynCollVal> sublevels(DynCollDef def, DynCollVal value);
    List<Item> searchItemsInDynamicCollection(DynCollDef def, DynCollVal value);

}
