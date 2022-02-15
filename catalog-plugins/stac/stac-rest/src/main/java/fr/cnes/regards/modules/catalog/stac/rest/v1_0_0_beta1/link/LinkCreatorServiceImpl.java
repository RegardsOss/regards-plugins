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

package fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.link;

import com.google.gson.Gson;
import fr.cnes.regards.framework.feign.security.FeignSecurityManager;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.security.utils.jwt.JWTAuthentication;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.extension.searchcol.CollectionSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Collection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Item;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link;
import fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.CoreController;
import fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.ItemSearchController;
import fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.OGCFeaturesController;
import fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.extension.download.CollectionDownloadController;
import fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.extension.searchcol.CollectionSearchController;
import fr.cnes.regards.modules.catalog.stac.service.link.DownloadLinkCreator;
import fr.cnes.regards.modules.catalog.stac.service.link.OGCFeatLinkCreator;
import fr.cnes.regards.modules.catalog.stac.service.link.SearchPageLinkCreator;
import fr.cnes.regards.modules.catalog.stac.service.link.UriParamAdder;
import fr.cnes.regards.modules.catalog.stac.service.utils.Base64Codec;
import io.vavr.collection.HashMap;
import io.vavr.collection.Stream;
import io.vavr.control.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.net.URI;

import static fr.cnes.regards.modules.catalog.stac.domain.error.StacRequestCorrelationId.*;
import static fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.common.Link.Relations.*;
import static fr.cnes.regards.modules.catalog.stac.domain.utils.TryDSL.tryOf;
import static fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.utils.StacApiConstants.*;
import static io.vavr.control.Option.none;

/**
 * Allows generating link creators.
 */
@Service
public class LinkCreatorServiceImpl implements LinkCreatorService, Base64Codec {

    private static final Logger LOGGER = LoggerFactory.getLogger(LinkCreatorServiceImpl.class);

    private final static String URI_PATTERN_MESSAGE = "URI after  adding token: {}";

    private final static String FAILURE_PATTERN_MESSAGE = "Failure creating page link: {}";

    private final IRuntimeTenantResolver tenantResolver;

    private final UriParamAdder uriParamAdder;

    private final Gson gson;

    // @formatter:off

    @Autowired
    public LinkCreatorServiceImpl(
            IRuntimeTenantResolver tenantResolver,
            UriParamAdder uriParamAdder,
            Gson gson
    ) {
        this.tenantResolver = tenantResolver;
        this.uriParamAdder = uriParamAdder;
        this.gson = gson;
    }

    @Override
    public OGCFeatLinkCreator makeOGCFeatLinkCreator(JWTAuthentication auth) {
        String tenant = tenantResolver.getTenant();

        return new OGCFeatLinkCreator() {

            @Override
            public Option<Link> createRootLink() {
                return tryOf(() ->
                    WebMvcLinkBuilder.linkTo(
                        CoreController.class,
                        getMethodNamedInClass(CoreController.class, "root")
                    ).toUri()
                )
                .flatMapTry(uriParamAdder.appendAuthParams(auth))
                .onFailure(t -> warn(LOGGER, "Failed to create root link", t))
                .toOption()
                .map(createLink(ROOT, String.format("STAC %s root", tenant)));
            }

            @Override
            public Option<Link> createCollectionsLink() {
                return tryOf(() ->
                        WebMvcLinkBuilder.linkTo(
                                OGCFeaturesController.class,
                                getMethodNamedInClass(OGCFeaturesController.class, "collections")
                        ).toUri()
                )
                .flatMapTry(uriParamAdder.appendAuthParams(auth))
                .onFailure(t -> warn(LOGGER, "Failed to create collections link", t))
                .toOption()
                .map(createLink(COLLECTION, String.format("STAC %s collections", tenant)));
            }

            @Override
            public Option<Link> createCollectionLink(String collectionId, String collectionTitle) {
                return tryOf(() ->
                    WebMvcLinkBuilder.linkTo(
                        OGCFeaturesController.class,
                        getMethodNamedInClass(OGCFeaturesController.class, "collection"),
                        collectionId
                    ).toUri()
                )
                .flatMapTry(uriParamAdder.appendAuthParams(auth))
                .onFailure(t -> warn(LOGGER, "Failed to create collection link: {}", collectionId, t))
                .toOption()
                .map(createLink(COLLECTION, collectionTitle));
            }

            @Override
            public Option<Link> createCollectionItemsLink(String collectionId) {
                return tryOf(() ->
                    WebMvcLinkBuilder.linkTo(
                        OGCFeaturesController.class,
                        getMethodNamedInClass(OGCFeaturesController.class, "features"),
                        collectionId, null, null, null
                    ).toUri()
                )
                .flatMapTry(uriParamAdder.appendAuthParams(auth))
                .onFailure(t -> warn(LOGGER, "Failed to create collection items link: {}", collectionId, t))
                .toOption()
                .map(createLink(ITEMS, "Collections items"));
            }

            @Override
            public Option<Link> createCollectionLink(Collection collection) {
                return createCollectionLink(collection.getId(), collection.getTitle());
            }

            @Override
            public Option<Link> createItemLink(String collectionId, String itemId) {
                return tryOf(() ->
                    WebMvcLinkBuilder.linkTo(
                        OGCFeaturesController.class,
                        getMethodNamedInClass(OGCFeaturesController.class, "feature"),
                        collectionId,
                        itemId
                    ).toUri()
                )
                .flatMapTry(uriParamAdder.appendAuthParams(auth))
                .onFailure(t -> warn(LOGGER, "Failed to create item link: {}", itemId, t))
                .toOption()
                .map(createLink(SELF, itemId));
            }

            @Override
            public Option<Link> createItemLink(Item item) {
                return createItemLink(item.getCollection(), item.getId());
            }
        };
    }

