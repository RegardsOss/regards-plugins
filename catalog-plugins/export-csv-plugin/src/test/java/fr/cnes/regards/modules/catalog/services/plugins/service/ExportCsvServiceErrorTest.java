/*
 * Copyright 2017-2023 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.catalog.services.plugins.service;

import com.google.gson.Gson;
import fr.cnes.regards.framework.gson.GsonCustomizer;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.modules.catalog.services.helper.IServiceHelper;
import fr.cnes.regards.modules.catalog.services.plugins.domain.CsvHeader;
import fr.cnes.regards.modules.catalog.services.plugins.domain.CsvProcessingException;
import fr.cnes.regards.modules.catalog.services.plugins.utils.DataInitHelper;
import fr.cnes.regards.modules.catalog.services.plugins.utils.StreamHelper;
import fr.cnes.regards.modules.search.domain.SearchRequest;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.Optional;

/**
 * The purpose of this test is to verify if csv is not generated in case of error.
 *
 * @author Iliana Ghazali
 **/
@RunWith(MockitoJUnitRunner.class)
public class ExportCsvServiceErrorTest {

    public ExportCsvService exportCsvService;

    public IServiceHelper serviceHelper;

    @Before
    public void init() {
        Gson gson = GsonCustomizer.gsonBuilder(Optional.empty(), Optional.empty()).create();
        serviceHelper = Mockito.mock(IServiceHelper.class);
        this.exportCsvService = new ExportCsvService(serviceHelper, gson);
    }

    @Test
    public void givenNoDataObjects_whenExportCsv_thenCsvFail() throws ModuleException {
        // GIVEN
        // mock return of dataObjects
        Mockito.when(serviceHelper.getDataObjects((SearchRequest) Mockito.any(), Mockito.anyInt(), Mockito.anyInt()))
               .thenThrow(new ModuleException("Excepted exception for testing purposes."));

        // WHEN
        StreamingResponseBody generatedCsv = exportCsvService.writeCsvPageStream(Mockito.mock(SearchRequest.class),
                                                                                 new CsvHeader(DataInitHelper.DataProperties.getAllPropertiesAsPaths(),
                                                                                               List.of()),
                                                                                 "output_error.csv");
        // THEN
        // check CsvProcessingException is thrown when dataObjects could not be retrieved
        Assertions.assertThatThrownBy(() -> StreamHelper.writeResponseBodyToFile(generatedCsv,
                                                                                 "target/output_error.csv"))
                  .isInstanceOf(CsvProcessingException.class);
    }

}
