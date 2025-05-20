package fr.cnes.regards.modules.catalog.stac.domain.api;

import fr.cnes.regards.modules.catalog.stac.domain.AbstractDomainSerdeTest;

public class CollectionsResponseTest extends AbstractDomainSerdeTest<CollectionsResponse> {

    @Override
    protected Class<CollectionsResponse> testedType() {
        return CollectionsResponse.class;
    }

}