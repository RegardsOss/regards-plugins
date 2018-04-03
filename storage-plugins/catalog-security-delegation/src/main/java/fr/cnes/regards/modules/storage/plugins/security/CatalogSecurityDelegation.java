/*
 * LICENSE_PLACEHOLDER
 */
package fr.cnes.regards.modules.storage.plugins.security;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import feign.FeignException;
import fr.cnes.regards.framework.authentication.IAuthenticationResolver;
import fr.cnes.regards.framework.feign.security.FeignSecurityManager;
import fr.cnes.regards.framework.module.rest.exception.EntityNotFoundException;
import fr.cnes.regards.framework.module.rest.utils.HttpUtils;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.oais.urn.UniformResourceName;
import fr.cnes.regards.modules.accessrights.client.IProjectUsersClient;
import fr.cnes.regards.modules.entities.domain.AbstractEntity;
import fr.cnes.regards.modules.entities.domain.DataObject;
import fr.cnes.regards.modules.search.client.ISearchClient;
import fr.cnes.regards.modules.storage.dao.IAIPDao;
import fr.cnes.regards.modules.storage.domain.AIP;
import fr.cnes.regards.modules.storage.domain.plugin.ISecurityDelegation;

/**
 * Default {@link ISecurityDelegation} implementation using rs-catalog to check access rights
 *
 * @author Sylvain VISSIERE-GUERINET
 */
@Plugin(author = "REGARDS Team", description = "Plugin handling the security thanks to catalog",
        id = "CatalogSecurityDelegation", version = "1.0", contact = "regards@c-s.fr", licence = "GPLv3",
        owner = "CNES", url = "https://regardsoss.github.io/")
public class CatalogSecurityDelegation implements ISecurityDelegation {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogSecurityDelegation.class);

    /**
     * {@link ISearchClient} instance
     */
    @Autowired
    private ISearchClient searchClient;

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
    public boolean hasAccess(String ipId) throws EntityNotFoundException {
        try {
            FeignSecurityManager.asUser(authenticationResolver.getUser(), authenticationResolver.getRole());
            ResponseEntity<Resource<AbstractEntity>> catalogResponse = searchClient
                    .getEntity(UniformResourceName.fromString(ipId));
            if (!HttpUtils.isSuccess(catalogResponse.getStatusCode()) && !catalogResponse.getStatusCode()
                    .equals(HttpStatus.NOT_FOUND)) {
                // either there was an error or it was forbidden
                return false;
            }
            // if we could get the entity, then we can access the aip
            if (HttpUtils.isSuccess(catalogResponse.getStatusCode())) {
                AbstractEntity entity = catalogResponse.getBody().getContent();
                // lets check if the AIP is of type DATA, in this case, lets check downloadable attribute
                if(entity instanceof DataObject) {
                    return ((DataObject) entity).getDownloadable();
                }
                // otherwise, we always have access
                return true;
            }
            // we now have receive a not found from catalog, lets check if AIP exist in our database and if the user is an admin.
            Optional<AIP> aip = aipDao.findOneByIpId(ipId);
            if (!aip.isPresent()) {
                throw new EntityNotFoundException(ipId, AIP.class);
            }
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
            //there was an error, lets assume that we cannot access the aip
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
                // if a problem occured, we cannot now if current user is an admin or not: lets assume he is not
                return false;
            }
        } finally {
            FeignSecurityManager.reset();
        }
    }
}
