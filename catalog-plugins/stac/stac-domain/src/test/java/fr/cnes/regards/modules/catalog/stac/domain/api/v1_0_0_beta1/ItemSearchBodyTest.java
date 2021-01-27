package fr.cnes.regards.modules.catalog.stac.domain.api.v1_0_0_beta1;

import fr.cnes.regards.modules.catalog.stac.domain.AbstractDomainSerdeTest;

public class ItemSearchBodyTest extends AbstractDomainSerdeTest<ItemSearchBody> {

    @Override protected boolean logValues() { return true; }

    @Override
    protected Class<ItemSearchBody> testedType() {
        return ItemSearchBody.class;
    }

}