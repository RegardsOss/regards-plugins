package fr.cnes.regards.modules.catalog.stac.service.collection.dyncoll.helpers;

import static fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType.DATETIME;
import static fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType.NUMBER;
import static fr.cnes.regards.modules.catalog.stac.domain.properties.StacPropertyType.STRING;
import static java.time.OffsetDateTime.now;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Collection;

import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.metrics.stats.ParsedStats;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.modules.catalog.stac.domain.properties.StacProperty;
import fr.cnes.regards.modules.catalog.stac.domain.properties.conversion.IdentityPropertyConverter;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.DynCollDef;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.DynCollVal;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.level.DatePartsLevelDef;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.level.NumberRangeLevelDef;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.level.StringPrefixLevelDef;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel.DynCollSublevelType;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel.NumberRangeSublevelDef;
import fr.cnes.regards.modules.catalog.stac.domain.properties.dyncoll.sublevel.StringPrefixSublevelDef;
import fr.cnes.regards.modules.catalog.stac.service.collection.EsAggregationHelper;
import fr.cnes.regards.modules.catalog.stac.service.collection.EsAggregationHelperImpl;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessor;
import fr.cnes.regards.modules.catalog.stac.service.configuration.ConfigurationAccessorFactory;
import fr.cnes.regards.modules.catalog.stac.service.criterion.RegardsPropertyAccessorAwareTest;
import fr.cnes.regards.modules.catalog.stac.service.criterion.StacSearchCriterionBuilder;
import fr.cnes.regards.modules.indexer.dao.IEsRepository;
import fr.cnes.regards.modules.indexer.dao.spatial.ProjectGeoSettings;
import fr.cnes.regards.modules.indexer.domain.spatial.Crs;
import io.vavr.collection.List;

