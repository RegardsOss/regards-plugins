package fr.cnes.regards.modules.catalog.stac.service.RegardsStacCollectionConverter;

import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Collection;
import io.vavr.control.Try;


public interface IRegardsStacCollectionConverter {

    Try<Collection> convertRequest(String urn);
}
