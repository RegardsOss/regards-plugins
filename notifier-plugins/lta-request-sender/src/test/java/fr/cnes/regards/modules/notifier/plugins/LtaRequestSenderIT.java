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
package fr.cnes.regards.modules.notifier.plugins;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import fr.cnes.regards.common.notifier.plugins.AbstractRabbitMQSender;
import fr.cnes.regards.framework.amqp.IInstancePublisher;
import fr.cnes.regards.framework.amqp.IPublisher;
import fr.cnes.regards.framework.amqp.ISubscriber;
import fr.cnes.regards.framework.amqp.event.AbstractRequestEvent;
import fr.cnes.regards.framework.geojson.geometry.IGeometry;
import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.framework.modules.plugins.domain.parameter.IPluginParam;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.urn.DataType;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.framework.utils.plugins.PluginUtils;
import fr.cnes.regards.framework.utils.plugins.exception.NotAvailablePluginConfigurationException;
import fr.cnes.regards.modules.feature.dto.Feature;
import fr.cnes.regards.modules.feature.dto.urn.FeatureUniformResourceName;
import fr.cnes.regards.modules.ltamanager.dto.submission.input.ProductFileDto;
import fr.cnes.regards.modules.ltamanager.dto.submission.input.SubmissionRequestDto;
import fr.cnes.regards.modules.model.domain.attributes.AttributeModel;
import fr.cnes.regards.modules.model.domain.attributes.AttributeModelBuilder;
import fr.cnes.regards.modules.model.domain.attributes.Fragment;
import fr.cnes.regards.modules.model.dto.properties.PropertyType;
import fr.cnes.regards.modules.model.gson.IAttributeHelper;
import fr.cnes.regards.modules.model.gson.MultitenantFlattenedAttributeAdapterFactory;
import fr.cnes.regards.modules.notifier.domain.NotificationRequest;
import fr.cnes.regards.modules.notifier.domain.plugin.IRecipientNotifier;
import fr.cnes.regards.modules.notifier.dto.out.NotificationState;
import fr.cnes.regards.modules.notifier.utils.SessionUtils;
import fr.cnes.regards.modules.workermanager.dto.events.EventHeadersHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.MimeType;

import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Thibaud Michaudel
 **/
@RunWith(SpringRunner.class)
@ActiveProfiles(value = { "test", "noscheduler" })
@ContextConfiguration(classes = { LtaRequestSenderIT.ScanningConfiguration.class })
@EnableAutoConfiguration(exclude = { JpaRepositoriesAutoConfiguration.class, FlywayAutoConfiguration.class })
@PropertySource({ "classpath:amqp.properties", "classpath:cloud.properties" })
@TestPropertySource(
    properties = { "regards.amqp.enabled=true", "spring.application.name=rs-test", "regards.cipher.iv=1234567812345678",
        "regards.cipher.keyLocation=src/test/resources/testKey" })
public class LtaRequestSenderIT {

    @Configuration
    @ComponentScan(basePackages = { "fr.cnes.regards.modules" })
    public static class ScanningConfiguration {

        @Bean
        public IPublisher getPublisher() {
            return Mockito.spy(IPublisher.class);
        }

        @MockBean
        public ISubscriber subscriber;

        @MockBean
        public IInstancePublisher instancePublisher;

        @Bean
        public IRuntimeTenantResolver getRuntimeTenantResolver() {
            IRuntimeTenantResolver resolverMock = Mockito.mock(IRuntimeTenantResolver.class);
            Mockito.when(resolverMock.getTenant()).thenReturn(TENANT);
            return resolverMock;
        }

        @Bean
        public IAttributeHelper getAttributeHelper() {
            IAttributeHelper resolverMock = Mockito.mock(IAttributeHelper.class);
            List<AttributeModel> attributeModels = new ArrayList<>();
            AttributeModel propertyA = new AttributeModelBuilder("stringA", PropertyType.STRING, "stringA").setFragment(
                Fragment.buildDefault()).build();
            AttributeModel propertyB = new AttributeModelBuilder("integerB",
                                                                 PropertyType.INTEGER,
                                                                 "integerB").setFragment(Fragment.buildDefault())
                                                                            .build();
            AttributeModel datatype = new AttributeModelBuilder("datatype",
                                                                PropertyType.STRING,
                                                                "datatype").setFragment(Fragment.buildDefault())
                                                                           .build();

            attributeModels.add(propertyA);
            attributeModels.add(propertyB);
            attributeModels.add(datatype);
            Mockito.when(resolverMock.getAllAttributes()).thenReturn(attributeModels);
            return resolverMock;
        }
    }

