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
import fr.cnes.regards.common.notifier.plugins.AbstractRabbitMQSender;
import fr.cnes.regards.framework.amqp.IPublisher;
import fr.cnes.regards.framework.amqp.RawMessageEvent;
import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.framework.modules.plugins.dto.parameter.parameter.IPluginParam;
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
@ContextConfiguration(classes = { RabbitMQSenderTest.ScanningConfiguration.class })
@EnableAutoConfiguration(exclude = { JpaRepositoriesAutoConfiguration.class, FlywayAutoConfiguration.class })
@PropertySource({ "classpath:amqp.properties", "classpath:cloud.properties" })
@TestPropertySource(properties = { "regards.amqp.enabled=true",
                                   "spring.application.name=rs-test",
                                   "regards.cipher.iv=1234567812345678",
                                   "regards.cipher.keyLocation=src/test/resources/testKey" })
public class RabbitMQSenderTest {

    @Configuration
    @ComponentScan(basePackages = { "fr.cnes.regards.modules" })
    public static class ScanningConfiguration {

        @Bean
        public IPublisher getPublisher() {
            return Mockito.spy(IPublisher.class);
        }
    }

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
    private ArgumentCaptor<Collection<RawMessageEvent>> messagesCaptor;

    @Captor
    private ArgumentCaptor<Map<String, Object>> headersCaptor;

    @Test
    public void testRabbitMQSender() throws NotAvailablePluginConfigurationException {
        Mockito.clearInvocations(publisher);

        PluginUtils.setup();
        String exchange = "exchange";
        String queueName = "queueName";
        String recipientLabel = "recipientLabel";
        boolean ackRequired = true;
        // Plugin parameters
        Set<IPluginParam> parameters = IPluginParam.set(IPluginParam.build(AbstractRabbitMQSender.EXCHANGE_PARAM_NAME,
                                                                           exchange),
                                                        IPluginParam.build(AbstractRabbitMQSender.QUEUE_PARAM_NAME,
                                                                           queueName),
                                                        IPluginParam.build(AbstractRabbitMQSender.RECIPIENT_LABEL_PARAM_NAME,
                                                                           recipientLabel),
                                                        IPluginParam.build(RabbitMQSender.ACK_REQUIRED_PARAM_NAME,
                                                                           ackRequired));

        // Instantiate plugin
        IRecipientNotifier plugin = PluginUtils.getPlugin(PluginConfiguration.build(RabbitMQSender.class,
                                                                                    UUID.randomUUID().toString(),
                                                                                    parameters),
                                                          new ConcurrentHashMap<>());
        Assert.assertNotNull(plugin);

        // Run plugin
        Collection<NotificationRequest> requests = Lists.newArrayList(new NotificationRequest());
        plugin.send(requests);

        Assert.assertEquals("should retrieve ack", ackRequired, plugin.isAckRequired());
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
        Assert.assertFalse("should send a message", messagesCaptor.getValue().isEmpty());
        Assert.assertTrue("should not override headers", headersCaptor.getValue().isEmpty());
    }
}
