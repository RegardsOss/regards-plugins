package fr.cnes.regards.modules.catalog.stac.domain.spec.geo;

import fr.cnes.regards.modules.catalog.stac.domain.AbstractDomainSerdeTest;

public class BBoxTest extends AbstractDomainSerdeTest<BBox> {

    @Override
    protected Class<BBox> testedType() {
        return BBox.class;
    }

}