    private static final String TENANT = "TEST_TENANT";

    private final List<NotificationRequest> notificationRequests = new ArrayList<>();

    @Captor
    private ArgumentCaptor<Collection<Message>> messagesCaptor;

    @Autowired
    protected IPublisher publisher;

    @Autowired
    Gson gson;

    @Autowired
    IAttributeHelper attributeHelper;

    @Autowired
    IRuntimeTenantResolver runtimeTenantResolver;

    @Autowired
    private MultitenantFlattenedAttributeAdapterFactory multitenantFlattenedAttributeAdapterFactory;

    @Before
    public void init() throws IOException {
        initNotificationRequests();
        Mockito.clearInvocations(publisher);
        multitenantFlattenedAttributeAdapterFactory.registerAttributes(runtimeTenantResolver.getTenant(),
                                                                       attributeHelper.getAllAttributes());
    }

    @Test
    public void testLtaRequestSender() throws NotAvailablePluginConfigurationException {
        IRecipientNotifier plugin = instantiateLtaPlugin();
        Assert.assertNotNull(plugin);

        plugin.send(notificationRequests);
        Mockito.verify(publisher, Mockito.times(1))
               .broadcastAll(Mockito.anyString(),
                             Mockito.any(),
                             Mockito.any(),
                             Mockito.any(),
                             Mockito.anyInt(),
                             messagesCaptor.capture(),
                             Mockito.any());

        List<Message> messagesSent = (List<Message>) messagesCaptor.getValue();
        Assert.assertEquals(messagesSent.size(), notificationRequests.size());
        for (Message message : messagesSent) {
            //Common headers
            MessageProperties messageHeaders = message.getMessageProperties();
            Assert.assertEquals(messageHeaders.getHeader("source"), "REGARDS-" + TENANT);
            Assert.assertEquals(messageHeaders.getHeader(EventHeadersHelper.TENANT_HEADER), TENANT);

            //Body
            SubmissionRequestDto requestMessage = gson.fromJson(new String(message.getBody()),
                                                                SubmissionRequestDto.class);
            Optional<String> requestIdToCheck = notificationRequests.stream()
                                                                    .map(notificationRequest -> notificationRequest.getPayload()
                                                                                                                   .get(
                                                                                                                       "id")
                                                                                                                   .getAsString())
                                                                    .filter(notificationRequestId -> new String(message.getBody()).contains(
                                                                        notificationRequestId))
                                                                    .findAny();
            if (requestIdToCheck.isPresent()) {
                SubmissionRequestDto expected;
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                String date = sdf.format(new Date());
                switch (requestIdToCheck.get()) {
                    case "feature1-id":
                        Map<String, Object> properties = new HashMap();
                        properties.put("stringA", "stringAValue");
                        //Because of json deserialization of Number as Object, integerB isn't an integer anymore
                        //This behavior poses no problem as the value will be correctly converted back to integer when needed during ingestion
                        properties.put("integerB", 55.0);
                        properties.put("datatype", "L0A_LR_Packet");
                        List<ProductFileDto> files = new ArrayList<>();
                        files.add(new ProductFileDto(DataType.RAWDATA,
                                                     "file://./file.txt",
                                                     "file.txt",
                                                     "824cacd0b51c11594bc91cb9f6eb1114",
                                                     MimeType.valueOf("text/plain")));
                        expected = new SubmissionRequestDto("feature1-id",
                                                            "TestDataType",
                                                            null,
                                                            files,
                                                            Collections.emptyList(),
                                                            "URN:FEATURE:DATA:test:11111111-aaaa-aaaa-aaaa-111111111111:V1",
                                                            properties,
                                                            null,
                                                            "L0A_LR_Packet-" + date + "-swot",
                                                            false);
                        break;
                    case "feature2-id":
                        expected = new SubmissionRequestDto("feature2-id",
                                                            "TestDataType",
                                                            IGeometry.simplePolygon(1, 2, 3, 4),
                                                            Collections.emptyList(),
                                                            Collections.emptyList(),
                                                            "URN:FEATURE:DATA:test:22222222-bbbb-bbbb-bbbb-222222222222:V1",
                                                            new HashMap<>(),
                                                            null,
                                                            "sessionNamePatternError-" + date,
                                                            false);
                        break;
                    default:
                        expected = null;
                }
                Assert.assertEquals(expected, requestMessage);
            } else {
                Assert.fail();
            }
        }
    }

