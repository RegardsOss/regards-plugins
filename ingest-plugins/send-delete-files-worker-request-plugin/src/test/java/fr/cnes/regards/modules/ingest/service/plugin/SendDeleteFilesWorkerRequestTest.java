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
package fr.cnes.regards.modules.ingest.service.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.cnes.regards.framework.amqp.IPublisher;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.oais.InformationPackageProperties;
import fr.cnes.regards.framework.oais.OAISDataObjectLocation;
import fr.cnes.regards.framework.urn.DataType;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.modules.ingest.domain.aip.AIPEntity;
import fr.cnes.regards.modules.ingest.domain.sip.SIPEntity;
import fr.cnes.regards.modules.ingest.dto.sip.SIP;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.amqp.core.Message;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author Sébastien Binda
 **/
public class SendDeleteFilesWorkerRequestTest {

    private IPublisher publisher;

    private IRuntimeTenantResolver runtimeTenantResolver;

    private SendDeleteFilesWorkerRequest plugin;

    private ArgumentCaptor<Message> messageCaptor;

    private Gson gson;

    @Before
    public void init() {
        gson = new GsonBuilder().create();
        publisher = Mockito.mock(IPublisher.class);
        runtimeTenantResolver = Mockito.mock(IRuntimeTenantResolver.class);

        plugin = new SendDeleteFilesWorkerRequest();
        ReflectionTestUtils.setField(plugin, "publisher", publisher);
        ReflectionTestUtils.setField(plugin, "runtimeTenantResolver", runtimeTenantResolver);
        ReflectionTestUtils.setField(plugin, "gson", new GsonBuilder().create());
        ReflectionTestUtils.setField(plugin, "contentType", SendDeleteFilesWorkerRequest.WORKER_CONTENT_TYPE_DEFAULT);

        messageCaptor = ArgumentCaptor.forClass(Message.class);
    }

    @Test
    public void test_urls_in_worker_request_from_aips() {

        // GIVEN
        String url1 = "file:///test/tmp/file.txt";
        String url2 = "http://where/test/tmp/file.txt";
        String url3 = "http://where/test/tmp/file2.txt";
        String url4 = "file:///test/tmp/file2.txt";
        Collection<AIPEntity> aipEntities = List.of(initEntity("001", "owner1", "session1", url1),
                                                    initEntity("002", "owner1", "session1", url2),
                                                    initEntity("003", "owner1", "session1", url3, url4));

        // WHEN
        plugin.postprocess(aipEntities);

        // THEN
        Mockito.verify(publisher, Mockito.times(1))
               .basicPublish(Mockito.any(), Mockito.any(), Mockito.any(), messageCaptor.capture());

        List<DeleteFilesRequestDTO> deleteFilesRequestDTOs = deserializeMessages();
        Assert.assertEquals(1, deleteFilesRequestDTOs.size());
        Assert.assertEquals(4, deleteFilesRequestDTOs.get(0).getUrls().size());
        Assert.assertTrue(deleteFilesRequestDTOs.get(0).getUrls().contains(url1));
        Assert.assertTrue(deleteFilesRequestDTOs.get(0).getUrls().contains(url2));
        Assert.assertTrue(deleteFilesRequestDTOs.get(0).getUrls().contains(url3));
        Assert.assertTrue(deleteFilesRequestDTOs.get(0).getUrls().contains(url4));

    }

    @Test
    public void test_urls_in_worker_request_from_aips_many_sessions() {

        // GIVEN
        String url1 = "file:///test/tmp/file.txt";
        String url2 = "http://where/test/tmp/file.txt";
        String url3 = "http://where/test/tmp/file2.txt";
        String url4 = "file:///test/tmp/file2.txt";
        Collection<AIPEntity> aipEntities = List.of(initEntity("001", "owner1", "session1", url1),
                                                    initEntity("002", "owner1", "session2", url2),
                                                    initEntity("003", "owner2", "session1", url3, url4));

        // WHEN
        plugin.postprocess(aipEntities);

        // THEN
        Mockito.verify(publisher, Mockito.times(3))
               .basicPublish(Mockito.any(), Mockito.any(), Mockito.any(), messageCaptor.capture());

        List<DeleteFilesRequestDTO> deleteFilesRequestDTOs = deserializeMessages();
        Assert.assertEquals("One request per session expected.", 3, deleteFilesRequestDTOs.size());

        List<String> urls = deleteFilesRequestDTOs.stream().flatMap(dfr -> dfr.getUrls().stream()).toList();
        Assert.assertEquals(4, urls.size());
        Assert.assertTrue(urls.contains(url1));
        Assert.assertTrue(urls.contains(url2));
        Assert.assertTrue(urls.contains(url3));
        Assert.assertTrue(urls.contains(url4));

    }

    private List<DeleteFilesRequestDTO> deserializeMessages() {
        List<DeleteFilesRequestDTO> results = new ArrayList<>();
        messageCaptor.getAllValues()
                     .stream()
                     .forEach(message -> results.add(gson.fromJson(new String(message.getBody(),
                                                                              StandardCharsets.UTF_8),
                                                                   DeleteFilesRequestDTO.class)));
        return results;
    }

    private AIPEntity initEntity(String providerId, String owner, String session, String... urls) {
        AIPEntity aipEntity = new AIPEntity();
        SIPEntity sipEntity = new SIPEntity();
        SIP sip = SIP.build(EntityType.DATA, providerId);
        InformationPackageProperties ip = InformationPackageProperties.build();
        OAISDataObjectLocation[] locs = Arrays.stream(urls)
                                              .map(OAISDataObjectLocation::build)
                                              .toList()
                                              .toArray(new OAISDataObjectLocation[urls.length]);
        ip.withDataObject(DataType.RAWDATA, "filename", "MD5", "123456789", 12L, locs);
        ip.registerContentInformation();
        sip.setProperties(ip);
        sipEntity.setSession(session);
        sipEntity.setSessionOwner(owner);
        sipEntity.setSip(sip);
        aipEntity.setSip(sipEntity);
        return aipEntity;
    }

}
