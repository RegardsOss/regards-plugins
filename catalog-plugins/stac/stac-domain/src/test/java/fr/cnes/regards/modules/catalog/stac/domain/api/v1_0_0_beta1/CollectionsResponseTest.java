package fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1;

import fr.cnes.regards.modules.catalog.stac.domain.AbstractDomainSerdeTest;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.Catalog;

public class CollectionsResponseTest  extends AbstractDomainSerdeTest<CollectionsResponse> {

    @Override
    protected Class<CollectionsResponse> testedType() {
        return CollectionsResponse.class;
    }

}