    private static IRecipientNotifier instantiateLtaPlugin() throws NotAvailablePluginConfigurationException {
        String exchange = "exchange";
        String queueName = "queueName";
        String recipientLabel = "recipientLabel";
        String dataType = "TestDataType";
        String sessionNamePattern = "{properties.datatype}-#day-swot";
        String recipientTenant = TENANT;
        boolean replaceMode = false;
        boolean ackRequired = true;

        // Plugin parameters
        Set<IPluginParam> parameters = IPluginParam.set(IPluginParam.build(AbstractRabbitMQSender.EXCHANGE_PARAM_NAME,
                                                                           exchange),
                                                        IPluginParam.build(AbstractRabbitMQSender.QUEUE_PARAM_NAME,
                                                                           queueName),
                                                        IPluginParam.build(AbstractRabbitMQSender.RECIPIENT_LABEL_PARAM_NAME,
                                                                           recipientLabel),
                                                        IPluginParam.build(LtaRequestSender.DATATYPE_PARAM_NAME,
                                                                           dataType),
                                                        IPluginParam.build(LtaRequestSender.SESSION_NAME_PATTERN_PARAM_NAME,
                                                                           sessionNamePattern),
                                                        IPluginParam.build(LtaRequestSender.RECIPIENT_TENANT_PARAM_NAME,
                                                                           recipientTenant),
                                                        IPluginParam.build(LtaRequestSender.REPLACE_MODE_PARAM_NAME,
                                                                           replaceMode),
                                                        IPluginParam.build(LtaRequestSender.ACK_REQUIRED_PARAM_NAME,
                                                                           ackRequired));

        // Instantiate plugin
        PluginUtils.setup();
        IRecipientNotifier plugin = PluginUtils.getPlugin(PluginConfiguration.build(LtaRequestSender.class,
                                                                                    UUID.randomUUID().toString(),
                                                                                    parameters),
                                                          new ConcurrentHashMap<>());
        return plugin;
    }

    private void initNotificationRequests() throws IOException {
        Set<JsonObject> featuresSamples = new HashSet<>();
        featuresSamples.add(gson.fromJson(new InputStreamReader(getClass().getClassLoader()
                                                                          .getResourceAsStream("requests/request1.json")),
                                          JsonObject.class));
        Feature request2 = Feature.build("feature2-id",
                                         "feature2-owner",
                                         FeatureUniformResourceName.fromString(
                                             "URN:FEATURE:DATA:test:22222222-bbbb-bbbb-bbbb-222222222222:V1"),
                                         IGeometry.simplePolygon(1, 2, 3, 4),
                                         EntityType.DATA,
                                         "feature-2-model");
        featuresSamples.add((JsonObject) gson.toJsonTree(request2));

        JsonObject metadata = gson.fromJson("{\""
                                            + SessionUtils.SESSION_OWNER_METADATA_PATH
                                            + "\":\"testSessionOwner"
                                            + "\",\""
                                            + SessionUtils.SESSION_METADATA_PATH
                                            + "\":\"testSession\"}", JsonObject.class);

        for (JsonObject feature : featuresSamples) {
            notificationRequests.add(new NotificationRequest(feature,
                                                             metadata.getAsJsonObject(),
                                                             AbstractRequestEvent.generateRequestId(),
                                                             this.getClass().getSimpleName(),
                                                             OffsetDateTime.now(),
                                                             NotificationState.SCHEDULED,
                                                             new HashSet<>()));
        }
    }

}
