package fr.cnes.regards.modules.catalog.stac.service.criterion;

import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.spec.geo.BBox;
import fr.cnes.regards.modules.indexer.domain.criterion.BoundaryBoxCriterion;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import io.vavr.collection.List;
import io.vavr.control.Option;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BBoxCriterionBuilderTest {

    @Test
    public void testBuildCriterionEmpty() {
        // GIVEN
        List<StacProperty> properties = List.of();
        // WHEN
        Option<ICriterion> criterion = new BBoxCriterionBuilder().buildCriterion(properties, null);
        // THEN
        assertThat(criterion).isEmpty();
    }

    @Test
    public void testBuildCriterion() {
        // GIVEN
        List<StacProperty> properties = List.of();
        BBox bbox = new BBox(0, 0, 1, 1);
        // WHEN
        Option<ICriterion> criterion = new BBoxCriterionBuilder().buildCriterion(properties, bbox);
        // THEN
        assertThat(criterion).isNotEmpty();
        assertThat(criterion.get()).isInstanceOf(BoundaryBoxCriterion.class);
        assertThat(((BoundaryBoxCriterion) criterion.get()).getMinX()).isEqualTo(0);
        assertThat(((BoundaryBoxCriterion) criterion.get()).getMinY()).isEqualTo(0);
        assertThat(((BoundaryBoxCriterion) criterion.get()).getMaxX()).isEqualTo(1);
        assertThat(((BoundaryBoxCriterion) criterion.get()).getMaxY()).isEqualTo(1);
    }

}