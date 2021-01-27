package fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1;

import fr.cnes.regards.modules.catalog.stac.domain.AbstractDomainSerdeTest;

public class ConformanceResponseTest extends AbstractDomainSerdeTest<ConformanceResponse> {

    @Override
    protected Class<ConformanceResponse> testedType() {
        return ConformanceResponse.class;
    }

}