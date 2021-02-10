package fr.cnes.regards.modules.catalog.stac.service.RegardsStacCollectionConverter;

import fr.cnes.regards.framework.module.rest.exception.EntityNotFoundException;
import fr.cnes.regards.framework.module.rest.exception.EntityOperationForbiddenException;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.framework.urn.UniformResourceName;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Collection;
import fr.cnes.regards.modules.dam.domain.entities.AbstractEntity;
import fr.cnes.regards.modules.dam.domain.entities.feature.EntityFeature;
import fr.cnes.regards.modules.search.service.CatalogSearchService;
import io.vavr.control.Try;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RegardsStacCollectionConverterImpl implements IRegardsStacCollectionConverter {

    @Autowired
    private CatalogSearchService catalogSearchService;

    @Override
    public Try<Collection> convertRequest(String urn) {

        UniformResourceName resourceName = UniformResourceName.fromString(urn);

        AbstractEntity abstractEntity = null;
        if (resourceName.getEntityType().equals(EntityType.DATASET) ||
                resourceName.getEntityType().equals(EntityType.COLLECTION)) {
            try {
                abstractEntity = catalogSearchService.get(resourceName);
            } catch (EntityOperationForbiddenException | EntityNotFoundException e) {
                return Try.failure(e);
            }
        }else {

            return Try.success(null);
        }
        abstractEntity.get
        return Try.success();
    }

}