    // @formatter:on

    @Override
    public SearchPageLinkCreator makeSearchPageLinkCreator(JWTAuthentication auth, Integer page,
            ItemSearchBody itemSearchBody) {
        return new SearchPageLinkCreator() {

            @Override
            public Option<URI> searchAll() {
                return tryOf(() -> WebMvcLinkBuilder
                        .linkTo(ItemSearchController.class, getMethodNamedInClass(ItemSearchController.class, "simple"))
                        .toUri()).flatMapTry(uriParamAdder.appendAuthParams(auth))
                        .onFailure(t -> warn(LOGGER, "Failed to create search all", t)).toOption();
            }

            private Option<URI> createPageLink(int i, ItemSearchBody itemSearchBody, JWTAuthentication auth) {
                String itemBodyB64 = toBase64(gson.toJson(itemSearchBody));
                return tryOf(() -> WebMvcLinkBuilder.linkTo(ItemSearchController.class,
                                                            getMethodNamedInClass(ItemSearchController.class,
                                                                                  "otherPage"), itemBodyB64, i).toUri())
                        .flatMapTry(uriParamAdder.appendAuthParams(auth)).flatMapTry(uriParamAdder.appendParams(
                                HashMap.of(SEARCH_ITEMBODY_QUERY_PARAM, itemBodyB64, PAGE_QUERY_PARAM, "" + i)))
                        .onSuccess(u -> debug(LOGGER, URI_PATTERN_MESSAGE, u))
                        .onFailure(t -> error(LOGGER, FAILURE_PATTERN_MESSAGE, t.getMessage(), t)).toOption();
            }

            @Override
            public Option<URI> createNextPageLink() {
                return createPageLink(page + 1, itemSearchBody, auth);
            }

            @Override
            public Option<URI> createPrevPageLink() {
                return page == 0 ? none() : createPageLink(page - 1, itemSearchBody, auth);
            }

            @Override
            public Option<URI> createSelfPageLink() {
                return createPageLink(page, itemSearchBody, auth);
            }
        };
    }

    @Override
    public SearchPageLinkCreator makeCollectionItemsPageLinkCreator(JWTAuthentication auth, Integer page,
            String collectionId) {
        return new SearchPageLinkCreator() {

            @Override
            public Option<URI> searchAll() {
                return Option.none(); // unused when generating collection items page
            }

            private Option<URI> createPageLink(int i, JWTAuthentication auth) {
                return tryOf(() -> WebMvcLinkBuilder.linkTo(OGCFeaturesController.class,
                                                            getMethodNamedInClass(OGCFeaturesController.class,
                                                                                  "features"), collectionId, i).toUri())
                        .flatMapTry(uriParamAdder.appendAuthParams(auth))
                        .flatMapTry(uriParamAdder.appendParams(HashMap.of(PAGE_QUERY_PARAM, "" + i)))
                        .onSuccess(u -> debug(LOGGER, URI_PATTERN_MESSAGE, u))
                        .onFailure(t -> error(LOGGER, FAILURE_PATTERN_MESSAGE, t.getMessage(), t)).toOption();
            }

            @Override
            public Option<URI> createNextPageLink() {
                return createPageLink(page + 1, auth);
            }

            @Override
            public Option<URI> createPrevPageLink() {
                return page == 1 ? none() : createPageLink(page - 1, auth);
            }

            @Override
            public Option<URI> createSelfPageLink() {
                return createPageLink(page, auth);
            }
        };
    }

    private <T> Method getMethodNamedInClass(Class<T> type, String methodName) {
        return Stream.of(type.getDeclaredMethods()).filter(m -> methodName.equals(m.getName())).head();
    }

