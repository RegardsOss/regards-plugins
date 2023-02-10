package fr.cnes.regards.modules.catalog.stac.service.criterion;

import fr.cnes.regards.framework.geojson.coordinates.PolygonPositions;
import fr.cnes.regards.framework.geojson.geometry.IGeometry;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.indexer.domain.criterion.OrCriterion;
import fr.cnes.regards.modules.indexer.domain.criterion.PolygonCriterion;
import io.vavr.collection.List;
import io.vavr.control.Option;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GeometryCriterionBuilderTest {

    @Test
    public void testBuildCriterionEmpty() {
        // GIVEN
        List<StacProperty> properties = List.of();
        // WHEN
        Option<ICriterion> criterion = new GeometryCriterionBuilder().buildCriterion(properties, null);
        // THEN
        assertThat(criterion).isEmpty();
    }

    @Test
    public void testBuildCriterionPolygon() {
        // GIVEN
        List<StacProperty> properties = List.of();
        IGeometry geom = IGeometry.simplePolygon(1, 2, 3, 4);
        // WHEN
        Option<ICriterion> criterion = new GeometryCriterionBuilder().buildCriterion(properties, geom);
        // THEN
        assertThat(criterion).isNotEmpty();
        assertThat(criterion.get()).isInstanceOf(PolygonCriterion.class);
        assertThat(((PolygonCriterion) criterion.get()).getCoordinates()).isEqualTo(new double[][][] { new double[][] {
            new double[] { 1, 2 },
            new double[] { 3, 4 },
            new double[] { 1, 2 } } });
    }

    @Test
    public void testBuildCriterionMultipolygon() {
        // GIVEN
        List<StacProperty> properties = List.of();
        double[][][] fstPoly = { new double[][] { new double[] { 1, 2 },
                                                  new double[] { 3, 4 },
                                                  new double[] { 1, 2 } } };
        double[][][] sndPoly = { new double[][] { new double[] { 1, 2 },
                                                  new double[] { 3, 4 },
                                                  new double[] { 1, 2 } } };
        IGeometry geom = IGeometry.multiPolygon(PolygonPositions.fromArray(fstPoly),
                                                PolygonPositions.fromArray(sndPoly));

        // WHEN
        Option<ICriterion> criterion = new GeometryCriterionBuilder().buildCriterion(properties, geom);
        // THEN
        assertThat(criterion).isNotEmpty();
        assertThat(criterion.get()).isInstanceOf(OrCriterion.class);
        OrCriterion orCrit = (OrCriterion) (criterion.get());
        java.util.List<ICriterion> polyCrits = orCrit.getCriterions();
        assertThat(polyCrits).hasSize(2);
        ICriterion firstPolyCrit = polyCrits.get(0);
        assertThat(firstPolyCrit).isInstanceOf(PolygonCriterion.class);
        assertThat(((PolygonCriterion) firstPolyCrit).getCoordinates()).isEqualTo(fstPoly);
        ICriterion secondPolyCrit = polyCrits.get(1);
        assertThat(secondPolyCrit).isInstanceOf(PolygonCriterion.class);
        assertThat(((PolygonCriterion) secondPolyCrit).getCoordinates()).isEqualTo(sndPoly);
    }

}