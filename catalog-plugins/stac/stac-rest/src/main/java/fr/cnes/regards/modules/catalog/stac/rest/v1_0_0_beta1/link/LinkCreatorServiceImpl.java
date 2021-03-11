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

import fr.cnes.regards.framework.security.role.DefaultRole;
import fr.cnes.regards.framework.security.utils.jwt.JWTAuthentication;
import fr.cnes.regards.framework.security.utils.jwt.JWTService;
import fr.cnes.regards.framework.security.utils.jwt.UserDetails;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemCollectionResponse;
import fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1.ItemSearchBody;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Collection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Item;
import fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.ItemSearchController;
import fr.cnes.regards.modules.catalog.stac.rest.v1_0_0_beta1.OGCFeaturesController;
import fr.cnes.regards.modules.catalog.stac.service.link.OGCFeatLinkCreator;
import fr.cnes.regards.modules.catalog.stac.service.link.SearchPageLinkCreator;
import io.vavr.CheckedFunction1;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.Stream;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.lang.reflect.Method;
import java.net.URI;

import static io.vavr.control.Option.none;

/**
 * Allows to generate link creators.
 */
@Service
public class LinkCreatorServiceImpl implements LinkCreatorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LinkCreatorServiceImpl.class);

    private final JWTService jwtService;

    // @formatter:off

    @Autowired
    public LinkCreatorServiceImpl(
            JWTService jwtService
    ) {
        this.jwtService = jwtService;
    }

    @Override
    public OGCFeatLinkCreator makeOGCFeatLinkCreator(JWTAuthentication auth) {

        return new OGCFeatLinkCreator() {

            @Override
            public Try<URI> createRootLink() {
                return Try.of(() ->
                        WebMvcLinkBuilder.linkTo(
                                OGCFeaturesController.class,
                                getMethodNamedInClass(OGCFeaturesController.class, "collections")
                        ).toUri()
                )
                        .flatMapTry(appendAuthParams(auth));
            }

            @Override
            public Try<URI> createCollectionLink(String collectionId) {
                return Try.of(() ->
                        WebMvcLinkBuilder.linkTo(
                                OGCFeaturesController.class,
                                getMethodNamedInClass(OGCFeaturesController.class, "collection"),
                                collectionId
                        ).toUri()
                )
                        .flatMapTry(appendAuthParams(auth));
            }

            @Override
            public Try<URI> createCollectionLink(Collection collection) {
                return createCollectionLink(collection.getId());
            }

            @Override
            public Try<URI> createItemLink(String collectionId, String itemId) {
                return Try.of(() ->
                        WebMvcLinkBuilder.linkTo(
                                OGCFeaturesController.class,
                                getMethodNamedInClass(OGCFeaturesController.class, "item"),
                                collectionId,
                                itemId
                        ).toUri()
                )
                        .flatMapTry(appendAuthParams(auth));
            }

            @Override
            public Try<URI> createItemLink(Item item) {
                return createItemLink(item.getCollection(), item.getId());
            }
        };
    }

    @Override
    public SearchPageLinkCreator makeSearchPageLinkCreator(JWTAuthentication auth, Integer page, ItemSearchBody itemSearchBody) {
        return new SearchPageLinkCreator() {

            private Option<URI> createPageLink(int i, ItemSearchBody itemSearchBody, JWTAuthentication auth) {
                return Try.of(() ->
                        WebMvcLinkBuilder.linkTo(
                                ItemSearchController.class,
                                getMethodNamedInClass(ItemSearchController.class, "otherPage"),
                                itemSearchBody,
                                i
                        ).toUri()
                )
                        .flatMapTry(appendAuthParams(auth))
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

    private Tuple2<String, String> makeAuthParam(
            JWTAuthentication auth
    ) {
        String tenant = auth.getTenant();
        UserDetails user = auth.getUser();
        String role = user.getRole();
        return DefaultRole.PUBLIC.name().equals(role)
            ? Tuple.of("scope", tenant)
            : Tuple.of("token", jwtService.generateToken(tenant, user.getLogin(), user.getEmail(), role));
    }

    private CheckedFunction1<URI, Try<URI>> appendAuthParams (JWTAuthentication auth) {
        return uri -> {
            Tuple2<String, String> authParam = makeAuthParam(auth);
            return Try.of(() ->
                UriComponentsBuilder.fromUri(uri).queryParam(authParam._1, authParam._2).build().toUri()
            );
        };
    }

    // @formatter:on

}
