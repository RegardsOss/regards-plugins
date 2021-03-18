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

package fr.cnes.regards.modules.catalog.stac.plugin.dyncoll;

import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.DynCollDef;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.DynCollLevelDef;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.DynCollService;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.DynCollVal;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Item;
import fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.utils.Base64Codec;
import io.vavr.collection.List;
import io.vavr.control.Option;

/**
 * Base implementation for {@link DynCollService}.
 */
public class DynCollServiceImpl implements DynCollService, Base64Codec {

    @Override
    public Option<DynCollDef> dynamicCollectionsDefinition(List<StacProperty> properties) {
        return null; // TODO
    }

    @Override
    public DynCollLevelDef parseDynamicCollectionLevelDefinition(String levelFormat) {
        return null;  // TODO
    }

    @Override
    public Option<String> representDynamicCollectionsValueAsURN(DynCollVal val) {
        return null;  // TODO
    }

    @Override
    public Option<DynCollVal> parseDynamicCollectionsValueFromURN(String urn) {
        return null;  // TODO
    }

    @Override
    public boolean isDynamicCollectionValueURN(String urn) {
        return false;
    }

    @Override
    public boolean hasMoreSublevels(DynCollDef def, DynCollVal value) {
        return false; // TODO
    }

    @Override
    public List<DynCollVal> sublevels(DynCollDef def, DynCollVal value) {
        return null;  // TODO
    }

    @Override
    public List<Item> searchItemsInDynamicCollection(DynCollDef def, DynCollVal value) {
        return null;  // TODO
    }
}
