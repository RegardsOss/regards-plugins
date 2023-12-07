/*
 * Copyright 2017-2022 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
 *
 * This file is part of REGARDS.
 *
 * REGARDS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * REGARDS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with REGARDS. If not, see <http://www.gnu.org/licenses/>.
 */
package fr.cnes.regards.modules.dam.plugins.datasources;

import com.google.common.collect.Lists;
import fr.cnes.regards.framework.gson.adapters.OffsetDateTimeAdapter;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.framework.modules.plugins.domain.parameter.IPluginParam;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.oais.urn.OAISIdentifier;
import fr.cnes.regards.framework.oais.urn.OaisUniformResourceName;
import fr.cnes.regards.framework.test.integration.AbstractRegardsServiceIT;
import fr.cnes.regards.framework.urn.DataType;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.framework.utils.plugins.PluginParameterTransformer;
import fr.cnes.regards.framework.utils.plugins.PluginUtils;
import fr.cnes.regards.framework.utils.plugins.exception.NotAvailablePluginConfigurationException;
import fr.cnes.regards.modules.dam.domain.datasources.CrawlingCursor;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourceException;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourcePluginConstants;
import fr.cnes.regards.modules.dam.domain.entities.feature.DataObjectFeature;
import fr.cnes.regards.modules.ingest.dto.aip.AIP;
import fr.cnes.regards.modules.model.domain.Model;
import fr.cnes.regards.modules.model.dto.properties.LongProperty;
import fr.cnes.regards.modules.model.dto.properties.PropertyType;
import fr.cnes.regards.modules.model.service.IModelService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * {@link AipDataSourcePlugin} test class
 *
 * @author Simon Milhau
 */
@TestPropertySource("classpath:aip-datasource-test.properties")
@ContextConfiguration(classes = { AipDataSourcePluginTestConfiguration.class })
public class AipDataSourcePluginTest extends AbstractRegardsServiceIT {

    private static final String MODEL_FILE_NAME = "model.xml";

    private static final String MODEL_NAME = "model_1";

    private AipDataSourcePlugin aipDataSourcePlugin;

    @Autowired
    private IModelService modelService;

    @Autowired
    private IRuntimeTenantResolver tenantResolver;

    @Before
    public void setUp() throws ModuleException, NotAvailablePluginConfigurationException {
        PluginUtils.setup();
        tenantResolver.forceTenant(getDefaultTenant());
        try {
            // Remove the model if existing
            modelService.getModelByName(MODEL_NAME);
            modelService.deleteModel(MODEL_NAME);
        } catch (ModuleException e) {
            // There is nothing to do - we create the model later
        }
        importModel(MODEL_FILE_NAME);

        ConcurrentMap<String, Object> pluginCacheMap = new ConcurrentHashMap<>();

        // Instantiate the data source plugin
        Set<IPluginParam> parameters = IPluginParam.set(IPluginParam.build(DataSourcePluginConstants.BINDING_MAP,
                                                                           PluginParameterTransformer.toJson(
                                                                               createBindingMap())),
                                                        IPluginParam.build(DataSourcePluginConstants.SUBSETTING_TAGS,
                                                                           PluginParameterTransformer.toJson(List.of(
                                                                               MODEL_NAME))),
                                                        IPluginParam.build(DataSourcePluginConstants.MODEL_NAME_PARAM,
                                                                           MODEL_NAME),
                                                        IPluginParam.build(DataSourcePluginConstants.REFRESH_RATE,
                                                                           1800),
                                                        IPluginParam.build(DataSourcePluginConstants.TAGS,
                                                                           PluginParameterTransformer.toJson(Lists.newArrayList(
                                                                               "TOTO",
                                                                               "TITI"))),
                                                        IPluginParam.build(DataSourcePluginConstants.MODEL_ATTR_FILE_SIZE,
                                                                           "ALTITUDE.SIZE"));

        aipDataSourcePlugin = PluginUtils.getPlugin(PluginConfiguration.build(AipDataSourcePlugin.class,
                                                                              null,
                                                                              parameters), pluginCacheMap);

    }

