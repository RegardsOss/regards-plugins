package fr.cnes.regards.modules.catalog.stac.domain.api;

import fr.cnes.regards.modules.catalog.stac.domain.AbstractDomainSerdeTest;

public class ConformanceResponseTest extends AbstractDomainSerdeTest<ConformanceResponse> {

    @Override
    protected Class<ConformanceResponse> testedType() {
        return ConformanceResponse.class;
    }

}