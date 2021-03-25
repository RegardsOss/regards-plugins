package fr.cnes.regards.modules.catalog.stac.service.collection.dynamic.helpers;

import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.level.DynCollLevelDef;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel.*;
import io.vavr.collection.List;
import org.assertj.core.data.Offset;
import org.junit.Test;

import static fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel.DynCollSublevelType.DatetimeBased.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

public class DynCollLevelDefParserImplTest {


    private DynCollLevelDefParserImpl parser = new DynCollLevelDefParserImpl();

    @Test
    public void testStringPrefix_prefix2A9() {
        // GIVEN
        StacProperty prop = makeProperty(1, "PREFIX(2,A9)", StacPropertyType.STRING);
        // WHEN
        DynCollLevelDef levelDef = parser.parse(prop);
        // THEN
        assertThat(levelDef.getStacProperty()).isSameAs(prop);
        List<DynCollSublevelDef> sublevels = levelDef.getSublevels();
        assertThat(sublevels).hasSize(2);

        assertThat(sublevels.get(0)).isInstanceOf(StringPrefixSublevelDef.class);
        StringPrefixSublevelDef sublevel1 = (StringPrefixSublevelDef) sublevels.get(0);
        assertThat(sublevel1.getPosition()).isEqualTo(1);
        assertThat(sublevel1.isAlpha()).isTrue();
        assertThat(sublevel1.isDigits()).isTrue();

        assertThat(sublevels.get(1)).isInstanceOf(StringPrefixSublevelDef.class);
        StringPrefixSublevelDef sublevel2 = (StringPrefixSublevelDef) sublevels.get(1);
        assertThat(sublevel2.getPosition()).isEqualTo(2);
        assertThat(sublevel2.isAlpha()).isTrue();
        assertThat(sublevel2.isDigits()).isTrue();
    }


    @Test
    public void testStringPrefix_prefix39() {
        // GIVEN
        StacProperty prop = makeProperty(1, "PREFIX(3,9)", StacPropertyType.STRING);
        // WHEN
        DynCollLevelDef levelDef = parser.parse(prop);
        // THEN
        assertThat(levelDef.getStacProperty()).isSameAs(prop);
        List<DynCollSublevelDef> sublevels = levelDef.getSublevels();
        assertThat(sublevels).hasSize(3);

        assertThat(sublevels.get(0)).isInstanceOf(StringPrefixSublevelDef.class);
        StringPrefixSublevelDef sublevel1 = (StringPrefixSublevelDef) sublevels.get(0);
        assertThat(sublevel1.getPosition()).isEqualTo(1);
        assertThat(sublevel1.isAlpha()).isFalse();
        assertThat(sublevel1.isDigits()).isTrue();

        assertThat(sublevels.get(1)).isInstanceOf(StringPrefixSublevelDef.class);
        StringPrefixSublevelDef sublevel2 = (StringPrefixSublevelDef) sublevels.get(1);
        assertThat(sublevel2.getPosition()).isEqualTo(2);
        assertThat(sublevel2.isAlpha()).isFalse();
        assertThat(sublevel2.isDigits()).isTrue();

        assertThat(sublevels.get(2)).isInstanceOf(StringPrefixSublevelDef.class);
        StringPrefixSublevelDef sublevel3 = (StringPrefixSublevelDef) sublevels.get(2);
        assertThat(sublevel3.getPosition()).isEqualTo(3);
        assertThat(sublevel3.isAlpha()).isFalse();
        assertThat(sublevel3.isDigits()).isTrue();
    }

    @Test
    public void testDate() {
        // GIVEN
        StacProperty prop = makeProperty(1, "HOUR", StacPropertyType.DATETIME);
        // WHEN
        DynCollLevelDef levelDef = parser.parse(prop);
        // THEN
        assertThat(levelDef.getStacProperty()).isSameAs(prop);
        List<DynCollSublevelDef> sublevels = levelDef.getSublevels();
        assertThat(sublevels).hasSize(4);

        assertThat(sublevels.filter(t -> t instanceof DatePartSublevelDef))
                .isEqualTo(sublevels);
        assertThat(sublevels.map(DatePartSublevelDef.class::cast).map(s -> s.getType()))
                .contains(YEAR, MONTH, DAY, HOUR);

    }

    @Test
    public void testRange() {
        // GIVEN
        StacProperty prop = makeProperty(1, "-0.0;0.1;1.0", StacPropertyType.PERCENTAGE);
        // WHEN
        DynCollLevelDef levelDef = parser.parse(prop);
        // THEN
        assertThat(levelDef.getStacProperty()).isSameAs(prop);
        List<DynCollSublevelDef> sublevels = levelDef.getSublevels();
        assertThat(sublevels).hasSize(1);
        DynCollSublevelDef sl = sublevels.get(0);
        assertThat(sl).isInstanceOf(NumberRangeSublevelDef.class);
        NumberRangeSublevelDef slr = (NumberRangeSublevelDef) sl;
        Offset<Double> offset = offset(0.0001);
        assertThat(slr.getMin()).isEqualTo(0, offset);
        assertThat(slr.getStep()).isEqualTo(0.1, offset);
        assertThat(slr.getMax()).isEqualTo(1, offset);
    }

    @Test
    public void testRangeIntegers() {
        // GIVEN
        StacProperty prop = makeProperty(1, "5;10;95", StacPropertyType.PERCENTAGE);
        // WHEN
        DynCollLevelDef levelDef = parser.parse(prop);
        // THEN
        assertThat(levelDef.getStacProperty()).isSameAs(prop);
        List<DynCollSublevelDef> sublevels = levelDef.getSublevels();
        assertThat(sublevels).hasSize(1);
        DynCollSublevelDef sl = sublevels.get(0);
        assertThat(sl).isInstanceOf(NumberRangeSublevelDef.class);
        NumberRangeSublevelDef slr = (NumberRangeSublevelDef) sl;
        Offset<Double> offset = offset(0.0001);
        assertThat(slr.getMin()).isEqualTo(5, offset);
        assertThat(slr.getStep()).isEqualTo(10, offset);
        assertThat(slr.getMax()).isEqualTo(95, offset);
    }

    private StacProperty makeProperty(int level, String format, StacPropertyType type) {
        return new StacProperty(
                null, "theName", "core", false,
                level, format, type,
                null);
    }

}