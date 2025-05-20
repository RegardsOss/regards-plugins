package fr.cnes.regards.modules.catalog.stac.service.criterion;

import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.indexer.domain.criterion.OrCriterion;
import fr.cnes.regards.modules.indexer.domain.criterion.StringMatchCriterion;
import io.vavr.collection.List;
import io.vavr.control.Option;
import org.junit.Test;

import static fr.cnes.regards.modules.catalog.stac.domain.StacProperties.TAGS_PROPERTY_NAME;
import static fr.cnes.regards.modules.indexer.domain.criterion.MatchType.CONTAINS;
import static org.assertj.core.api.Assertions.assertThat;

public class CollectionsCriterionBuilderTest {

    @Test
    public void testBuildCriterionEmpty() {
        // GIVEN
        List<StacProperty> properties = List.of();
        // WHEN
        Option<ICriterion> criterion = new CollectionsCriterionBuilder().buildCriterion(properties, List.empty());
        // THEN
        assertThat(criterion).isEmpty();
    }

    @Test
    public void testBuildCriterionNull() {
        // GIVEN
        List<StacProperty> properties = List.of();
        // WHEN
        Option<ICriterion> criterion = new CollectionsCriterionBuilder().buildCriterion(properties, null);
        // THEN
        assertThat(criterion).isEmpty();
    }

    @Test
    public void testBuildCriterionOne() {
        // GIVEN
        List<StacProperty> properties = List.of();
        List<String> collections = List.of("coll1");
        // WHEN
        Option<ICriterion> criterion = new CollectionsCriterionBuilder().buildCriterion(properties, collections);
        // THEN
        assertThat(criterion).isNotEmpty();

        assertThat(criterion.get()).isInstanceOf(StringMatchCriterion.class);
        assertThat(((StringMatchCriterion) criterion.get()).getName()).isEqualTo(TAGS_PROPERTY_NAME);
        assertThat(((StringMatchCriterion) criterion.get()).getValue()).isEqualTo("coll1");
        assertThat(((StringMatchCriterion) criterion.get()).getType()).isEqualTo(CONTAINS);
    }

    @Test
    public void testBuildCriterionMany() {
        // GIVEN
        List<StacProperty> properties = List.of();
        List<String> collections = List.of("coll1", "coll2");
        // WHEN
        Option<ICriterion> criterion = new CollectionsCriterionBuilder().buildCriterion(properties, collections);
        // THEN
        assertThat(criterion).isNotEmpty();
        assertThat(criterion.get()).isInstanceOf(OrCriterion.class);

        OrCriterion orCriterion = (OrCriterion) criterion.get();
        java.util.List<ICriterion> andCrits = orCriterion.getCriterions();
        assertThat(andCrits).hasSize(2);

        assertThat(andCrits.get(0)).isInstanceOf(StringMatchCriterion.class);
        assertThat(((StringMatchCriterion) andCrits.get(0)).getName()).isEqualTo(TAGS_PROPERTY_NAME);
        assertThat(((StringMatchCriterion) andCrits.get(0)).getValue()).isEqualTo("coll1");
        assertThat(((StringMatchCriterion) andCrits.get(0)).getType()).isEqualTo(CONTAINS);

        assertThat(andCrits.get(1)).isInstanceOf(StringMatchCriterion.class);
        assertThat(((StringMatchCriterion) andCrits.get(1)).getName()).isEqualTo(TAGS_PROPERTY_NAME);
        assertThat(((StringMatchCriterion) andCrits.get(1)).getValue()).isEqualTo("coll2");
        assertThat(((StringMatchCriterion) andCrits.get(1)).getType()).isEqualTo(CONTAINS);

    }

}