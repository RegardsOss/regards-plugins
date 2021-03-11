package fr.cnes.regards.modules.catalog.stac.domain.utils;

import fr.cnes.regards.framework.geojson.geometry.IGeometry;
import fr.cnes.regards.framework.geojson.geometry.Polygon;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.geo.BBox;
import fr.cnes.regards.modules.catalog.stac.domain.spec.v1_0_0_beta2.geo.Centroid;
import fr.cnes.regards.modules.catalog.stac.testutils.gson.GsonAwareTest;
import io.vavr.Tuple3;
import io.vavr.control.Option;
import org.junit.Test;
import org.locationtech.spatial4j.io.GeoJSONReader;

import static org.assertj.core.api.Assertions.assertThat;

public class StacGeoHelperTest implements GsonAwareTest {

    StacGeoHelper helper = new StacGeoHelper(gson());
    GeoJSONReader reader = helper.makeGeoJSONReader(helper.updateFactory(true));

    @Test
    public void testComputeBBoxUnlocated() {
        Option<Tuple3<IGeometry, BBox, Centroid>> result = helper.computeBBoxCentroid(IGeometry.unlocated(), reader);
        assertThat(result).isEmpty();
    }

    @Test
    public void testComputeBBoxPolygon() {
        Polygon polygon = IGeometry.simplePolygon(0d, 0d, 0d, 3d, 3d, 0d);
        Option<Tuple3<IGeometry, BBox, Centroid>> result = helper.computeBBoxCentroid(polygon, reader);
        System.out.println(result);
        assertThat(result).isNotEmpty();
        assertThat(result.get()._1).isSameAs(polygon);
        assertThat(result.get()._2).isEqualTo(new BBox(0d,0d,3d,3d));
        assertThat(result.get()._3).isEqualTo(new Centroid(1d,1d));
    }

    @Test
    public void testComputeBBoxPolygonAntemeridian() {
        Polygon polygon = IGeometry.simplePolygon(178d, 1d, -178d, 1d, -178d, -1d, 178d, -1d);
        Option<Tuple3<IGeometry, BBox, Centroid>> result = helper.computeBBoxCentroid(polygon, reader);
        System.out.println(result);
        assertThat(result).isNotEmpty();
        assertThat(result.get()._1).isSameAs(polygon);
        assertThat(result.get()._2).isEqualTo(new BBox(178d,-1d,-178d,1d));
        assertThat(result.get()._3).isEqualTo(new Centroid(180d,0d));
    }

    @Test
    public void testComputeBBoxPolygonAntemeridianCcw() {
        Polygon polygon = IGeometry.simplePolygon(178d, -1d, -178d, -1d, -178d, 1d, 178d, 1d);
        Option<Tuple3<IGeometry, BBox, Centroid>> result = helper.computeBBoxCentroid(polygon, reader);
        System.out.println(result);
        assertThat(result).isNotEmpty();
        assertThat(result.get()._1).isSameAs(polygon);
        assertThat(result.get()._2).isEqualTo(new BBox(178d,-1d,-178d,1d));
        assertThat(result.get()._3).isEqualTo(new Centroid(180d,0d));
    }

}