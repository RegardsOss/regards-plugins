/*
 * Copyright 2017-2018 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import com.google.common.collect.Lists;

import fr.cnes.regards.framework.gson.adapters.OffsetDateTimeAdapter;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.framework.modules.plugins.domain.PluginMetaData;
import fr.cnes.regards.framework.modules.plugins.domain.PluginParameter;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.oais.urn.DataType;
import fr.cnes.regards.framework.oais.urn.EntityType;
import fr.cnes.regards.framework.oais.urn.OAISIdentifier;
import fr.cnes.regards.framework.oais.urn.UniformResourceName;
import fr.cnes.regards.framework.test.integration.AbstractRegardsServiceIT;
import fr.cnes.regards.framework.utils.plugins.PluginParametersFactory;
import fr.cnes.regards.framework.utils.plugins.PluginUtils;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourceException;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourcePluginConstants;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.IDataSourcePlugin;
import fr.cnes.regards.modules.dam.domain.entities.DataObject;
import fr.cnes.regards.modules.dam.domain.entities.attribute.DateIntervalAttribute;
import fr.cnes.regards.modules.dam.domain.entities.attribute.IntegerIntervalAttribute;
import fr.cnes.regards.modules.dam.domain.entities.attribute.LongAttribute;
import fr.cnes.regards.modules.dam.domain.entities.attribute.StringArrayAttribute;
import fr.cnes.regards.modules.dam.domain.models.Model;
import fr.cnes.regards.modules.dam.service.models.IModelService;
import fr.cnes.regards.modules.storage.domain.AIP;
import fr.cnes.regards.modules.storage.domain.AIPBuilder;

/**
 * @author oroussel
 */
@ContextConfiguration(classes = { AipDataSourceConfiguration.class })
@TestPropertySource("classpath:aip-datasource-test.properties")
// @Ignore("Fails sometimes on Jenkins i don't why and i am fed up with")
public class AipDataSourcePluginTest extends AbstractRegardsServiceIT {

    private static final String PLUGIN_CURRENT_PACKAGE = "fr.cnes.regards.modules.dam.plugins.datasources";

    private static final String MODEL_FILE_NAME = "model.xml";

    private static final String MODEL_NAME = "model_1";

    private AipDataSourcePlugin dsPlugin;

    protected static final String TENANT = DEFAULT_TENANT;

    @Autowired
    private IModelService modelService;

    @Autowired
    private IRuntimeTenantResolver tenantResolver;

    @Before
    public void setUp() throws SQLException, ModuleException {
        tenantResolver.forceTenant(getDefaultTenant());
        try {
            // Remove the model if existing
            modelService.getModelByName(MODEL_NAME);
            modelService.deleteModel(MODEL_NAME);
        } catch (ModuleException e) {
            // There is nothing to do - we create the model later
        }
        importModel(MODEL_FILE_NAME);

        Map<Long, Object> pluginCacheMap = new HashMap<>();

        // Instantiate the data source plugin
        List<PluginParameter> parameters;
        parameters = PluginParametersFactory.build()
                .addParameter(DataSourcePluginConstants.BINDING_MAP, createBindingMap())
                .addParameter(DataSourcePluginConstants.SUBSETTING_TAGS, Arrays.asList(MODEL_NAME))
                .addParameter(DataSourcePluginConstants.MODEL_NAME_PARAM, MODEL_NAME)
                .addParameter(DataSourcePluginConstants.REFRESH_RATE, 1800)
                .addParameter(DataSourcePluginConstants.TAGS, Lists.newArrayList("TOTO", "TITI"))
                .addParameter(DataSourcePluginConstants.MODEL_ATTR_FILE_SIZE, "SIZE").getParameters();

        PluginMetaData metadata = PluginUtils.createPluginMetaData(AipDataSourcePlugin.class,
                                                                   IDataSourcePlugin.class.getPackage().getName(),
                                                                   AipDataSourcePlugin.class.getPackage().getName());

        PluginConfiguration aipDs = new PluginConfiguration(metadata, "LABEL", parameters);
        // FIXME : @svissier Why the fuck is this shit ?
        String truc = gsonBuilder.create().toJson(aipDs);
        dsPlugin = PluginUtils.getPlugin(parameters, AipDataSourcePlugin.class, Arrays.asList(PLUGIN_CURRENT_PACKAGE),
                                         pluginCacheMap);

    }