    /**
     * Import model definition file from resources directory
     *
     * @param filename filename
     * @throws ModuleException if error occurs
     */
    private void importModel(final String filename) throws ModuleException {
        try {
            final InputStream input = Files.newInputStream(Paths.get("src", "test", "resources", filename));
            modelService.importModel(input);
        } catch (final IOException e) {
            final String errorMessage = "Cannot import " + filename;
            throw new AssertionError(errorMessage);
        }
    }

    protected static List<AIP> createAIPs(int count, String... tags) {
        List<AIP> aips = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            OaisUniformResourceName id = new OaisUniformResourceName(OAISIdentifier.AIP,
                                                                     EntityType.DATA,
                                                                     "TENANT",
                                                                     UUID.randomUUID(),
                                                                     1,
                                                                     null,
                                                                     null);
            AIP aip = AIP.build(EntityType.DATA, id, Optional.empty(), "sipId" + i, 1);
            aip.withContextTags(tags);

            aip.withDescriptiveInformation("label", "libellé du data object " + i);
            aip.withDescriptiveInformation("START_DATE", OffsetDateTime.now());
            aip.withDescriptiveInformation("ALT_MAX", 1500 + i);
            aip.withDescriptiveInformation("HISTORY", new String[] { "H1", "H2", "H3" });
            aip.withDescriptiveInformation("HISTORY_SET", new HashSet<>(Arrays.asList("aaaa", "bb", "ccccc", "dddd")));
            aip.withDescriptiveInformation("HISTORY_LIST",
                                           new ArrayList<>(Arrays.asList("paris",
                                                                         "toulouse",
                                                                         "lyon",
                                                                         "nice",
                                                                         "bordeaux")));
            aip.withDescriptiveInformation("POUET", "POUET");
            aip.withDescriptiveInformation("LINKS", "https://sipad-serad.cnes.fr");
            Map<String, String> dateBounds = new HashMap<>();
            dateBounds.put(DataSourcePluginConstants.LOWER_BOUND,
                           OffsetDateTime.now()
                                         .atZoneSameInstant(ZoneOffset.UTC)
                                         .format(OffsetDateTimeAdapter.ISO_DATE_TIME_UTC));
            dateBounds.put(DataSourcePluginConstants.UPPER_BOUND,
                           OffsetDateTime.now()
                                         .atZoneSameInstant(ZoneOffset.UTC)
                                         .format(OffsetDateTimeAdapter.ISO_DATE_TIME_UTC));
            aip.withDescriptiveInformation("range", dateBounds);

            Map<String, Integer> intBounds = new HashMap<>();
            intBounds.put("ilow", 100);
            intBounds.put("iup", null);
            aip.withDescriptiveInformation("intrange", intBounds);
            aips.add(aip);
        }
        return aips;
    }

    /**
     * Binding map from AIP (key) properties and associated model attributes (value)
     */
    private Map<String, String> createBindingMap() {
        HashMap<String, String> map = new HashMap<>();
        map.put("label", "properties.descriptiveInformation.label");
        map.put("properties.START_DATE", "properties.descriptiveInformation.START_DATE");
        map.put("properties.ALTITUDE.MAX", "properties.descriptiveInformation.ALT_MAX");
        map.put("properties.ALTITUDE.MIN", "properties.descriptiveInformation.ALT_MIN");
        map.put("properties.history", "properties.descriptiveInformation.HISTORY");
        map.put("properties.history_set", "properties.descriptiveInformation.HISTORY_SET");
        map.put("properties.history_list", "properties.descriptiveInformation.HISTORY_LIST");
        map.put("properties.LINKS", "properties.descriptiveInformation.LINKS");

        // Date interval
        map.put("properties.DATE_INTERVAL" + DataSourcePluginConstants.LOWER_BOUND_SUFFIX,
                "properties.descriptiveInformation.range" + DataSourcePluginConstants.LOWER_BOUND_SUFFIX);
        map.put("properties.DATE_INTERVAL" + DataSourcePluginConstants.UPPER_BOUND_SUFFIX,
                "properties.descriptiveInformation.range" + DataSourcePluginConstants.UPPER_BOUND_SUFFIX);

        // Integer open interval
        map.put("properties.INT_INTERVAL" + DataSourcePluginConstants.LOWER_BOUND_SUFFIX,
                "properties.descriptiveInformation.intrange.ilow");
        map.put("properties.INT_INTERVAL" + DataSourcePluginConstants.UPPER_BOUND_SUFFIX,
                "properties.descriptiveInformation.intrange.iup");
        return map;
    }

    @Test
    public void test() throws DataSourceException {
        // Given
        String tenant = getDefaultTenant();
        CrawlingCursor crawlingCursor = new CrawlingCursor(0, 10);

        // When
        List<DataObjectFeature> dataObjects = aipDataSourcePlugin.findAll(tenant,
                                                                          crawlingCursor,
                                                                          null,
                                                                          OffsetDateTime.now());

        // Then
        Assert.assertNotNull(dataObjects);
        Assert.assertEquals(1, dataObjects.size());

        DataObjectFeature feature = dataObjects.get(0);
        Assert.assertEquals("libellé du data object 0", feature.getLabel());
        Assert.assertNotNull(feature.getProperty("START_DATE"));
        Assert.assertNotNull(feature.getProperty("LINKS"));
        Assert.assertNotNull(feature.getProperty("ALTITUDE.MAX"));
        Assert.assertNull(feature.getProperty("ALTITUDE.MIN"));
        Assert.assertNotNull(feature.getTags());
        Assert.assertTrue(feature.getTags().contains("tag1"));
        Assert.assertTrue(feature.getTags().contains("tag2"));
        Assert.assertEquals(PropertyType.STRING_ARRAY, feature.getProperty("history").getType());
        Assert.assertTrue(Arrays.binarySearch((Object[]) feature.getProperty("history").getValue(), "H1") > -1);
        Assert.assertTrue(Arrays.binarySearch((Object[]) feature.getProperty("history").getValue(), "H2") > -1);
        Assert.assertEquals(PropertyType.DATE_INTERVAL, feature.getProperty("DATE_INTERVAL").getType());
        Assert.assertEquals(PropertyType.INTEGER_INTERVAL, feature.getProperty("INT_INTERVAL").getType());
        Assert.assertNotNull(feature.getFiles());
        Assert.assertEquals(1, feature.getFiles().size());
        Assert.assertTrue(feature.getFiles().containsKey(DataType.RAWDATA));

        Assert.assertTrue(feature.getTags().contains("TOTO"));
        Assert.assertTrue(feature.getTags().contains("TITI"));

        Assert.assertNotNull(feature.getProperty("ALTITUDE.SIZE"));
        Assert.assertTrue(feature.getProperty("ALTITUDE.SIZE") instanceof LongProperty);

        Assert.assertTrue(feature.getFiles().get(DataType.RAWDATA).iterator().next().isOnline());
        Assert.assertFalse(feature.getFiles().get(DataType.RAWDATA).iterator().next().isReference());

        Assert.assertTrue(feature.getFiles().get(DataType.RAWDATA).iterator().next().getTypes().contains("type1"));
        Assert.assertTrue(feature.getFiles().get(DataType.RAWDATA).iterator().next().getTypes().contains("type2"));
    }

    @After
    public void tearDown() throws ModuleException {
        Model model = modelService.getModelByName(MODEL_NAME);
        if (model != null) {
            modelService.deleteModel(model.getName());
        }
    }
}
