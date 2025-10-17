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
package fr.cnes.regards.modules.catalog.services.plugins;

import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.dto.parameter.parameter.IPluginParam;
import fr.cnes.regards.framework.utils.plugins.exception.NotAvailablePluginConfigurationException;
import fr.cnes.regards.modules.catalog.services.domain.ServicePluginParameters;
import fr.cnes.regards.modules.catalog.services.plugins.service.ExportCsvService;
import fr.cnes.regards.modules.catalog.services.plugins.utils.DataInitHelper;
import fr.cnes.regards.modules.catalog.services.plugins.utils.PluginHelper;
import fr.cnes.regards.modules.catalog.services.plugins.utils.StreamHelper;
import jakarta.servlet.http.HttpServletResponse;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * The purpose of this test is to verify if {@link ExportCsvPlugin} handles csv generation error properly.
 *
 * @author Iliana Ghazali
 **/
@RunWith(MockitoJUnitRunner.class)
public class ExportCsvPluginErrorTest {

    @Mock
    private ExportCsvService exportCsvService;

    @Test
    public void givenExceededDataObjects_whenExportCsv_thenError()
        throws IOException, ModuleException, NotAvailablePluginConfigurationException {
        // --- GIVEN ---
        // init plugin with parameters
        int maxNbDataObjects = 3;
        Set<IPluginParam> pluginParameters = PluginHelper.initPluginParameters(null, maxNbDataObjects, null, List.of());
        ExportCsvPlugin plugin = PluginHelper.initPlugin(exportCsvService, pluginParameters);
        // mock dataObjects retrieved to generate csv (more than max allowed)
        int nbDataObjects = 4;
        Mockito.when(exportCsvService.retrievePageDataObjects(Mockito.any(), Mockito.anyInt(), Mockito.anyInt()))
               .thenAnswer(asw -> DataInitHelper.simulatePageDataObjects(nbDataObjects,
                                                                         asw.getArgument(1),
                                                                         asw.getArgument(2),
                                                                         DataInitHelper.DataObjectTestType.values()));
        // --- WHEN ---
        ResponseEntity<StreamingResponseBody> result = plugin.apply(new ServicePluginParameters(),
                                                                    Mockito.mock(HttpServletResponse.class));
        // --- THEN ---
        // error message should be returned because csv could not be generated (maxDataObjectsToExport exceeded)
        Assertions.assertThat(result.getStatusCode())
                  .isEqualTo(HttpStatus.OK); // status is set to ok even in case of error
        Assertions.assertThat(result.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        StreamingResponseBody body = result.getBody();
        Assertions.assertThat(body).isNotNull();
        Assertions.assertThat(StreamHelper.getBodyContent(body))
                  .isEqualTo(String.format("\"Number of data to export to csv (%s) exceeds the maximum number of data "
                                           + "allowed (%s). Csv was not created.\"", nbDataObjects, maxNbDataObjects));
    }

    @Test
    public void givenProcessingException_whenExportCsv_thenError() throws IOException, ModuleException {
        // --- GIVEN ---
        // init plugin with parameters
        Set<IPluginParam> pluginParameters = PluginHelper.initPluginParameters(null, 10000, null, List.of());
        ExportCsvPlugin plugin = PluginHelper.initPlugin(exportCsvService, pluginParameters);
        // throw exception when retrievePageDataObjects is called
        String expectedMessage = "Expected exception";
        Mockito.when(exportCsvService.retrievePageDataObjects(Mockito.any(), Mockito.anyInt(), Mockito.anyInt()))
               .thenThrow(new ModuleException(expectedMessage));
        // --- WHEN ---
        ResponseEntity<StreamingResponseBody> result = plugin.apply(new ServicePluginParameters(),
                                                                    Mockito.mock(HttpServletResponse.class));
        // --- THEN ---
        // error message should be returned because exception was thrown
        Assertions.assertThat(result.getStatusCode())
                  .isEqualTo(HttpStatus.OK); // status is set to ok even in case of error
        Assertions.assertThat(result.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        StreamingResponseBody body = result.getBody();
        Assertions.assertThat(body).isNotNull();
        Assertions.assertThat(StreamHelper.getBodyContent(body))
                  .isEqualTo(String.format("\"Error during creation of csv. Cause: %s.\"", expectedMessage));
    }

}
