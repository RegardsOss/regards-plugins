/*
 * Copyright 2017-2024 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.catalog.services.plugins.utils;

import fr.cnes.regards.framework.gson.GsonCustomizer;
import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.framework.modules.plugins.dto.parameter.parameter.IPluginParam;
import fr.cnes.regards.framework.utils.plugins.PluginParameterTransformer;
import fr.cnes.regards.framework.utils.plugins.PluginUtils;
import fr.cnes.regards.framework.utils.plugins.exception.NotAvailablePluginConfigurationException;
import fr.cnes.regards.modules.catalog.services.plugins.ExportCsvConstants;
import fr.cnes.regards.modules.catalog.services.plugins.ExportCsvPlugin;
import fr.cnes.regards.modules.catalog.services.plugins.service.ExportCsvService;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper used to generate {@link ExportCsvPlugin}.
 *
 * @author Iliana Ghazali
 **/
public class PluginHelper {

    private PluginHelper() {
        // helper class
    }

    public static Set<IPluginParam> initPluginParameters(String dynamicCsvFilename,
                                                         int maxDataobjectsToExport,
                                                         List<String> basicPropertiesToExclude,
                                                         List<String> dynamicPropertiesToRetrieve) {
        PluginParameterTransformer.setup(GsonCustomizer.gsonBuilder(Optional.empty(), Optional.empty()).create());
        Set<IPluginParam> pluginParams = new HashSet<>();
        pluginParams.add(IPluginParam.build(ExportCsvConstants.MAX_DATA_OBJECTS_TO_EXPORT, maxDataobjectsToExport));
        if (dynamicCsvFilename != null) {
            pluginParams.add(IPluginParam.build(ExportCsvConstants.DYNAMIC_CSV_FILENAME, dynamicCsvFilename));
        }
        if (basicPropertiesToExclude != null) {
            pluginParams.add(IPluginParam.build(ExportCsvConstants.BASIC_PROPERTIES,
                                                PluginParameterTransformer.toJson(basicPropertiesToExclude)));

        }
        if (dynamicPropertiesToRetrieve != null) {
            pluginParams.add(IPluginParam.build(ExportCsvConstants.DYNAMIC_PROPERTIES,
                                                PluginParameterTransformer.toJson(dynamicPropertiesToRetrieve)));
        }
        return pluginParams;
    }

    public static ExportCsvPlugin initPlugin(ExportCsvService exportCsvService, Set<IPluginParam> pluginParameters)
        throws NotAvailablePluginConfigurationException {
        PluginUtils.setup();
        ExportCsvPlugin plugin = PluginUtils.getPlugin(PluginConfiguration.build(ExportCsvPlugin.class,
                                                                                 "ExportCsvPlugin",
                                                                                 pluginParameters),
                                                       new ConcurrentHashMap<>());
        ReflectionTestUtils.setField(plugin, "exportCsvService", exportCsvService);
        return plugin;
    }

}
