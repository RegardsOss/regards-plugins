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

import com.google.common.collect.Sets;
import com.google.common.io.CharStreams;
import com.google.gson.Gson;
import fr.cnes.regards.framework.amqp.IPublisher;
import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.framework.modules.plugins.domain.parameter.IPluginParam;
import fr.cnes.regards.framework.utils.plugins.PluginUtils;
import fr.cnes.regards.framework.utils.plugins.exception.NotAvailablePluginConfigurationException;
import fr.cnes.regards.modules.notifier.domain.NotificationRequest;
import fr.cnes.regards.modules.notifier.domain.plugin.IRecipientNotifier;
import fr.cnes.regards.modules.notifier.dto.in.NotificationRequestEvent;
import fr.cnes.regards.modules.notifier.dto.out.NotificationState;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.*;

import static fr.cnes.regards.modules.notifier.plugins.ChronosRecipientSender.ACTION_KEY;
import static fr.cnes.regards.modules.notifier.plugins.ChronosRecipientSender.OWNER_KEY;

@RunWith(SpringRunner.class)
@ActiveProfiles(value = { "test", "noscheduler" })
@ContextConfiguration(classes = { ChronosRecipientSenderTest.ScanningConfiguration.class })
@EnableAutoConfiguration(exclude = { JpaRepositoriesAutoConfiguration.class, FlywayAutoConfiguration.class })
@PropertySource({ "classpath:amqp.properties", "classpath:cloud.properties" })
@TestPropertySource(
    properties = { "regards.amqp.enabled=true", "spring.application.name=rs-test", "regards.cipher.iv=1234567812345678",
        "regards.cipher.keyLocation=src/test/resources/testKey" })
public class ChronosRecipientSenderTest {

    @Configuration
    @ComponentScan(basePackages = { "fr.cnes.regards.modules" })
    public static class ScanningConfiguration {

        @Bean
        public IPublisher getPublisher() {
            return Mockito.spy(IPublisher.class);
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ChronosRecipientSenderTest.class);

    private static final String EXCHANGE_PARAMETER = "exchange";

    private static final String QUEUE_NAME_PARAMETER = "queueName";

    private static final String RECIPIENT_LABEL_PARAMETER = "recipientLabel";

    private static final String CREATED_BY_PARAMETER = "history.createdBy";

    private static final String UPDATED_BY_PARAMETER = "history.updatedBy";

    private static final String DELETED_BY_PARAMETER = "history.deletedBy";

    private static final String GPFS_URL_PARAMETER = "properties.system.gpfs_url";

    private static final String LOM_URL_PARAMETER = "properties.system.lom_url";

    private static final String FILENAME_PARAMETER = "properties.system.filename";

    private static final boolean ACK_REQUIRED = true;

    @Autowired
    private Gson gson;

    @Autowired
    private IPublisher publisher;

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
    public void sendTest_with_uri_gpfs_url() throws NotAvailablePluginConfigurationException {
        sendTest(getEvent("input-chronos_with_gpfs_url.json"), false, "file://home/geode/test.tar");

        sendTest(getEvent("input-chronos_with_gpfs_lom_url.json"), false, "file://home/geode/test.tar");
    }

    @Test
    public void sendTest_with_uri_null() throws NotAvailablePluginConfigurationException {
        sendTest(getEvent("input-chronos_with_gpfs_url.json"), true, null);

        sendTest(getEvent("input-chronos_without_gpfs_lom_url.json"), true, null);
        sendTest(getEvent("input-chronos_without_gpfs_lom_url.json"), false, null);
    }

    @Test
    public void sendTest_with_uri_lom_url() throws NotAvailablePluginConfigurationException {
        sendTest(getEvent("input-chronos_with_gpfs_lom_url.json"),
                 true,
                 "http://rs-s3-minio:9000/bucket/854e6e34e4e4e470ac554627b1a0329b");

        sendTest(getEvent("input-chronos_without_gpfs_url.json"),
                 false,
                 "http://rs-s3-minio:9000/bucket/854e6e34e4e4e470ac554627b1a0329b");

        sendTest(getEvent("input-chronos_without_gpfs_url.json"),
                 true,
                 "http://rs-s3-minio:9000/bucket/854e6e34e4e4e470ac554627b1a0329b");
    }

