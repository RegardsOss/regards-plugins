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
package fr.cnes.regards.modules.notifier.plugins;

import com.google.common.collect.Lists;
import com.google.gson.JsonObject;
import fr.cnes.regards.framework.amqp.IPublisher;
import fr.cnes.regards.framework.amqp.configuration.AmqpConstants;
import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.framework.modules.plugins.dto.parameter.parameter.IPluginParam;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.utils.plugins.PluginUtils;
import fr.cnes.regards.framework.utils.plugins.exception.NotAvailablePluginConfigurationException;
import fr.cnes.regards.modules.notifier.domain.NotificationRequest;
import fr.cnes.regards.modules.notifier.domain.plugin.IRecipientNotifier;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Léo Mieulet
 */
@RunWith(SpringRunner.class)
@ActiveProfiles(value = { "test", "noscheduler" })
@ContextConfiguration(classes = { DisseminationAckSenderTest.ScanningConfiguration.class })
@EnableAutoConfiguration(exclude = { JpaRepositoriesAutoConfiguration.class, FlywayAutoConfiguration.class })
@PropertySource({ "classpath:amqp.properties", "classpath:cloud.properties" })
@TestPropertySource(properties = { "regards.amqp.enabled=true",
                                   "spring.application.name=rs-test",
                                   "regards.cipher.iv=1234567812345678",
                                   "regards.cipher.keyLocation=src/test/resources/testKey" })
public class DisseminationAckSenderTest {

    @Configuration
    @ComponentScan(basePackages = { "fr.cnes.regards.modules" })
    public static class ScanningConfiguration {

        @Bean
        public IPublisher getPublisher() {
            return Mockito.spy(IPublisher.class);
        }

        @Bean
        public IRuntimeTenantResolver getRuntimeTenantResolver() {
            IRuntimeTenantResolver resolverMock = Mockito.mock(IRuntimeTenantResolver.class);
            Mockito.when(resolverMock.getTenant()).thenReturn(TENANT);
            return resolverMock;
        }
    }

    private static final String TENANT = "SOME_TENANT";

    @Autowired
    protected IPublisher publisher;

    @Captor
    private ArgumentCaptor<String> exchangeNameCaptor;

    @Captor
    private ArgumentCaptor<Optional<String>> queueNameCaptor;

    @Captor
    private ArgumentCaptor<Optional<String>> routingKeyCaptor;

    @Captor
    private ArgumentCaptor<Optional<String>> dlkCaptor;

    @Captor
    private ArgumentCaptor<Integer> priorityCaptor;

    @Captor
    private ArgumentCaptor<Collection<fr.cnes.regards.modules.feature.dto.event.in.DisseminationAckEvent>> messagesCaptor;

    @Captor
    private ArgumentCaptor<Collection<fr.cnes.regards.modules.ingest.dto.request.event.DisseminationAckEvent>> oaisMessagesCaptor;

    @Captor
    private ArgumentCaptor<Map<String, Object>> headersCaptor;

    @Test
    public void testFeatureDisseminationAckSender() throws NotAvailablePluginConfigurationException {
        Mockito.clearInvocations(publisher);

        PluginUtils.setup();
        String featureDisseminationExchange = "featureDisseminationExchange";
        String senderLabel = "senderLabel";
        String featureQueueName = "featureQueueName";

        // Plugin parameters
        Set<IPluginParam> parameters = IPluginParam.set(IPluginParam.build(DisseminationAckSender.FEATURE_EXCHANGE_PARAM_NAME,
                                                                           featureDisseminationExchange),
                                                        IPluginParam.build(DisseminationAckSender.SENDER_LABEL_PARAM_NAME,
                                                                           senderLabel),
                                                        IPluginParam.build(DisseminationAckSender.FEATURE_QUEUE_PARAM_NAME,
                                                                           featureQueueName));

        // Instantiate plugin
        IRecipientNotifier plugin = PluginUtils.getPlugin(PluginConfiguration.build(DisseminationAckSender.class,
                                                                                    UUID.randomUUID().toString(),
                                                                                    parameters),
                                                          new ConcurrentHashMap<>());
        Assert.assertNotNull(plugin);

        // sample requests
        NotificationRequest invalidNotificationRequest = new NotificationRequest();
        JsonObject invalidFeature = new JsonObject();
        invalidFeature.addProperty(DisseminationAckSender.FEATURE_PATH_TO_URN, "invalid_urn");
        invalidNotificationRequest.setPayload(invalidFeature);

        NotificationRequest validNotificationRequest = new NotificationRequest();
        JsonObject validFeature = new JsonObject();
        String validURN = "URN:FEATURE:DATA:PROJECT1:00000000-0000-0000-0000-000169267448:V1";
        validFeature.addProperty(DisseminationAckSender.FEATURE_PATH_TO_URN, validURN);
        validNotificationRequest.setPayload(validFeature);

        // Run plugin
        Collection<NotificationRequest> requests = Lists.newArrayList(invalidNotificationRequest,
                                                                      validNotificationRequest);
        Collection<NotificationRequest> requestError = plugin.send(requests);
        Assert.assertEquals("should return one error", 1, requestError.size());

        Mockito.verify(publisher, Mockito.times(1))
               .broadcastAll(exchangeNameCaptor.capture(),
                             queueNameCaptor.capture(),
                             routingKeyCaptor.capture(),
                             dlkCaptor.capture(),
                             priorityCaptor.capture(),
                             messagesCaptor.capture(),
                             headersCaptor.capture());
        Assert.assertEquals("should retrieve good exchange",
                            featureDisseminationExchange,
                            exchangeNameCaptor.getValue());
        Assert.assertEquals("should retrieve good queue name",
                            Optional.of(featureQueueName),
                            queueNameCaptor.getValue());
        Assert.assertFalse("should not override routing key", routingKeyCaptor.getValue().isPresent());
        Assert.assertFalse("should not override DLK", dlkCaptor.getValue().isPresent());
        Assert.assertEquals("should retrieve default priority", Integer.valueOf(0), priorityCaptor.getValue());
        Assert.assertEquals("should provide 1 header", 1, headersCaptor.getValue().size());
        Assert.assertEquals("should override tenant",
                            "PROJECT1",
                            headersCaptor.getValue().get(AmqpConstants.REGARDS_TENANT_HEADER));
        Assert.assertFalse("should send a message", messagesCaptor.getValue().isEmpty());
        fr.cnes.regards.modules.feature.dto.event.in.DisseminationAckEvent event = messagesCaptor.getValue()
                                                                                                 .stream()
                                                                                                 .findFirst()
                                                                                                 .get();
        Assert.assertEquals("should get the correct recipient label (current tenant)",
                            senderLabel,
                            event.getRecipientLabel());
        Assert.assertEquals("should send a message", validURN, event.getUrn());
    }

