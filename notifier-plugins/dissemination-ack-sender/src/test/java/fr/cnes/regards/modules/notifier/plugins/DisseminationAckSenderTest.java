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

import com.google.common.collect.Lists;
import com.google.gson.JsonObject;
import fr.cnes.regards.common.notifier.plugins.AbstractRabbitMQSender;
import fr.cnes.regards.framework.amqp.IPublisher;
import fr.cnes.regards.framework.amqp.configuration.AmqpConstants;
import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.framework.modules.plugins.domain.parameter.IPluginParam;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.utils.plugins.PluginUtils;
import fr.cnes.regards.framework.utils.plugins.exception.NotAvailablePluginConfigurationException;
import fr.cnes.regards.modules.feature.dto.event.in.DisseminationAckEvent;
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

/**
 * @author Léo Mieulet
 */
@RunWith(SpringRunner.class)
@ActiveProfiles(value = { "test", "noscheduler" })
@ContextConfiguration(classes = { DisseminationAckSenderTest.ScanningConfiguration.class })
@EnableAutoConfiguration(exclude = { JpaRepositoriesAutoConfiguration.class, FlywayAutoConfiguration.class })
@PropertySource({ "classpath:amqp.properties", "classpath:cloud.properties" })
@TestPropertySource(
    properties = { "regards.amqp.enabled=true", "spring.application.name=rs-test", "regards.cipher.iv=1234567812345678",
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
    private ArgumentCaptor<Collection<?>> messagesCaptor;

    @Captor
    private ArgumentCaptor<Map<String, Object>> headersCaptor;

    @Test
    public void testDisseminationAckSender() throws NotAvailablePluginConfigurationException {
        Mockito.clearInvocations(publisher);

        PluginUtils.setup();
        String exchange = "exchange";
        String queueName = "queueName";
        String recipientLabel = "recipientLabel";
        String recipientTenant = "recipientTenant";
        String senderLabelName = "sender";
        // Plugin parameters
        Set<IPluginParam> parameters = IPluginParam.set(IPluginParam.build(AbstractRabbitMQSender.EXCHANGE_PARAM_NAME,
                                                                           exchange),
                                                        IPluginParam.build(AbstractRabbitMQSender.QUEUE_PARAM_NAME,
                                                                           queueName),
                                                        IPluginParam.build(AbstractRabbitMQSender.RECIPIENT_LABEL_PARAM_NAME,
                                                                           recipientLabel),
                                                        IPluginParam.build(DisseminationAckSender.SENDER_LABEL_PARAM_NAME,
                                                                           senderLabelName),
                                                        IPluginParam.build(DisseminationAckSender.RECIPIENT_TENANT_PARAM_NAME,
                                                                           recipientTenant));

        // Instantiate plugin
        IRecipientNotifier plugin = PluginUtils.getPlugin(PluginConfiguration.build(DisseminationAckSender.class,
                                                                                    UUID.randomUUID().toString(),
                                                                                    parameters), new HashMap<>());
        Assert.assertNotNull(plugin);

        // sample requests
        NotificationRequest invalidNotificationRequest = new NotificationRequest();
        JsonObject invalidFeature = new JsonObject();
        invalidFeature.addProperty(DisseminationAckSender.FEATURE_PATH_TO_URN, "invalid_urn");
        invalidNotificationRequest.setPayload(invalidFeature);

        NotificationRequest validNotificationRequest = new NotificationRequest();
        JsonObject validFeature = new JsonObject();
        String validURN = "URN:FEATURE:DATA:PROJECT:00000000-0000-0000-0000-000169267448:V1";
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
        Assert.assertEquals("should retrieve good exchange", exchange, exchangeNameCaptor.getValue());
        Assert.assertEquals("should retrieve good queue name", Optional.of(queueName), queueNameCaptor.getValue());
        Assert.assertFalse("should not override routing key", routingKeyCaptor.getValue().isPresent());
        Assert.assertFalse("should not override DLK", dlkCaptor.getValue().isPresent());
        Assert.assertEquals("should retrieve default priority", Integer.valueOf(0), priorityCaptor.getValue());
        Assert.assertEquals("should provide 1 header", 1, headersCaptor.getValue().size());
        Assert.assertEquals("should override tenant",
                            recipientTenant,
                            headersCaptor.getValue().get(AmqpConstants.REGARDS_TENANT_HEADER));

        Assert.assertFalse("should send a message", messagesCaptor.getValue().isEmpty());
        DisseminationAckEvent event = (DisseminationAckEvent) messagesCaptor.getValue().stream().findFirst().get();
        Assert.assertEquals("should get the correct recipient label (current tenant)",
                            senderLabelName,
                            event.getRecipientLabel());
        Assert.assertEquals("should send a message", validURN, event.getUrn());
    }
}
