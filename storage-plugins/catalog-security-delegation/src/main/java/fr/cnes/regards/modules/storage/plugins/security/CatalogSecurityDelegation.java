/*
 * Copyright 2017-2019 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.storage.plugins.security;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.google.common.collect.Collections2;

import feign.FeignException;
import fr.cnes.regards.framework.authentication.IAuthenticationResolver;
import fr.cnes.regards.framework.feign.security.FeignSecurityManager;
import fr.cnes.regards.framework.module.rest.utils.HttpUtils;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.oais.urn.UniformResourceName;
import fr.cnes.regards.modules.accessrights.client.IProjectUsersClient;
import fr.cnes.regards.modules.search.client.IAccessRights;
import fr.cnes.regards.modules.storage.dao.IAIPDao;
import fr.cnes.regards.modules.storage.domain.plugin.ISecurityDelegation;

/**
 * Default {@link ISecurityDelegation} implementation using rs-catalog to check access rights
 * @author Sylvain VISSIERE-GUERINET
 */
@Plugin(author = "REGARDS Team", description = "Plugin handling the security thanks to catalog",
        id = "CatalogSecurityDelegation", version = "1.0", contact = "regards@c-s.fr", license = "GPLv3",
        owner = "CNES", url = "https://regardsoss.github.io/")
public class CatalogSecurityDelegation implements ISecurityDelegation {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogSecurityDelegation.class);

    @Autowired
    private IAccessRights accessRights;

    /**
     * {@link IProjectUsersClient} instance
     */
    @Autowired
    private IProjectUsersClient projectUsersClient;

    /**
     * {@link IAIPDao} instance
     */
    @Autowired
    private IAIPDao aipDao;

    /**
     * {@link IAuthenticationResolver} instance
     */
    @Autowired
    private IAuthenticationResolver authenticationResolver;

    @Override
    public Set<UniformResourceName> hasAccess(Collection<UniformResourceName> urns) {
        try {
            FeignSecurityManager.asUser(authenticationResolver.getUser(), authenticationResolver.getRole());
            // If user is admin => return all AIP_ID from storage db
            ResponseEntity<Boolean> adminResponse = projectUsersClient.isAdmin(authenticationResolver.getUser());
            if (HttpUtils.isSuccess(adminResponse.getStatusCode())) {
                // if no problem occurred then lets give the answer from rs-admin
                if (adminResponse.getBody()) {
                    // Return only existing AIPs from given list
                    return aipDao.findUrnsByAipIdIn(Collections2.transform(urns, UniformResourceName::toString))
                            .collect(Collectors.toSet());
                }
            }
            // Else, ask catalog for AIP which has access
            ResponseEntity<Set<UniformResourceName>> catalogResponse = accessRights.hasAccess(urns);
            if (!HttpUtils.isSuccess(catalogResponse.getStatusCode())) {
                // either there was an error or it was forbidden
                return Collections.emptySet();
            } else { // if we could get the entity, then we can access the aip
                return catalogResponse.getBody();
            }
        } catch (FeignException e) {
            LOGGER.error(String
                    .format("Issue with feign while trying to check if the user has access to aips %s",
                            urns.stream().map(UniformResourceName::toString).collect(Collectors.joining(", "))),
                         e);
            // there was an error, lets assume that we cannot access none of the aips
            return Collections.emptySet();
        } finally {
            FeignSecurityManager.reset();
        }
    }

    @Override
    public boolean hasAccess(String ipId) {
        try {
            FeignSecurityManager.asUser(authenticationResolver.getUser(), authenticationResolver.getRole());
            UniformResourceName urn = UniformResourceName.fromString(ipId);
            ResponseEntity<Boolean> catalogResponse = accessRights.hasAccess(urn);
            if (!HttpUtils.isSuccess(catalogResponse.getStatusCode())
                    && !catalogResponse.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                // either there was an error or it was forbidden
                return false;
            }
            // if we could get the entity, then we can access the aip
            if (HttpUtils.isSuccess(catalogResponse.getStatusCode())) {
                return catalogResponse.getBody();
            }
            // we now have received a not found from catalog, lets check if AIP exists in our database and if the user
            // is an admin.
            if (!aipDao.findOneByAipId(urn.toString()).isPresent()) {
                return false;
            }
            // AIP hasn't been found => it is not indexed but it is present into storage (ie Aip has been stored but not
            // yet indexed => it is too soon), only administrator can access it
            ResponseEntity<Boolean> adminResponse = projectUsersClient.isAdmin(authenticationResolver.getUser());
            if (HttpUtils.isSuccess(adminResponse.getStatusCode())) {
                // if no problem occurred then lets give the answer from rs-admin
                return adminResponse.getBody();
            } else {
                // if a problem occurred, we cannot know if current user is an admin or not: lets assume he is not
                return false;
            }
        } catch (FeignException e) {
            LOGGER.error(String.format("Issue with feign while trying to check if the user has access to aip %s", ipId),
                         e);
            // there was an error, lets assume that we cannot access the aip
            return false;
        } finally {
            FeignSecurityManager.reset();
        }
    }

    @Override
    public boolean hasAccessToListFeature() {
        try {
            FeignSecurityManager.asSystem();
            ResponseEntity<Boolean> adminResponse = projectUsersClient.isAdmin(authenticationResolver.getUser());
            if (HttpUtils.isSuccess(adminResponse.getStatusCode())) {
                // if no problem occured then lets give the answer from rs-admin
                return adminResponse.getBody();
            } else {
                // if a problem occured, we cannot know if current user is an admin or not: lets assume he is not
                return false;
            }
        } catch (FeignException e) {
            LOGGER.error(e.getMessage(), e);
            // if a problem occured, we cannot know if current user is an admin or not: lets assume he is not
            return false;
        } finally {
            FeignSecurityManager.reset();
        }
    }
}
