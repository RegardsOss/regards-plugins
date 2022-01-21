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
package fr.cnes.regards.framework.s3.client;

import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.framework.modules.plugins.domain.parameter.IPluginParam;
import fr.cnes.regards.framework.utils.plugins.PluginUtils;
import fr.cnes.regards.framework.utils.plugins.exception.NotAvailablePluginConfigurationException;
import fr.cnes.regards.modules.storage.domain.database.FileLocation;
import fr.cnes.regards.modules.storage.domain.database.FileReference;
import fr.cnes.regards.modules.storage.domain.database.FileReferenceMetaInfo;
import fr.cnes.regards.modules.storage.plugin.s3.S3OnlineStorage;
import org.junit.*;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.MimeType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Instantiate S3 plugin to test with local minio deployed server
 *
 * @author Marc SORDI
 */
@Ignore("This test requires an S3 server available")
public class S3OnlineStoragePluginTest {

    private S3OnlineStorage s3OnlineStorage;

    @Before
    public void prepare() {
        // Initialize plugin environment
        PluginUtils.setup();
    }

    private void loadPlugin(String endpoint, String region, String key, String secret, String bucket, String rootPath) {

        // Set plugin configuration
        Collection<IPluginParam> parameters = IPluginParam.set(
                IPluginParam.build(S3OnlineStorage.S3_SERVER_ENDPOINT_PARAM_NAME, endpoint),
                IPluginParam.build(S3OnlineStorage.S3_SERVER_REGION_PARAM_NAME, region),
                IPluginParam.build(S3OnlineStorage.S3_SERVER_KEY_PARAM_NAME, key),
                IPluginParam.build(S3OnlineStorage.S3_SERVER_SECRET_PARAM_NAME, secret),
                IPluginParam.build(S3OnlineStorage.S3_SERVER_BUCKET_PARAM_NAME, bucket),
                IPluginParam.build(S3OnlineStorage.S3_SERVER_ROOT_PATH_PARAM_NAME, rootPath));

        PluginConfiguration pluginConfiguration = PluginConfiguration.build(S3OnlineStorage.class, "S3 plugin",
                                                                            parameters);
        // Load plugin
        try {
            s3OnlineStorage = PluginUtils.getPlugin(pluginConfiguration, new HashMap<>());
        } catch (NotAvailablePluginConfigurationException e) {
            Assert.fail(e.getMessage());
        }
        Assert.assertNotNull(s3OnlineStorage);
    }

    @Test
    public void givenS3_whenReference_thenValidateAndRetrieveFile() throws IOException {

        loadPlugin("http://127.0.0.1:9000", "cnes", "123456789", "123456789", "h2swot", "");

        // Retrieve file
        FileReference fileReference = new FileReference("regards",
                                                        new FileReferenceMetaInfo("706126bf6d8553708227dba90694e81c",
                                                                                  "MD5", "small.txt", 427L,
                                                                                  MimeType.valueOf("text/plain")),
                                                        new FileLocation("S3",
                                                                         "http://127.0.0.1:9000/h2swot/small.txt"));

        // Validate reference
        Assert.assertTrue(String.format("Invalid URL %s", fileReference.getLocation().getUrl()),
                          s3OnlineStorage.isValidUrl(fileReference.getLocation().getUrl(), new HashSet<>()));

        // Get file from as input stream
        InputStream inputStream = s3OnlineStorage.retrieve(fileReference);
        Assert.assertNotNull(inputStream);

        // Write result to file
        File targetFile = Paths.get("target", "result1.txt").toFile();
        try (FileOutputStream outputStream = new FileOutputStream(targetFile)) {
            FileCopyUtils.copy(inputStream, outputStream);
        }
    }
}