    /**
     * Import model definition file from resources directory
     * @param filename filename
     * @return list of created model attributes
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

    // This method is called by Aip client proxy (from AipDataSourceConfiguration) to provide some AIPs when calling
    // aip client method
    protected static List<AIP> createAIPs(int count, String... tags) {
        List<AIP> aips = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UniformResourceName id = new UniformResourceName(OAISIdentifier.AIP, EntityType.DATA, TENANT,
                    UUID.randomUUID(), 1);
            AIPBuilder builder = new AIPBuilder(id, Optional.empty(), "sipId" + i, EntityType.DATA, "session 1");
            builder.addTags(tags);

            builder.addDescriptiveInformation("label", "libellé du data object " + i);
            builder.addDescriptiveInformation("START_DATE", OffsetDateTime.now());
            builder.addDescriptiveInformation("ALT_MAX", 1500 + i);
            builder.addDescriptiveInformation("HISTORY", new String[] { "H1", "H2", "H3" });
            builder.addDescriptiveInformation("HISTORY_SET", Arrays.asList("aaaa", "bb", "ccccc", "dddd").stream()
                    .collect(Collectors.toSet()));
            builder.addDescriptiveInformation("HISTORY_LIST", Arrays
                    .asList("paris", "toulouse", "lyon", "nice", "bordeaux").stream().collect(Collectors.toList()));
            builder.addDescriptiveInformation("POUET", "POUET");

            Map<String, String> dateBounds = new HashMap<>();
            dateBounds.put(DataSourcePluginConstants.LOWER_BOUND, OffsetDateTime.now().atZoneSameInstant(ZoneOffset.UTC)
                    .format(OffsetDateTimeAdapter.ISO_DATE_TIME_UTC));
            dateBounds.put(DataSourcePluginConstants.UPPER_BOUND, OffsetDateTime.now().atZoneSameInstant(ZoneOffset.UTC)
                    .format(OffsetDateTimeAdapter.ISO_DATE_TIME_UTC));
            builder.addDescriptiveInformation("range", dateBounds);

            Map<String, Integer> intBounds = new HashMap<>();
            intBounds.put("ilow", 100);
            intBounds.put("iup", null);
            builder.addDescriptiveInformation("intrange", intBounds);

            aips.add(builder.build());
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

        // FIXME
        // map.put("properties.history", "properties.descriptiveInformation.NIMP");
        return map;
    }

    @Test
    public void test() throws DataSourceException {
        Page<DataObject> page = dsPlugin.findAll(getDefaultTenant(), new PageRequest(0, 10));
        Assert.assertNotNull(page);
        Assert.assertNotNull(page.getContent());
        Assert.assertTrue(page.getContent().size() > 0);
        DataObject do1 = page.getContent().get(0);
        Assert.assertEquals("libellé du data object 0", do1.getLabel());
        Assert.assertNotNull(do1.getProperty("START_DATE"));
        Assert.assertNotNull(do1.getProperty("ALTITUDE.MAX"));
        Assert.assertNull(do1.getProperty("ALTITUDE.MIN"));
        Assert.assertNotNull(do1.getTags());
        Assert.assertTrue(do1.getTags().contains("tag1"));
        Assert.assertTrue(do1.getTags().contains("tag2"));
        Assert.assertTrue(do1.getProperty("history") instanceof StringArrayAttribute);
        Assert.assertTrue(Arrays.binarySearch(((StringArrayAttribute) do1.getProperty("history")).getValue(),
                                              "H1") > -1);
        Assert.assertTrue(Arrays.binarySearch(((StringArrayAttribute) do1.getProperty("history")).getValue(),
                                              "H2") > -1);
        Assert.assertTrue(do1.getProperty("DATE_INTERVAL") instanceof DateIntervalAttribute);
        Assert.assertTrue(do1.getProperty("INT_INTERVAL") instanceof IntegerIntervalAttribute);
        Assert.assertNotNull(do1.getFiles());
        Assert.assertEquals(1, do1.getFiles().size());
        Assert.assertTrue(do1.getFiles().containsKey(DataType.RAWDATA));

        Assert.assertTrue(do1.getTags().contains("TOTO"));
        Assert.assertTrue(do1.getTags().contains("TITI"));

        Assert.assertTrue(do1.getProperty("SIZE") instanceof LongAttribute);

    }

    @After
    public void tearDown() throws ModuleException {
        Model model = modelService.getModelByName(MODEL_NAME);
        if (model != null) {
            modelService.deleteModel(model.getName());
        }
    }
}
