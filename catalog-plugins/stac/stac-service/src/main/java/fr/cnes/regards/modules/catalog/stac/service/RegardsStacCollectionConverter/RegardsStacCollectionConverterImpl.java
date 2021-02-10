package fr.cnes.regards.modules.catalog.stac.service.RegardsStacCollectionConverter;

import fr.cnes.regards.framework.module.rest.exception.EntityNotFoundException;
import fr.cnes.regards.framework.module.rest.exception.EntityOperationForbiddenException;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.framework.urn.UniformResourceName;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Collection;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.collection.Provider;
import fr.cnes.regards.modules.dam.domain.entities.AbstractEntity;
import fr.cnes.regards.modules.dam.domain.entities.DataObject;
import fr.cnes.regards.modules.dam.domain.entities.feature.CollectionFeature;
import fr.cnes.regards.modules.dam.domain.entities.feature.EntityFeature;
import fr.cnes.regards.modules.search.service.CatalogSearchService;
import io.vavr.collection.List;
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

        fr.cnes.regards.modules.dam.domain.entities.Collection damCollection = null;
        if (resourceName.getEntityType().equals(EntityType.DATASET) ||
                resourceName.getEntityType().equals(EntityType.COLLECTION)) {
            try {
                damCollection = catalogSearchService.get(resourceName);
            } catch (EntityOperationForbiddenException | EntityNotFoundException e) {
                return Try.failure(e);
            }
        }else {

            return Try.success(null);
        }
        Provider provider = new Provider("", "", null, List.empty());
        Collection collection = new Collection("1.0.0-beta2", List.empty(), damCollection.getLabel(), damCollection.getId().toString(),
                damCollection.getModel().getDescription(), List.empty(), List.empty(), "",
                List.of(provider), null, null);

        return Try.success(collection);
    }

}
