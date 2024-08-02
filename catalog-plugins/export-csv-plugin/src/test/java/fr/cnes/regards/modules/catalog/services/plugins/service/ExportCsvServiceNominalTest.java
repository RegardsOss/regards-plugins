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
package fr.cnes.regards.modules.catalog.services.plugins.service;

import com.google.gson.Gson;
import fr.cnes.regards.framework.gson.GsonCustomizer;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.test.report.annotation.Purpose;
import fr.cnes.regards.modules.catalog.services.helper.IServiceHelper;
import fr.cnes.regards.modules.catalog.services.plugins.domain.CsvHeader;
import fr.cnes.regards.modules.catalog.services.plugins.utils.DataInitHelper;
import fr.cnes.regards.modules.catalog.services.plugins.utils.StreamHelper;
import fr.cnes.regards.modules.search.dto.SearchRequest;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * The purpose of this test is to verify if csv is properly exported from dataObjects.
 *
 * @author Iliana Ghazali
 **/
@RunWith(MockitoJUnitRunner.class)
public class ExportCsvServiceNominalTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExportCsvServiceNominalTest.class);

    private static final String TEST_OUTPUT_PATH = "target/";

    public ExportCsvService exportCsvService;

    public IServiceHelper serviceHelper;

    @Before
    public void init() {
        Gson gson = GsonCustomizer.gsonBuilder(Optional.empty(), Optional.empty()).create();
        serviceHelper = Mockito.mock(IServiceHelper.class);
        this.exportCsvService = new ExportCsvService(serviceHelper, gson);
    }

    @Test
    @Purpose("nominal test")
    public void givenDataObjects_whenExportCsv_thenCsvCreated() throws IOException, ModuleException {
        // GIVEN
        // mock return of dataObjects
        int nbDataObjects = 10;
        Mockito.when(serviceHelper.getDataObjects((SearchRequest) Mockito.any(), Mockito.anyInt(), Mockito.anyInt()))
               .thenAnswer(asw -> DataInitHelper.simulatePageDataObjects(nbDataObjects,
                                                                         asw.getArgument(1),
                                                                         asw.getArgument(2),
                                                                         DataInitHelper.DataObjectTestType.values()));

        // WHEN
        String csvFileName = "output_nominal.csv";
        List<String> dynamicPropertiesToRetrieve = DataInitHelper.DataProperties.getAllPropertiesAsPaths();

        StreamingResponseBody generatedCsv = exportCsvService.writeCsvPageStream(Mockito.mock(SearchRequest.class),
                                                                                 exportCsvService.getHeader(List.of(),
                                                                                                            dynamicPropertiesToRetrieve),
                                                                                 csvFileName);
        // THEN
        // check expected csv and generated csv are identical
        String filePath = TEST_OUTPUT_PATH + csvFileName;
        String expectedFilePath = "src/test/resources/expected_outputs/expected_file_nominal.csv";
        StreamHelper.writeResponseBodyToFile(generatedCsv, filePath);
        Assertions.assertThat(Files.mismatch(Path.of(expectedFilePath), Path.of(filePath)))
                  .as(String.format("Csv file located at \"%s\" does not match expected file \"%s\". "
                                    + "Finds and returns the position of the first mismatched byte in the content of two files, or -1L if there is no mismatch.",
                                    filePath,
                                    expectedFilePath))
                  .isEqualTo(-1L);

    }

    @Test
    @Purpose("nominal test")
    public void givenDataObjectsWithExcludedFields_whenExportCsv_thenCsvCreated() throws IOException, ModuleException {
        // GIVEN
        // mock return of dataObjects
        int nbDataObjects = 10;
        Mockito.when(serviceHelper.getDataObjects((SearchRequest) Mockito.any(), Mockito.anyInt(), Mockito.anyInt()))
               .thenAnswer(asw -> DataInitHelper.simulatePageDataObjects(nbDataObjects,
                                                                         asw.getArgument(1),
                                                                         asw.getArgument(2),
                                                                         DataInitHelper.DataObjectTestType.values()));

        // WHEN
        String csvFileName = "output_nominal_exclude.csv";
        List<String> dynamicPropertiesToRetrieve = DataInitHelper.DataProperties.getAllPropertiesAsPaths();

        StreamingResponseBody generatedCsv = exportCsvService.writeCsvPageStream(Mockito.mock(SearchRequest.class),
                                                                                 exportCsvService.getHeader(List.of("id",
                                                                                                                    "tags",
                                                                                                                    "model",
                                                                                                                    "randomPpt"),
                                                                                                            dynamicPropertiesToRetrieve),
                                                                                 csvFileName);
        // THEN
        // check expected csv and generated csv are identical
        String filePath = TEST_OUTPUT_PATH + csvFileName;
        String expectedFilePath = "src/test/resources/expected_outputs/expected_file_nominal_exclude.csv";
        StreamHelper.writeResponseBodyToFile(generatedCsv, filePath);
        Assertions.assertThat(Files.mismatch(Path.of(expectedFilePath), Path.of(filePath)))
                  .as(String.format("Csv file located at \"%s\" does not match expected file \"%s\". "
                                    + "Finds and returns the position of the first mismatched byte in the content of two files, or -1L if there is no mismatch.",
                                    filePath,
                                    expectedFilePath))
                  .isEqualTo(-1L);

    }

    @Test
    @Purpose("perf test")
    public void givenMaxDataObjects_whenExportCsv_thenCsvCreated() throws IOException, ModuleException {
        // GIVEN
        // mock return of dataObjects
        int nbDataObjects = 2300;
        Mockito.when(serviceHelper.getDataObjects((SearchRequest) Mockito.any(), Mockito.anyInt(), Mockito.anyInt()))
               .thenAnswer(asw -> DataInitHelper.simulatePageDataObjects(nbDataObjects,
                                                                         asw.getArgument(1),
                                                                         asw.getArgument(2),
                                                                         new DataInitHelper.DataObjectTestType[] {
                                                                             DataInitHelper.DataObjectTestType.LARGE }));
        // WHEN
        long start = System.currentTimeMillis();
        String csvFileName = "output_max.csv";
        StreamingResponseBody generatedCsv = exportCsvService.writeCsvPageStream(Mockito.mock(SearchRequest.class),
                                                                                 new CsvHeader(List.of(),
                                                                                               IntStream.rangeClosed(0,
                                                                                                                     DataInitHelper.NB_LARGE_COLUMNS)
                                                                                                        .mapToObj(String::valueOf)
                                                                                                        .toList()),
                                                                                 csvFileName);
        // THEN
        // check csv was generated in the expected amount of time
        String filePath = TEST_OUTPUT_PATH + csvFileName;
        StreamHelper.writeResponseBodyToFile(generatedCsv, filePath);
        long end = System.currentTimeMillis() - start;
        long expectedMaxDurationMs = 4_000;
        LOGGER.info("{} dataObjects exported in csv file \"{}\" in {} ms.", nbDataObjects, filePath, end);
        Assertions.assertThat(end)
                  .as(String.format("Test with %d dataObjects did not end in the expected amount of time (%d ms).",
                                    nbDataObjects,
                                    expectedMaxDurationMs))
                  .isLessThan(expectedMaxDurationMs);
    }

}
