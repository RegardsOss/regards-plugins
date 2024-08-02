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
 * The purpose of this test is to verify if {@link ExportCsvPlugin} handles csv generation success properly.
 *
 * @author Iliana Ghazali
 **/
@RunWith(MockitoJUnitRunner.class)
public class ExportCsvPluginNominalTest {

    @Mock
    private ExportCsvService exportCsvService;

    @Test
    public void givenNominalDataObjectsWithAllFields_whenExportCsv_thenCsvCreated()
        throws IOException, ModuleException, NotAvailablePluginConfigurationException {
        // --- GIVEN ---
        // init plugin with parameters
        int maxNbDataObjects = 3;
        Set<IPluginParam> pluginParameters = PluginHelper.initPluginParameters("", maxNbDataObjects, null, List.of());
        ExportCsvPlugin plugin = PluginHelper.initPlugin(exportCsvService, pluginParameters);
        // mock dataObjects retrieved to generate csv
        int nbDataObjects = 2;
        Mockito.when(exportCsvService.retrievePageDataObjects(Mockito.any(), Mockito.anyInt(), Mockito.anyInt()))
               .thenAnswer(asw -> DataInitHelper.simulatePageDataObjects(nbDataObjects,
                                                                         asw.getArgument(1),
                                                                         asw.getArgument(2),
                                                                         DataInitHelper.DataObjectTestType.values()));
        // mock streaming
        Mockito.when(exportCsvService.writeCsvPageStream(Mockito.any(), Mockito.any(), Mockito.any()))
               .thenReturn(StreamHelper.getMockStream());

        // mock csv filename
        Mockito.when(exportCsvService.getCsvFilename(Mockito.any())).thenCallRealMethod();
        Mockito.when(exportCsvService.getHeader(Mockito.any(), Mockito.any())).thenCallRealMethod();

        // --- WHEN ---
        ResponseEntity<StreamingResponseBody> result = plugin.apply(new ServicePluginParameters(),
                                                                    Mockito.mock(HttpServletResponse.class));
        // --- THEN ---
        // assert response is successfully created with expected csv content
        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        Assertions.assertThat(result.getHeaders().getContentType()).isEqualTo(new MediaType("text", "csv"));
        Assertions.assertThat(result.getHeaders().getContentDisposition().getFilename()).startsWith("csv_export_");
        StreamingResponseBody body = result.getBody();
        Assertions.assertThat(body).isNotNull();
        Assertions.assertThat(StreamHelper.getBodyContent(body)).isEqualTo("""
                                                                               Col1,Col2,Col3
                                                                               Val1,Val2,Val3
                                                                               """);
    }

}