public class DynCollValNextSublevelHelperImplTest implements RegardsPropertyAccessorAwareTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynCollValNextSublevelHelperImplTest.class);

    // GIVEN

    DynCollLevelValToQueryObjectConverter levelValToQueryObjectConverter = new DynCollLevelValToQueryObjectConverterImpl();

    StacSearchCriterionBuilder criterionBuilder = mock(StacSearchCriterionBuilder.class);

    IEsRepository esRepository = mock(IEsRepository.class);

    IRuntimeTenantResolver tenantResolver = mock(IRuntimeTenantResolver.class);

    ProjectGeoSettings projectGeoSettings = mock(ProjectGeoSettings.class);

    EsAggregationHelper aggregagtionHelper = new EsAggregationHelperImpl(esRepository, tenantResolver, projectGeoSettings);

    ConfigurationAccessor config = mock(ConfigurationAccessor.class);

    ConfigurationAccessorFactory configFactory = () -> config;

    DynCollValNextSublevelHelperImpl helper = new DynCollValNextSublevelHelperImpl(levelValToQueryObjectConverter,
            criterionBuilder, aggregagtionHelper, configFactory);

    StacProperty prop1 = new StacProperty(accessor("prop1", NUMBER, 12d), null, "prop1", "", false, 1, "0;10;20", NUMBER,
            new IdentityPropertyConverter<>(NUMBER), Boolean.FALSE);

    StacProperty prop2 = new StacProperty(accessor("prop2", DATETIME, now()), null, "prop2", "", false, 2, "DAY", DATETIME,
            new IdentityPropertyConverter<>(DATETIME), Boolean.FALSE);

    StacProperty prop3 = new StacProperty(accessor("prop3", STRING, ""), null, "prop3", "", false, 3, "PREFIX(2,9)", STRING,
            new IdentityPropertyConverter<>(STRING), Boolean.FALSE);

    NumberRangeLevelDef numberRangeLevelDef = new NumberRangeLevelDef(prop1, new NumberRangeSublevelDef(0, 10, 20));

    DatePartsLevelDef datePartsLevelDef = new DatePartsLevelDef(prop2, DynCollSublevelType.DatetimeBased.DAY);

    StringPrefixLevelDef stringPrefixLevelDef = new StringPrefixLevelDef(prop3,
            List.of(new StringPrefixSublevelDef(0, false, true), new StringPrefixSublevelDef(1, false, true)));

    DynCollDef def = new DynCollDef(List.of(numberRangeLevelDef, datePartsLevelDef, stringPrefixLevelDef));

    @Before
    public void init() {
        when(config.getStacProperties()).thenReturn(List.of(prop1, prop2, prop3));
        when(tenantResolver.getTenant()).thenReturn("theTenant");
        when(projectGeoSettings.getCrs()).thenReturn(Crs.WGS_84);
        when(esRepository.minDate(any(), any(), anyString()))
                .thenAnswer(i -> OffsetDateTime.of(2020, 1, 1, 0, 0, 0, 0, UTC));
        when(esRepository.maxDate(any(), any(), anyString()))
                .thenAnswer(i -> OffsetDateTime.of(2021, 1, 1, 0, 0, 0, 0, UTC));
    }

    @Test
    public void nextSublevels_empty() {
        // WHEN
        List<DynCollVal> actual = helper.nextSublevels(new DynCollVal(def, List.empty()));
        // THEN
        assertThat(actual).hasSize(4);
        assertThat(actual.get(0).getLevels().get(0).getSublevels().get(0).getSublevelValue()).isEqualTo("<0.0");
        assertThat(actual.get(1).getLevels().get(0).getSublevels().get(0).getSublevelValue()).isEqualTo("0.0;10.0");
        assertThat(actual.get(2).getLevels().get(0).getSublevels().get(0).getSublevelValue()).isEqualTo("10.0;20.0");
        assertThat(actual.get(3).getLevels().get(0).getSublevels().get(0).getSublevelValue()).isEqualTo(">20.0");
    }

    @Test
    public void nextSublevels_hasNum() {
        // GIVEN
        class MyDateStats extends ParsedStats {

            public MyDateStats(final String name, final double min, final double max) {
                setName(name);
                this.min = min;
                this.max = max;
            }
        }

        when(esRepository.getAggregationsFor(any(), any(), any())).thenAnswer(i -> {
            Collection<AggregationBuilder> aggBuilders = i.getArgument(2);
            String path = List.ofAll(aggBuilders).head().getName();
            ParsedStats result = new MyDateStats(path, OffsetDateTime.of(2020, 1, 2, 0, 0, 0, 0, UTC).toEpochSecond() * 1000,
                    OffsetDateTime.of(2021, 12, 31, 0, 0, 0, 0, UTC).toEpochSecond() * 1000);
            return new Aggregations(List.of(result).toJavaList());
        });

        // WHEN
        List<DynCollVal> actual = helper
                .nextSublevels(new DynCollVal(def, List.of(numberRangeLevelDef.parseValues("20;30"))));
        // THEN
        actual.map(v -> v.toLabel()).forEach(LOGGER::info);
        assertThat(actual).hasSize(2);
        assertThat(actual.get(0).getLevels().get(1).getSublevels().get(0).getSublevelValue()).isEqualTo("2020");
        assertThat(actual.get(1).getLevels().get(1).getSublevels().get(0).getSublevelValue()).isEqualTo("2021");
    }

    @Test
    public void nextSublevels_hasNumYear() {
        // WHEN
        List<DynCollVal> actual = helper.nextSublevels(new DynCollVal(def,
                List.of(numberRangeLevelDef.parseValues("20;30"), datePartsLevelDef.parseValues("2020"))));
        // THEN
        assertThat(actual).hasSize(12);
        assertThat(actual.get(0).getLevels().get(1).getSublevels().get(1).getSublevelValue()).isEqualTo("2020-01");
        // ...
        assertThat(actual.get(11).getLevels().get(1).getSublevels().get(1).getSublevelValue()).isEqualTo("2020-12");
    }

    @Test
    public void nextSublevels_hasNumYearMonth() {
        // WHEN
        List<DynCollVal> actual = helper.nextSublevels(new DynCollVal(def,
                List.of(numberRangeLevelDef.parseValues("20;30"), datePartsLevelDef.parseValues("2020-02"))));
        // THEN
        assertThat(actual).hasSize(28);
        assertThat(actual.get(0).getLevels().get(1).getSublevels().get(2).getSublevelValue()).isEqualTo("2020-02-01");
        // ...
        assertThat(actual.get(27).getLevels().get(1).getSublevels().get(2).getSublevelValue()).isEqualTo("2020-02-28");
    }

    @Test
    public void nextSublevels_hasNumYearMonthDay() {
        // WHEN
        List<DynCollVal> actual = helper.nextSublevels(new DynCollVal(def,
                List.of(numberRangeLevelDef.parseValues("20;30"), datePartsLevelDef.parseValues("2020-02-16"))));
        // THEN
        assertThat(actual).hasSize(10);
        assertThat(actual.get(0).getLevels().get(2).getSublevels().get(0).getSublevelValue()).isEqualTo("0");
        // ...
        assertThat(actual.get(9).getLevels().get(2).getSublevels().get(0).getSublevelValue()).isEqualTo("9");
    }

    @Test
    public void nextSublevels_hasNumYearMonthDayFst() {
        // WHEN
        List<DynCollVal> actual = helper.nextSublevels(new DynCollVal(def,
                List.of(numberRangeLevelDef.parseValues("20;30"), datePartsLevelDef.parseValues("2020-02-16"),
                        stringPrefixLevelDef.parseValues("7"))));
        // THEN
        assertThat(actual).hasSize(10);
        assertThat(actual.get(0).getLevels().get(2).getSublevels().get(1).getSublevelValue()).isEqualTo("70");
        // ...
        assertThat(actual.get(9).getLevels().get(2).getSublevels().get(1).getSublevelValue()).isEqualTo("79");
    }

    @Test
    public void nextSublevels_full() {
        // WHEN
        List<DynCollVal> actual = helper.nextSublevels(new DynCollVal(def,
                List.of(numberRangeLevelDef.parseValues("20;30"), datePartsLevelDef.parseValues("2020-02-16"),
                        stringPrefixLevelDef.parseValues("77"))));
        // THEN
        assertThat(actual).isEmpty();
    }

}