    @Override
    public SearchPageLinkCreator makeSearchCollectionPageLinkCreation(JWTAuthentication auth, Integer page,
            CollectionSearchBody collectionSearchBody) {
        return new SearchPageLinkCreator() {

            @Override
            public Option<URI> searchAll() {
                return tryOf(() -> WebMvcLinkBuilder.linkTo(CollectionSearchController.class,
                                                            getMethodNamedInClass(CollectionSearchController.class,
                                                                                  "simple")).toUri())
                        .flatMapTry(uriParamAdder.appendAuthParams(auth))
                        .onFailure(t -> warn(LOGGER, "Failed to create search all collections", t)).toOption();
            }

            private Option<URI> createPageLink(int i, CollectionSearchBody collectionSearchBody,
                    JWTAuthentication auth) {
                String collectionBodyB64 = toBase64(gson.toJson(collectionSearchBody));
                return tryOf(() -> WebMvcLinkBuilder.linkTo(CollectionSearchController.class,
                                                            getMethodNamedInClass(CollectionSearchController.class,
                                                                                  "otherPage"), collectionBodyB64, i)
                        .toUri()).flatMapTry(uriParamAdder.appendAuthParams(auth)).flatMapTry(uriParamAdder
                                                                                                      .appendParams(
                                                                                                              HashMap.of(
                                                                                                                      SEARCH_COLLECTIONBODY_QUERY_PARAM,
                                                                                                                      collectionBodyB64,
                                                                                                                      PAGE_QUERY_PARAM,
                                                                                                                      ""
                                                                                                                              + i)))
                        .onSuccess(u -> debug(LOGGER, URI_PATTERN_MESSAGE, u))
                        .onFailure(t -> error(LOGGER, FAILURE_PATTERN_MESSAGE, t.getMessage(), t)).toOption();
            }

            @Override
            public Option<URI> createNextPageLink() {
                return createPageLink(page + 1, collectionSearchBody, auth);
            }

            @Override
            public Option<URI> createPrevPageLink() {
                return page == 0 ? none() : createPageLink(page - 1, collectionSearchBody, auth);
            }

            @Override
            public Option<URI> createSelfPageLink() {
                return createPageLink(page, collectionSearchBody, auth);
            }
        };
    }

    @Override
    public DownloadLinkCreator makeDownloadLinkCreator(JWTAuthentication auth,
            FeignSecurityManager feignSecurityManager) {
        return new DownloadLinkCreator() {

            private String systemToken = null;

            @Override
            public Option<URI> createAllCollectionsDownloadLink(String tinyUrlId) {
                return tryOf(() -> WebMvcLinkBuilder.linkTo(CollectionDownloadController.class,
                                                            getMethodNamedInClass(CollectionDownloadController.class,
                                                                                  "downloadAllCollectionsAsZip"))
                        .toUri()).flatMapTry(uriParamAdder.appendTinyUrl(tinyUrlId))
                        .flatMapTry(uriParamAdder.appendAuthParams(auth))
                        .onFailure(t -> warn(LOGGER, "Failed to create all collections download link", t)).toOption();
            }

            @Override
            public Option<URI> createSingleCollectionDownloadLink(String collectionId, String tinyUrlId) {
                return tryOf(() -> WebMvcLinkBuilder.linkTo(CollectionDownloadController.class,
                                                            getMethodNamedInClass(CollectionDownloadController.class,
                                                                                  "downloadSingeCollectionAsZip"),
                                                            collectionId).toUri())
                        .flatMapTry(uriParamAdder.appendTinyUrl(tinyUrlId))
                        .flatMapTry(uriParamAdder.appendAuthParams(auth))
                        .onFailure(t -> warn(LOGGER, "Failed to create single collection download link", t)).toOption();
            }

            @Override
            public Option<URI> createSingleCollectionSampleDownloadLink(String collectionId, String tinyUrlId) {
                return tryOf(() -> WebMvcLinkBuilder.linkTo(CollectionDownloadController.class,
                                                            getMethodNamedInClass(CollectionDownloadController.class,
                                                                                  "downloadSingeCollectionSampleAsZip"),
                                                            collectionId).toUri())
                        .flatMapTry(uriParamAdder.appendTinyUrl(tinyUrlId))
                        .flatMapTry(uriParamAdder.appendAuthParams(auth))
                        .onFailure(t -> warn(LOGGER, "Failed to create single collection sample download link", t)).toOption();
            }

            @Override
            public String appendAuthParamsForNginx(String uri) {
                return tryOf(() -> URI.create(uri)).flatMapTry(uriParamAdder.appendAuthParams(auth))
                        .onFailure(t -> warn(LOGGER, "Failed to append authentication parameters")).get().toString();
            }

            @Override
            public String getSystemToken() {
                if (systemToken == null) {
                    try {
                        // Generate client token
                        FeignSecurityManager.asSystem();
                        systemToken = feignSecurityManager.getToken();
                    } finally {
                        FeignSecurityManager.reset();
                    }
                }
                return systemToken;
            }
        };
    }
}
