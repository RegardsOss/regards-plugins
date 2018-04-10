package fr.cnes.regards.modules.storage.plugins.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import fr.cnes.regards.framework.feign.security.FeignSecurityManager;
import fr.cnes.regards.framework.hateoas.HateoasUtils;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.modules.entities.gson.IAttributeHelper;
import fr.cnes.regards.modules.models.client.IAttributeModelClient;
import fr.cnes.regards.modules.models.domain.attributes.AttributeModel;

/**
 * Helper class to retrieve model attributes
 * Copy/Paste from CatalogAttributeHelper created by Marc Sordi (feel free to ask him questions better than me)
 * @author Sylvain Vissere-Guerinet
 */
@Component
public class CatalogSecurityDelegationAttributeHelper implements IAttributeHelper {

    /**
     * Runtime tenant resolver
     */
    @Autowired
    private IRuntimeTenantResolver runtimeTenantResolver;

    /**
     * Feign client for rs-dam {@link AttributeModel} controller. Autowired by Spring.
     */
    @Autowired
    private IAttributeModelClient attributeModelClient;

    @Override
    public List<AttributeModel> getAllAttributes(String pTenant) {
        try {
            runtimeTenantResolver.forceTenant(pTenant);
            FeignSecurityManager.asSystem();

            ResponseEntity<List<Resource<AttributeModel>>> resources = attributeModelClient.getAttributes(null, null);
            if (resources != null) {
                return HateoasUtils.unwrapList(resources.getBody());
            }
        } finally {
            runtimeTenantResolver.clearTenant();
            FeignSecurityManager.reset();
        }
        return null;
    }
}
