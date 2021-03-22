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

package fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.link;

import com.google.gson.Gson;
import fr.cnes.regards.framework.security.utils.jwt.JWTAuthentication;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemCollectionResponse;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Collection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Item;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link;
import fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.CoreController;
import fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.ItemSearchController;
import fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.OGCFeaturesController;
import fr.cnes.regards.modules.catalog.stac.service.utils.Base64Codec;
import fr.cnes.regards.modules.catalog.stac.service.link.OGCFeatLinkCreator;
import fr.cnes.regards.modules.catalog.stac.service.link.SearchPageLinkCreator;
import fr.cnes.regards.modules.catalog.stac.service.link.UriParamAdder;
import io.vavr.collection.HashMap;
import io.vavr.collection.Stream;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.net.URI;

import static fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link.Relations.*;
import static fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.utils.StacApiConstants.PAGE_QUERY_PARAM;
import static fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.utils.StacApiConstants.SEARCH_ITEMBODY_QUERY_PARAM;
import static io.vavr.control.Option.none;

/**
 * Allows to generate link creators.
 */
@Service
public class LinkCreatorServiceImpl implements LinkCreatorService, Base64Codec {

    private static final Logger LOGGER = LoggerFactory.getLogger(LinkCreatorServiceImpl.class);

    private final UriParamAdder uriParamAdder;
    private final Gson gson;

    // @formatter:off

    @Autowired
    public LinkCreatorServiceImpl(
            UriParamAdder uriParamAdder,
            Gson gson
    ) {
        this.uriParamAdder = uriParamAdder;
        this.gson = gson;
    }

    @Override
    public OGCFeatLinkCreator makeOGCFeatLinkCreator(JWTAuthentication auth) {
        String tenant = auth.getTenant();

        return new OGCFeatLinkCreator() {

            @Override
            public Try<Link> createRootLink() {
                return Try.of(() ->
                    WebMvcLinkBuilder.linkTo(
                        CoreController.class,
                        getMethodNamedInClass(CoreController.class, "root")
                    ).toUri()
                )
                .flatMapTry(uriParamAdder.appendAuthParams(auth))
                .map(createLink(ROOT, String.format("STAC %s root", tenant)));
            }

            @Override
            public Try<Link> createCollectionsLink() {
                return Try.of(() ->
                        WebMvcLinkBuilder.linkTo(
                                OGCFeaturesController.class,
                                getMethodNamedInClass(OGCFeaturesController.class, "collections")
                        ).toUri()
                )
                .flatMapTry(uriParamAdder.appendAuthParams(auth))
                .map(createLink(COLLECTION, String.format("STAC %s collections", tenant)));
            }

            @Override
            public Try<Link> createCollectionLink(String collectionId, String collectionTitle) {
                return Try.of(() ->
                    WebMvcLinkBuilder.linkTo(
                        OGCFeaturesController.class,
                        getMethodNamedInClass(OGCFeaturesController.class, "collection"),
                        collectionId
                    ).toUri()
                )
                .flatMapTry(uriParamAdder.appendAuthParams(auth))
                .map(createLink(COLLECTION, collectionTitle));
            }

            @Override
            public Try<Link> createCollectionItemsLink(String collectionId) {
                return Try.of(() ->
                    WebMvcLinkBuilder.linkTo(
                        OGCFeaturesController.class,
                        getMethodNamedInClass(OGCFeaturesController.class, "features"),
                        collectionId, null, null, null
                    ).toUri()
                )
                .flatMapTry(uriParamAdder.appendAuthParams(auth))
                .map(createLink(ITEMS, "Collections items"));
            }

            @Override
            public Try<Link> createCollectionLink(Collection collection) {
                return createCollectionLink(collection.getId(), collection.getTitle());
            }

            @Override
            public Try<Link> createItemLink(String collectionId, String itemId) {
                return Try.of(() ->
                    WebMvcLinkBuilder.linkTo(
                        OGCFeaturesController.class,
                        getMethodNamedInClass(OGCFeaturesController.class, "item"),
                        collectionId,
                        itemId
                    ).toUri()
                )
                .flatMapTry(uriParamAdder.appendAuthParams(auth))
                .map(createLink(SELF, itemId));
            }

            @Override
            public Try<Link> createItemLink(Item item) {
                return createItemLink(item.getCollection(), item.getId());
            }
        };
    }

    @Override
    public SearchPageLinkCreator makeSearchPageLinkCreator(JWTAuthentication auth, Integer page, ItemSearchBody itemSearchBody) {
        return new SearchPageLinkCreator() {

            @Override
            public Option<URI> searchAll() {
                return Try.of(() ->
                        WebMvcLinkBuilder.linkTo(
                                ItemSearchController.class,
                                getMethodNamedInClass(ItemSearchController.class, "simple")
                        ).toUri()
                )
                .flatMapTry(uriParamAdder.appendAuthParams(auth))
                .toOption();
            }

            private Option<URI> createPageLink(int i, ItemSearchBody itemSearchBody, JWTAuthentication auth) {
                String itemBodyB64 = toBase64(gson.toJson(itemSearchBody));
                return Try.of(() ->
                    WebMvcLinkBuilder.linkTo(
                        ItemSearchController.class,
                        getMethodNamedInClass(ItemSearchController.class, "otherPage"),
                        itemBodyB64,
                        i
                    ).toUri()
                )
                .flatMapTry(uriParamAdder.appendAuthParams(auth))
                .flatMapTry(uriParamAdder.appendParams(HashMap.of(
                    SEARCH_ITEMBODY_QUERY_PARAM, itemBodyB64,
                    PAGE_QUERY_PARAM, "" + i
                )))
                .onSuccess(u -> LOGGER.debug("URI after  adding token: {}", u))
                .onFailure(t -> LOGGER.error("Failure creating page link: {}", t.getMessage(), t))
                .toOption();
            }

            @Override
            public Option<URI> createNextPageLink(ItemCollectionResponse itemCollection) {
                return createPageLink(page + 1, itemSearchBody, auth);
            }

            @Override
            public Option<URI> createPrevPageLink(ItemCollectionResponse itemCollection) {
                return page == 0 ? none() : createPageLink(page - 1, itemSearchBody, auth);
            }

            @Override
            public Option<URI> createSelfPageLink(ItemCollectionResponse itemCollection) {
                return createPageLink(page, itemSearchBody, auth);
            }
        };
    }

    private <T> Method getMethodNamedInClass(Class<T> type, String methodName) {
        return Stream.of(type.getDeclaredMethods())
            .filter(m -> methodName.equals(m.getName()))
            .head();
    }

    // @formatter:on

}
