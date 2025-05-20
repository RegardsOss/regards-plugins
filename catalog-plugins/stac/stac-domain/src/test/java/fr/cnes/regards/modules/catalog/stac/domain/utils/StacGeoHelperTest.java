package fr.cnes.regards.modules.catalog.stac.domain.utils;

import fr.cnes.regards.framework.geojson.geometry.IGeometry;
import fr.cnes.regards.framework.geojson.geometry.Polygon;
import fr.cnes.regards.modules.catalog.stac.domain.spec.geo.BBox;
import fr.cnes.regards.modules.catalog.stac.testutils.gson.GsonAwareTest;
import io.vavr.Tuple2;
import io.vavr.control.Option;
import org.junit.Test;
import org.locationtech.spatial4j.io.GeoJSONReader;

import static org.assertj.core.api.Assertions.assertThat;

public class    StacGeoHelperTest implements GsonAwareTest {

    StacGeoHelper helper = new StacGeoHelper(gson());

    GeoJSONReader reader = helper.makeGeoJSONReader(helper.updateFactory(true));

    @Test
    public void testComputeBBoxUnlocated() {
        Option<Tuple2<IGeometry, BBox>> result = helper.computeBBox(IGeometry.unlocated(), reader);
        assertThat(result).isEmpty();
    }

    @Test
    public void testComputeBBoxPolygon() {
        Polygon polygon = IGeometry.simplePolygon(0d, 0d, 0d, 3d, 3d, 0d);
        Option<Tuple2<IGeometry, BBox>> result = helper.computeBBox(polygon, reader);
        System.out.println(result);
        assertThat(result).isNotEmpty();
        assertThat(result.get()._1).isSameAs(polygon);
        assertThat(result.get()._2).isEqualTo(new BBox(0d, 0d, 3d, 3d));
    }

    @Test
    public void testComputeBBoxPolygonAntemeridian() {
        Polygon polygon = IGeometry.simplePolygon(178d, 1d, -178d, 1d, -178d, -1d, 178d, -1d);
        Option<Tuple2<IGeometry, BBox>> result = helper.computeBBox(polygon, reader);
        System.out.println(result);
        assertThat(result).isNotEmpty();
        assertThat(result.get()._1).isSameAs(polygon);
        assertThat(result.get()._2).isEqualTo(new BBox(178d, -1d, -178d, 1d));
    }

    @Test
    public void testComputeBBoxPolygonAntemeridianCcw() {
        Polygon polygon = IGeometry.simplePolygon(178d, -1d, -178d, -1d, -178d, 1d, 178d, 1d);
        Option<Tuple2<IGeometry, BBox>> result = helper.computeBBox(polygon, reader);
        System.out.println(result);
        assertThat(result).isNotEmpty();
        assertThat(result.get()._1).isSameAs(polygon);
        assertThat(result.get()._2).isEqualTo(new BBox(178d, -1d, -178d, 1d));
    }

}