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
 * along with REGARDS. If not, see `<http://www.gnu.org/licenses/>`.
 */
package fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.link;

import com.google.gson.Gson;
import fr.cnes.regards.framework.authentication.IAuthenticationResolver;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.modules.catalog.stac.rest.link.LinkCreatorService;
import fr.cnes.regards.modules.catalog.stac.rest.link.LinkCreatorServiceImpl;
import fr.cnes.regards.modules.catalog.stac.service.link.SearchPageLinkCreator;
import fr.cnes.regards.modules.catalog.stac.service.link.UriParamAdder;
import fr.cnes.regards.modules.catalog.stac.service.link.UriParamAdderImpl;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test class to check values of pagination when calling LinkCreatorService methods
 */
@SpringBootTest
@ActiveProfiles({ "test", "nojobs", "noscheduler" })
public class LinkCreatorServicePaginationTest {

    private LinkCreatorService linkCreatorService;

    @Before
    public void setUp() {
        IRuntimeTenantResolver runtimeTenantResolver = mock(IRuntimeTenantResolver.class);
        when(runtimeTenantResolver.getTenant()).thenReturn("testTenant");
        UriParamAdder uriParamAdder = new UriParamAdderImpl(runtimeTenantResolver, mock(IAuthenticationResolver.class));
        this.linkCreatorService = new LinkCreatorServiceImpl(runtimeTenantResolver, uriParamAdder, new Gson());
    }

    /**
     * We test the value of pagination links for the different {@link SearchPageLinkCreator} available through
     * {@link LinkCreatorService}
     */
    @Test
    public void test_pagination_search_page_link_creators() {
        SearchPageLinkCreator pageLinkCreator = linkCreatorService.makeSearchPageLinkCreator(2, null, true);

        SearchPageLinkCreator collectionItemsPageLinkCreator = linkCreatorService.makeCollectionItemsPageLinkCreator(2,
                                                                                                                     "URN:FEATURE:toto:123-123-123:V1",
                                                                                                                     true);

        SearchPageLinkCreator collectionPageLinkCreator = linkCreatorService.makeSearchCollectionPageLinkCreation(2,
                                                                                                                  null,
                                                                                                                  true);
        test_next_previous_self_pages(pageLinkCreator);
        test_next_previous_self_pages(collectionItemsPageLinkCreator);
        test_next_previous_self_pages(collectionPageLinkCreator);
    }

    /**
     * We check the previous, next and self links for a given {@link SearchPageLinkCreator}
     */
    public void test_next_previous_self_pages(SearchPageLinkCreator searchPageLinkCreator) {
        //Test next link
        URI nextUri = searchPageLinkCreator.createNextPageLink().get();
        List<NameValuePair> pageParams = new URIBuilder(nextUri).getQueryParams()
                                                                .stream()
                                                                .filter(pair -> pair.getName().equals("page"))
                                                                .toList();

        assertEquals(1, pageParams.size());
        assertEquals("3", pageParams.get(0).getValue());
        assertEquals(0,
                     new URIBuilder(nextUri).getQueryParams()
                                            .stream()
                                            .filter(pair -> pair.getName().equals("limit"))
                                            .count());

        //Test previous link
        URI previousUri = searchPageLinkCreator.createPrevPageLink().get();
        pageParams = new URIBuilder(previousUri).getQueryParams()
                                                .stream()
                                                .filter(pair -> pair.getName().equals("page"))
                                                .toList();

        assertEquals(1, pageParams.size());
        assertEquals("1", pageParams.get(0).getValue());
        assertEquals(0,
                     new URIBuilder(nextUri).getQueryParams()
                                            .stream()
                                            .filter(pair -> pair.getName().equals("limit"))
                                            .count());

        //Test self link
        URI selfUri = searchPageLinkCreator.createSelfPageLink().get();
        pageParams = new URIBuilder(selfUri).getQueryParams()
                                            .stream()
                                            .filter(pair -> pair.getName().equals("page"))
                                            .toList();

        assertEquals(1, pageParams.size());
        assertEquals("2", pageParams.get(0).getValue());
        assertEquals(0,
                     new URIBuilder(nextUri).getQueryParams()
                                            .stream()
                                            .filter(pair -> pair.getName().equals("limit"))
                                            .count());
    }
}
