package fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.level.DatePartsLevelDef;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.level.NumberRangeLevelDef;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.level.StringPrefixLevelDef;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel.DynCollSublevelType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel.NumberRangeSublevelDef;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel.StringPrefixSublevelDef;
import io.vavr.collection.List;

public class DynCollValTest {

    StacProperty prop1 = new StacProperty(null, null, "prop1", "", false, 1, "0;10;100", StacPropertyType.NUMBER, null, Boolean.FALSE);

    StacProperty prop2 = new StacProperty(null, null, "prop2", "", false, 2, "DAY", StacPropertyType.DATETIME, null, Boolean.FALSE);

    StacProperty prop3 = new StacProperty(null, null, "prop3", "", false, 3, "PREFIX(2,9)", StacPropertyType.STRING, null, Boolean.FALSE);

    NumberRangeLevelDef numberRangeLevelDef = new NumberRangeLevelDef(prop1, new NumberRangeSublevelDef(0, 10, 100));

    DatePartsLevelDef datePartsLevelDef = new DatePartsLevelDef(prop2, DynCollSublevelType.DatetimeBased.DAY);

    StringPrefixLevelDef stringPrefixLevelDef = new StringPrefixLevelDef(prop3,
            List.of(new StringPrefixSublevelDef(0, false, true), new StringPrefixSublevelDef(1, false, true)));

    DynCollDef def = new DynCollDef(List.of(numberRangeLevelDef, datePartsLevelDef, stringPrefixLevelDef));

    @Test
    public void firstMissingValue() {
        assertThat(new DynCollVal(def, List.empty()).firstMissingValue()).contains(numberRangeLevelDef);

        assertThat(new DynCollVal(def, List.of(numberRangeLevelDef.parseValues("20;30"))).firstMissingValue())
                .contains(datePartsLevelDef);

        assertThat(new DynCollVal(def,
                List.of(numberRangeLevelDef.parseValues("20;30"), datePartsLevelDef.parseValues("1999-01")))
                        .firstMissingValue()).contains(stringPrefixLevelDef);

        assertThat(new DynCollVal(def,
                List.of(numberRangeLevelDef.parseValues("20;30"), datePartsLevelDef.parseValues("1999-01-01")))
                        .firstMissingValue()).contains(stringPrefixLevelDef);

        assertThat(new DynCollVal(def,
                List.of(numberRangeLevelDef.parseValues("20;30"), datePartsLevelDef.parseValues("1999-01-01"),
                        stringPrefixLevelDef.parseValues("0"))).firstMissingValue()).isEmpty();

        assertThat(new DynCollVal(def,
                List.of(numberRangeLevelDef.parseValues("20;30"), datePartsLevelDef.parseValues("1999-01-01"),
                        stringPrefixLevelDef.parseValues("00"))).firstMissingValue()).isEmpty();
    }

    @Test
    public void firstPartiallyValued() {
        assertThat(new DynCollVal(def, List.empty()).firstPartiallyValued()).isEmpty();

        assertThat(new DynCollVal(def, List.of(numberRangeLevelDef.parseValues("20;30"))).firstPartiallyValued()).isEmpty();

        assertThat(new DynCollVal(def,
                List.of(numberRangeLevelDef.parseValues("20;30"), datePartsLevelDef.parseValues("1999-01")))
                        .firstPartiallyValued()).contains(datePartsLevelDef.parseValues("1999-01"));

        assertThat(new DynCollVal(def,
                List.of(numberRangeLevelDef.parseValues("20;30"), datePartsLevelDef.parseValues("1999-01-01")))
                        .firstPartiallyValued()).isEmpty();

        assertThat(new DynCollVal(def,
                List.of(numberRangeLevelDef.parseValues("20;30"), datePartsLevelDef.parseValues("1999-01-01"),
                        stringPrefixLevelDef.parseValues("0"))).firstPartiallyValued())
                                .contains(stringPrefixLevelDef.parseValues("0"));

        assertThat(new DynCollVal(def,
                List.of(numberRangeLevelDef.parseValues("20;30"), datePartsLevelDef.parseValues("1999-01-01"),
                        stringPrefixLevelDef.parseValues("00"))).firstPartiallyValued()).isEmpty();
    }

    @Test
    public void isFullyValued() {
        assertThat(new DynCollVal(def, List.empty()).isFullyValued()).isFalse();

        assertThat(new DynCollVal(def, List.of(numberRangeLevelDef.parseValues("20;30"))).isFullyValued()).isFalse();

        assertThat(new DynCollVal(def,
                List.of(numberRangeLevelDef.parseValues("20;30"), datePartsLevelDef.parseValues("1999"))).isFullyValued())
                        .isFalse();

        assertThat(new DynCollVal(def,
                List.of(numberRangeLevelDef.parseValues("20;30"), datePartsLevelDef.parseValues("1999-01"))).isFullyValued())
                        .isFalse();

        assertThat(new DynCollVal(def,
                List.of(numberRangeLevelDef.parseValues("20;30"), datePartsLevelDef.parseValues("1999-01-01")))
                        .isFullyValued()).isFalse();

        assertThat(new DynCollVal(def,
                List.of(numberRangeLevelDef.parseValues("20;30"), datePartsLevelDef.parseValues("1999-01-01"),
                        stringPrefixLevelDef.parseValues("0"))).isFullyValued()).isFalse();

        assertThat(new DynCollVal(def,
                List.of(numberRangeLevelDef.parseValues("20;30"), datePartsLevelDef.parseValues("1999-01-01"),
                        stringPrefixLevelDef.parseValues("00"))).isFullyValued()).isTrue();
    }

    @Test
    public void getLowestLevelLabel() {

        assertThat(new DynCollVal(def,
                List.of(numberRangeLevelDef.parseValues("20;30"), datePartsLevelDef.parseValues("1999-10")))
                        .getLowestLevelLabel()).isEqualTo("prop2=1999-10");

    }

    @Test
    public void parentValue() {
        assertThat(new DynCollVal(def, List.of(numberRangeLevelDef.parseValues("20;30"))).parentValue()).isEmpty();

        assertThat(new DynCollVal(def,
                List.of(numberRangeLevelDef.parseValues("20;30"), datePartsLevelDef.parseValues("1999"))).parentValue())
                        .contains(new DynCollVal(def, List.of(numberRangeLevelDef.parseValues("20;30"))));

        assertThat(new DynCollVal(def,
                List.of(numberRangeLevelDef.parseValues("20;30"), datePartsLevelDef.parseValues("1999-10"))).parentValue())
                        .contains(new DynCollVal(def,
                                List.of(numberRangeLevelDef.parseValues("20;30"), datePartsLevelDef.parseValues("1999"))));

        assertThat(new DynCollVal(def,
                List.of(numberRangeLevelDef.parseValues("20;30"), datePartsLevelDef.parseValues("1999-01-01"),
                        stringPrefixLevelDef.parseValues("0"))).parentValue())
                                .contains(new DynCollVal(def, List.of(numberRangeLevelDef.parseValues("20;30"),
                                                                      datePartsLevelDef.parseValues("1999-01-01"))));

    }
}