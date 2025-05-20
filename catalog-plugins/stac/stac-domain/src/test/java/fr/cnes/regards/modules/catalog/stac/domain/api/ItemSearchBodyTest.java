package fr.cnes.regards.modules.catalog.stac.domain.api;

import fr.cnes.regards.modules.catalog.stac.domain.AbstractDomainSerdeTest;

public class ItemSearchBodyTest extends AbstractDomainSerdeTest<ItemSearchBody> {

    @Override
    protected Class<ItemSearchBody> testedType() {
        return ItemSearchBody.class;
    }

}