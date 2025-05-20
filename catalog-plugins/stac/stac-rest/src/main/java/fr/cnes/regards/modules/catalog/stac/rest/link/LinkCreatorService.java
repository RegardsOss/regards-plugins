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

package fr.cnes.regards.modules.catalog.stac.rest.link;

import fr.cnes.regards.framework.feign.security.FeignSecurityManager;
import fr.cnes.regards.modules.catalog.stac.domain.api.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.api.extension.searchcol.CollectionSearchBody;
import fr.cnes.regards.modules.catalog.stac.service.link.DownloadLinkCreator;
import fr.cnes.regards.modules.catalog.stac.service.link.OGCFeatLinkCreator;
import fr.cnes.regards.modules.catalog.stac.service.link.SearchPageLinkCreator;

/**
 * This interface allows to create link creators used in the stac-service services.
 */
public interface LinkCreatorService {

    OGCFeatLinkCreator makeOGCFeatLinkCreator(boolean appendAuthParams);

    SearchPageLinkCreator makeSearchPageLinkCreator(Integer page,
                                                    ItemSearchBody itemSearchBody,
                                                    boolean appendAuthParams);

    SearchPageLinkCreator makeSearchCollectionPageLinkCreation(Integer page,
                                                               CollectionSearchBody collectionSearchBody,
                                                               boolean appendAuthParams);

    SearchPageLinkCreator makeCollectionItemsPageLinkCreator(Integer page,
                                                             String collectionId,
                                                             boolean appendAuthParams);

    DownloadLinkCreator makeDownloadLinkCreator(FeignSecurityManager feignSecurityManager, boolean appendAuthParams);
}