    private void sendTest(NotificationRequestEvent event, boolean notify_only_lom_url, String expectedUri)
        throws NotAvailablePluginConfigurationException {
        // Given
        Mockito.clearInvocations(publisher);

        String actionOwner = "DeletedBy";
        String action = event.getMetadata().getAsJsonObject().get("action").getAsString();
        String filename = event.getPayload()
                               .getAsJsonObject()
                               .get("properties")
                               .getAsJsonObject()
                               .get("system")
                               .getAsJsonObject()
                               .get("filename")
                               .getAsString();

        Map<String, Object> headers = new HashMap<>();
        headers.put(OWNER_KEY, actionOwner);
        headers.put(ACTION_KEY, action);

        // When
        // Instantiate plugin
        IRecipientNotifier sender = createPlugin(notify_only_lom_url);
        Assert.assertNotNull(sender);
        // Run plugin
        sender.send(Sets.newHashSet(new NotificationRequest(event.getPayload(),
                                                            event.getMetadata(),
                                                            event.getRequestId(),
                                                            event.getRequestOwner(),
                                                            event.getRequestDate(),
                                                            NotificationState.SCHEDULED,
                                                            new HashSet<>())));

        // Then
        Mockito.verify(publisher, Mockito.times(1))
               .broadcastAll(exchangeNameCaptor.capture(),
                             queueNameCaptor.capture(),
                             routingKeyCaptor.capture(),
                             dlkCaptor.capture(),
                             priorityCaptor.capture(),
                             messagesCaptor.capture(),
                             headersCaptor.capture());
        Assert.assertEquals("should retrieve good exchange", EXCHANGE_PARAMETER, exchangeNameCaptor.getValue());
        Assert.assertEquals("should retrieve good queue name",
                            Optional.of(QUEUE_NAME_PARAMETER),
                            queueNameCaptor.getValue());
        Assert.assertFalse("should not override routing key", routingKeyCaptor.getValue().isPresent());
        Assert.assertFalse("should not override DLK", dlkCaptor.getValue().isPresent());
        Assert.assertEquals("should retrieve default priority", Integer.valueOf(0), priorityCaptor.getValue());
        Assert.assertEquals("should set action(s) headers", headers, headersCaptor.getValue());

        Assert.assertFalse("should send a message", messagesCaptor.getValue().isEmpty());
        Assert.assertEquals("should send only one message", 1, messagesCaptor.getValue().size());
        Assert.assertTrue("Message send as notification to chronos is not valid",
                          messagesCaptor.getValue()
                                        .contains(new ChronosNotificationEvent(action,
                                                                               actionOwner,
                                                                               expectedUri,
                                                                               filename)));
    }

    private IRecipientNotifier createPlugin(boolean notify_only_lom_url)
        throws NotAvailablePluginConfigurationException {
        PluginUtils.setup();

        String exchangeParameter = "exchange";
        String queueNameParameter = "queueName";
        String recipientLabelParameter = "recipientLabel";
        String createdByParameter = "history.createdBy";
        String updatedByParameter = "history.updatedBy";
        String deletedByParameter = "history.deletedBy";
        String gpfsUrlParameter = "properties.system.gpfs_url";
        String lomUrlParameter = "properties.system.lom_url";
        String filenameParameter = "properties.system.filename";
        boolean ackRequired = true;
        // Plugin parameters
        Set<IPluginParam> parameters = IPluginParam.set(IPluginParam.build(ChronosRecipientSender.EXCHANGE_PARAM_NAME,
                                                                           exchangeParameter),
                                                        IPluginParam.build(ChronosRecipientSender.QUEUE_PARAM_NAME,
                                                                           queueNameParameter),
                                                        IPluginParam.build(ChronosRecipientSender.RECIPIENT_LABEL_PARAM_NAME,
                                                                           recipientLabelParameter),
                                                        IPluginParam.build(ChronosRecipientSender.ACK_REQUIRED_PARAM_NAME,
                                                                           ackRequired),
                                                        IPluginParam.build(ChronosRecipientSender.CREATED_BY_PROPERTY_PATH_PARAM_NAME,
                                                                           createdByParameter),
                                                        IPluginParam.build(ChronosRecipientSender.UPDATED_BY_PROPERTY_PATH_PARAM_NAME,
                                                                           updatedByParameter),
                                                        IPluginParam.build(ChronosRecipientSender.DELETED_BY_PROPERTY_PATH_PARAM_NAME,
                                                                           deletedByParameter),
                                                        IPluginParam.build(ChronosRecipientSender.GPFS_URL_PROPERTY_PATH_PARAM_NAME,
                                                                           gpfsUrlParameter),
                                                        IPluginParam.build(ChronosRecipientSender.LOM_URL_PROPERTY_PATH_PARAM_NAME,
                                                                           lomUrlParameter),
                                                        IPluginParam.build(ChronosRecipientSender.NOTIFY_ONLY_LOM_PARAM_NAME,
                                                                           notify_only_lom_url),
                                                        IPluginParam.build(ChronosRecipientSender.FILENAME_PROPERTY_PATH_PARAM_NAME,
                                                                           filenameParameter));

        return PluginUtils.getPlugin(PluginConfiguration.build(ChronosRecipientSender.class,
                                                               UUID.randomUUID().toString(),
                                                               parameters), new HashMap<>());
    }

    private NotificationRequestEvent getEvent(String name) {
        try (InputStream input = this.getClass().getResourceAsStream(name);
            Reader reader = new InputStreamReader(input)) {
            return gson.fromJson(CharStreams.toString(reader), NotificationRequestEvent.class);
        } catch (IOException e) {
            String errorMessage = "Cannot import event";
            LOGGER.debug(errorMessage);
            throw new AssertionError(errorMessage);
        }
    }

}