    @Test
    public void testAipDisseminationAckSender() throws NotAvailablePluginConfigurationException {
        Mockito.clearInvocations(publisher);

        PluginUtils.setup();
        String aipDisseminationExchange = "aipDisseminationExchange";
        String senderLabel = "senderLabel";
        String aipQueueName = "aipQueueName";

        // Plugin parameters
        Set<IPluginParam> parameters = IPluginParam.set(IPluginParam.build(DisseminationAckSender.AIP_EXCHANGE_PARAM_NAME,
                                                                           aipDisseminationExchange),
                                                        IPluginParam.build(DisseminationAckSender.SENDER_LABEL_PARAM_NAME,
                                                                           senderLabel),
                                                        IPluginParam.build(DisseminationAckSender.AIP_QUEUE_PARAM_NAME,
                                                                           aipQueueName));

        // Instantiate plugin
        IRecipientNotifier plugin = PluginUtils.getPlugin(PluginConfiguration.build(DisseminationAckSender.class,
                                                                                    UUID.randomUUID().toString(),
                                                                                    parameters),
                                                          new ConcurrentHashMap<>());
        Assert.assertNotNull(plugin);

        // sample requests
        NotificationRequest validNotificationRequest = new NotificationRequest();
        JsonObject validAip = new JsonObject();
        String validURN = "URN:AIP:DATA:PROJECT2:00000000-0000-0000-0000-000169267448:V1";
        validAip.addProperty(DisseminationAckSender.FEATURE_PATH_TO_URN, validURN);
        validNotificationRequest.setPayload(validAip);

        // Run plugin
        Collection<NotificationRequest> requests = Lists.newArrayList(validNotificationRequest);
        Collection<NotificationRequest> requestError = plugin.send(requests);
        Assert.assertEquals("should return one error", 0, requestError.size());

        Mockito.verify(publisher, Mockito.times(1))
               .broadcastAll(exchangeNameCaptor.capture(),
                             queueNameCaptor.capture(),
                             routingKeyCaptor.capture(),
                             dlkCaptor.capture(),
                             priorityCaptor.capture(),
                             oaisMessagesCaptor.capture(),
                             headersCaptor.capture());
        Assert.assertEquals("should retrieve good exchange", aipDisseminationExchange, exchangeNameCaptor.getValue());
        Assert.assertEquals("should retrieve good queue name", Optional.of(aipQueueName), queueNameCaptor.getValue());
        Assert.assertFalse("should not override routing key", routingKeyCaptor.getValue().isPresent());
        Assert.assertFalse("should not override DLK", dlkCaptor.getValue().isPresent());
        Assert.assertEquals("should retrieve default priority", Integer.valueOf(0), priorityCaptor.getValue());
        Assert.assertEquals("should provide 1 header", 1, headersCaptor.getValue().size());
        Assert.assertFalse("should send a message", oaisMessagesCaptor.getValue().isEmpty());
        Assert.assertEquals("should override tenant",
                            "PROJECT2",
                            headersCaptor.getValue().get(AmqpConstants.REGARDS_TENANT_HEADER));
        fr.cnes.regards.modules.ingest.dto.request.event.DisseminationAckEvent event = oaisMessagesCaptor.getValue()
                                                                                                         .stream()
                                                                                                         .findFirst()
                                                                                                         .get();
        Assert.assertEquals("should retrieve good urn", validURN, event.getUrn());
        Assert.assertEquals("should retrieve good label", senderLabel, event.getRecipientLabel());
    }
}
