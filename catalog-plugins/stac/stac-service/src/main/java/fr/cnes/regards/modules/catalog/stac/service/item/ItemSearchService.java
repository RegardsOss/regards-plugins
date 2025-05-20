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

package fr.cnes.regards.modules.catalog.stac.service.item;

import fr.cnes.regards.modules.catalog.stac.domain.api.ItemCollectionResponse;
import fr.cnes.regards.modules.catalog.stac.domain.api.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.spec.Item;
import fr.cnes.regards.modules.catalog.stac.service.link.OGCFeatLinkCreator;
import fr.cnes.regards.modules.catalog.stac.service.link.SearchPageLinkCreator;
import io.vavr.control.Try;

/**
 * STAC item searching methods.
 */
public interface ItemSearchService {

    /**
     * Search for items.
     *
     * @param itemSearchBody        the search body
     * @param page                  the page number
     * @param featLinkCreator       the link creator
     * @param searchPageLinkCreator the search page link creator
     * @return a page of items
     */
    Try<ItemCollectionResponse> search(ItemSearchBody itemSearchBody,
                                       Integer page,
                                       OGCFeatLinkCreator featLinkCreator,
                                       SearchPageLinkCreator searchPageLinkCreator);

    /**
     * Search for an item by its id.
     *
     * @param featureId       the feature id
     * @param featLinkCreator the link creator
     * @return a single item
     */
    Try<Item> searchById(String featureId, OGCFeatLinkCreator featLinkCreator);

}
