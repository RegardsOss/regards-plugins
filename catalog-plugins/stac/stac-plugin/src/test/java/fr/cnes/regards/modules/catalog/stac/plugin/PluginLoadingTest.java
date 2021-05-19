/*
 * Copyright 2017-2021 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.catalog.stac.plugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.Assert;
import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import fr.cnes.regards.framework.gson.GsonCustomizer;
import fr.cnes.regards.framework.gson.GsonProperties;
import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.framework.utils.plugins.PluginUtils;
import fr.cnes.regards.framework.utils.plugins.exception.NotAvailablePluginConfigurationException;

/**
 * @author marc
 *
 */
public class PluginLoadingTest {

    private PluginConfiguration loadPluginConfiguration() throws IOException {

        GsonBuilder builder = GsonCustomizer.gsonBuilder(Optional.of(new GsonProperties()), Optional.empty());
        Gson gson = builder.create();

        InputStream inputStream = this.getClass().getResourceAsStream("config-cat-ref.json");
        try (Reader reader = new BufferedReader(
                new InputStreamReader(inputStream, Charset.forName(StandardCharsets.UTF_8.name())))) {
            PluginConfiguration configuration = gson.fromJson(reader, PluginConfiguration.class);
            return configuration;
        } catch (Exception e) {
            Assert.fail(e.getMessage());
            throw e;
        }
    }

    @Test
    public void loadPlugin() throws NotAvailablePluginConfigurationException, IOException {

        PluginUtils.setup();
        PluginConfiguration configuration = loadPluginConfiguration();
        StacSearchEngine searchEngine = PluginUtils.getPlugin(configuration, null);
        Assert.assertNotNull(searchEngine);
    }
}
