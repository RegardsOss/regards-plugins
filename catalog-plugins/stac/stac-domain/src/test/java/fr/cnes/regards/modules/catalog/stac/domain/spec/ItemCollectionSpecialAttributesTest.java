/*
 * Copyright 2017-2025 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
 * along with REGARDS. If not, see `<http://www.gnu.org/licenses/>`.
 */
package fr.cnes.regards.modules.catalog.stac.domain.spec;

import fr.cnes.regards.modules.catalog.stac.domain.api.Context;
import fr.cnes.regards.modules.catalog.stac.domain.api.ItemCollectionResponse;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import net.javacrumbs.jsonunit.assertj.JsonAssert;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

/**
 * Test for serialization of specific Item attributes that need a special serialization,
 * when  Items are part of an ItemCollectionResponse.
 *
 * @author Julien Canches
 */
public class ItemCollectionSpecialAttributesTest extends ItemSpecialAttributesTest {

    /**
     * Check the serialization of an item 1/ as a standalone object, 2/ within an ItemCollectionResponse.
     */
    @Override
    protected JsonAssert assertThatItem(Item item) {
        ItemCollectionResponse response = new ItemCollectionResponse(HashSet.empty(),
                                                                     List.of(item),
                                                                     List.empty(),
                                                                     new Context(1, 10, 2L),
                                                                     2L,
                                                                     1L);
        return assertThatJson(gson.toJson(response)).node("features[0]");
    }

}
