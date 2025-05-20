package fr.cnes.regards.modules.catalog.stac.service.criterion;

import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.indexer.domain.criterion.OrCriterion;
import fr.cnes.regards.modules.indexer.domain.criterion.StringMatchCriterion;
import io.vavr.collection.List;
import io.vavr.control.Option;
import org.junit.Test;

import static fr.cnes.regards.modules.catalog.stac.domain.StacProperties.ID_PROPERTY_NAME;
import static fr.cnes.regards.modules.indexer.domain.criterion.MatchType.EQUALS;
import static org.assertj.core.api.Assertions.assertThat;

public class IdentitiesCriterionBuilderTest {

    @Test
    public void testBuildCriterionEmpty() {
        // GIVEN
        List<StacProperty> properties = List.of();
        // WHEN
        Option<ICriterion> criterion = new IdentitiesCriterionBuilder().buildCriterion(properties, List.empty());
        // THEN
        assertThat(criterion).isEmpty();
    }

    @Test
    public void testBuildCriterionNull() {
        // GIVEN
        List<StacProperty> properties = List.of();
        // WHEN
        Option<ICriterion> criterion = new IdentitiesCriterionBuilder().buildCriterion(properties, null);
        // THEN
        assertThat(criterion).isEmpty();
    }

    @Test
    public void testBuildCriterionOne() {
        // GIVEN
        List<StacProperty> properties = List.of();
        List<String> ids = List.of("id1");
        // WHEN
        Option<ICriterion> criterion = new IdentitiesCriterionBuilder().buildCriterion(properties, ids);
        // THEN
        assertThat(criterion).isNotEmpty();
        assertThat(criterion.get()).isInstanceOf(StringMatchCriterion.class);
        assertThat(((StringMatchCriterion) criterion.get()).getName()).isEqualTo(ID_PROPERTY_NAME);
        assertThat(((StringMatchCriterion) criterion.get()).getValue()).isEqualTo("id1");
        assertThat(((StringMatchCriterion) criterion.get()).getType()).isEqualTo(EQUALS);
    }

    @Test
    public void testBuildCriterionMany() {
        // GIVEN
        List<StacProperty> properties = List.of();
        List<String> ids = List.of("id1", "id2");
        // WHEN
        Option<ICriterion> criterion = new IdentitiesCriterionBuilder().buildCriterion(properties, ids);
        // THEN
        assertThat(criterion).isNotEmpty();
        assertThat(criterion.get()).isInstanceOf(OrCriterion.class);

        OrCriterion andCrit = (OrCriterion) criterion.get();
        java.util.List<ICriterion> andCrits = andCrit.getCriterions();
        assertThat(andCrits).hasSize(2);

        assertThat(andCrits.get(0)).isInstanceOf(StringMatchCriterion.class);
        assertThat(((StringMatchCriterion) andCrits.get(0)).getName()).isEqualTo(ID_PROPERTY_NAME);
        assertThat(((StringMatchCriterion) andCrits.get(0)).getValue()).isEqualTo("id1");
        assertThat(((StringMatchCriterion) andCrits.get(0)).getType()).isEqualTo(EQUALS);

        assertThat(andCrits.get(1)).isInstanceOf(StringMatchCriterion.class);
        assertThat(((StringMatchCriterion) andCrits.get(1)).getName()).isEqualTo(ID_PROPERTY_NAME);
        assertThat(((StringMatchCriterion) andCrits.get(1)).getValue()).isEqualTo("id2");
        assertThat(((StringMatchCriterion) andCrits.get(1)).getType()).isEqualTo(EQUALS);
    }

}