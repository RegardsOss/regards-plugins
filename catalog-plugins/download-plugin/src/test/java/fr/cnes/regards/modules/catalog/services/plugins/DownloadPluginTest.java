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
package fr.cnes.regards.modules.catalog.services.plugins;

import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.urn.DataType;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.framework.urn.UniformResourceName;
import fr.cnes.regards.modules.catalog.services.domain.ServicePluginParameters;
import fr.cnes.regards.modules.catalog.services.helper.IServiceHelper;
import fr.cnes.regards.modules.catalog.services.plugin.DownloadPlugin;
import fr.cnes.regards.modules.dam.domain.entities.DataObject;
import fr.cnes.regards.modules.dam.domain.entities.feature.DataObjectFeature;
import fr.cnes.regards.modules.indexer.domain.DataFile;
import fr.cnes.regards.modules.model.domain.Model;
import fr.cnes.regards.modules.search.domain.SearchRequest;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.UUID;

/**
 * @author Léo Mieulet
 */
public class DownloadPluginTest {

    @Test
    public void testApply() throws ModuleException, IOException {
        // given
        DownloadPlugin downloadPlugin = initDownloadPlugin(false);

        // when
        ResponseEntity<StreamingResponseBody> result = getDownloadPluginApplyResult(downloadPlugin);

        // then
        Assert.assertEquals(HttpStatus.OK, result.getStatusCode());
        Assert.assertEquals(169, getBodySize(result.getBody()));
    }


    @Test
    public void testApplyFilesTooBig() throws ModuleException, IOException {
        // given
        DownloadPlugin downloadPlugin = initDownloadPlugin(true);

        // when
        ResponseEntity<StreamingResponseBody> result = getDownloadPluginApplyResult(downloadPlugin);

        // then
        Assert.assertEquals(HttpStatus.OK, result.getStatusCode());
        Assert.assertEquals("\"Total size of selected files, 1716 Mo, exceeded maximum allowed of 1 Mo\"",
                            getBodyContent(result.getBody()));
    }

    private ResponseEntity<StreamingResponseBody> getDownloadPluginApplyResult(DownloadPlugin downloadPlugin) {
        final HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        return downloadPlugin.apply(new ServicePluginParameters(), response);
    }

    private DownloadPlugin initDownloadPlugin(boolean bigFile) throws ModuleException {
        DownloadPlugin downloadPlugin = new DownloadPlugin();
        downloadPlugin.init();

        IServiceHelper myService = Mockito.mock(IServiceHelper.class);

        DataObject do1 = getDataObject(bigFile);

        Page<DataObject> dataObjectStub = new PageImpl<>(Collections.singletonList(do1));
        Mockito.when(myService.getDataObjects((SearchRequest) Mockito.any(), Mockito.anyInt(), Mockito.anyInt()))
               .thenReturn(dataObjectStub);

        ReflectionTestUtils.setField(downloadPlugin, "serviceHelper", myService);
        ReflectionTestUtils.setField(downloadPlugin, "maxFilesSizeToDownload", 1);
        ReflectionTestUtils.setField(downloadPlugin, "maxFilesToDownload", 100);
        ReflectionTestUtils.setField(downloadPlugin, "archiveFileName", "download.zip");
        return downloadPlugin;
    }

    private int getBodySize(StreamingResponseBody resultBody) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        resultBody.writeTo(byteArrayOutputStream);
        return byteArrayOutputStream.size();
    }

    private String getBodyContent(StreamingResponseBody resultBody) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        resultBody.writeTo(byteArrayOutputStream);
        return byteArrayOutputStream.toString(Charset.defaultCharset());
    }

    private DataObject getDataObject(boolean bigFile) {
        UniformResourceName urn = UniformResourceName.build("theidentifier",
                                                            EntityType.DATA,
                                                            "theTenant",
                                                            UUID.randomUUID(),
                                                            5,
                                                            5L,
                                                            "test");
        DataObject do1 = new DataObject(new Model(), new DataObjectFeature(urn, "provider " + "id", "label"));

        if (bigFile) {
            addFile(DataType.RAWDATA, do1, 1800000000L);
        } else {
            addFile(DataType.RAWDATA, do1, 170000L);
        }
        addFile(DataType.QUICKLOOK_MD, do1, 15000L);
        return do1;
    }

    private void addFile(DataType rawdata, DataObject do1, long filesize) {
        URI folderUri = Paths.get("src/test/resources/filename.yml").toUri();
        DataFile dataFileRawData = DataFile.build(rawdata, "filename.yml", folderUri, null, true, true);
        dataFileRawData.setFilesize(filesize);
        dataFileRawData.setChecksum("checksum" + filesize);
        do1.getFiles().put(rawdata, dataFileRawData);
    }
}
