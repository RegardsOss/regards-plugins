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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.cnes.regards.framework.amqp.IPublisher;
import fr.cnes.regards.framework.amqp.event.AbstractRequestEvent;
import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.framework.modules.plugins.domain.parameter.IPluginParam;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.utils.plugins.PluginUtils;
import fr.cnes.regards.framework.utils.plugins.exception.NotAvailablePluginConfigurationException;
import fr.cnes.regards.modules.notifier.domain.NotificationRequest;
import fr.cnes.regards.modules.notifier.domain.plugin.IRecipientNotifier;
import fr.cnes.regards.modules.notifier.dto.out.NotificationState;
import fr.cnes.regards.modules.notifier.utils.SessionUtils;
import fr.cnes.regards.modules.workermanager.amqp.events.EventHeadersHelper;
import fr.cnes.regards.modules.workermanager.amqp.events.RawMessageBuilder;
import org.assertj.core.util.Sets;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
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

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test class for {@link WorkerManagerSender}
 *
 * @author Iliana Ghazali
 **/
@RunWith(SpringRunner.class)
@ActiveProfiles(value = { "test", "noscheduler" })
@ContextConfiguration(classes = { WorkerManagerSenderIT.ScanningConfiguration.class })
@EnableAutoConfiguration(exclude = { JpaRepositoriesAutoConfiguration.class, FlywayAutoConfiguration.class })
@PropertySource({ "classpath:amqp.properties", "classpath:cloud.properties" })
@TestPropertySource(properties = { "regards.amqp.enabled=true",
                                   "spring.application.name=rs-test",
                                   "regards.cipher.iv=1234567812345678",
                                   "regards.cipher.keyLocation=src/test/resources/testKey" })
public class WorkerManagerSenderIT {

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

    @Autowired
    private IPublisher publisher;

    @Captor
    private ArgumentCaptor<Collection<Message>> messagesCaptor;

    private final List<NotificationRequest> notificationRequests = Lists.newArrayList();

    private static final String TENANT = "TENANT_WM";

    private static final String EXCHANGE = "worker.manager.exchange";

    private static final String QUEUE = "worker.manager.queue";

    private static final String RECIPIENT_LABEL = "recipientLabel";

    private static final String CONTENT_TYPE = "feature";

    private static final boolean ACK_REQUIRED = true;

    private static final String CONTENT_TYPE_JSON_PATH = "feature.properties.data.type";

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkerManagerSender.class);

    @Before
    public void init() {
        PluginUtils.setup();
        initNotificationRequests();
        Mockito.clearInvocations(publisher);
    }

    @Test
    public void testWorkerManagerSender_withSessionNameOverridden() throws NotAvailablePluginConfigurationException {
        // GIVEN
        // Init worker manager sender plugin
        IRecipientNotifier workerMngPlugin = initWorkerSenderPluginWithSessionPattern("{"
                                                                                      + CONTENT_TYPE_JSON_PATH
                                                                                      + "}-#day-swot");

        // WHEN
        // run plugin
        workerMngPlugin.send(notificationRequests);
        Mockito.verify(publisher, Mockito.times(1))
               .broadcastAll(Mockito.anyString(),
                             Mockito.any(),
                             Mockito.any(),
                             Mockito.any(),
                             Mockito.anyInt(),
                             messagesCaptor.capture(),
                             Mockito.any());
        // THEN
        checkNotificationsSent(true, false);
    }

    @Test
    public void testWorkerManagerSender_withSessionNameOverriddenError()
        throws NotAvailablePluginConfigurationException {
        // GIVEN
        // Init worker manager sender plugin
        IRecipientNotifier workerMngPlugin = initWorkerSenderPluginWithSessionPattern("{path.not.existing}-#day");

        // WHEN
        // run plugin
        workerMngPlugin.send(notificationRequests);
        Mockito.verify(publisher, Mockito.times(1))
               .broadcastAll(Mockito.anyString(),
                             Mockito.any(),
                             Mockito.any(),
                             Mockito.any(),
                             Mockito.anyInt(),
                             messagesCaptor.capture(),
                             Mockito.any());

        // the sessionName pattern is invalid, check that the default session name is set in that case
        checkNotificationsSent(true, true);
    }

    @Test
    public void testWorkerManagerSender_withSameSessionName() throws NotAvailablePluginConfigurationException {
        // GIVEN
        // Init worker manager sender plugin
        IRecipientNotifier workerMngPlugin = initWorkerSenderPluginWithoutSessionPattern();

        // WHEN
        // run plugin
        workerMngPlugin.send(notificationRequests);
        Mockito.verify(publisher, Mockito.times(1))
               .broadcastAll(Mockito.anyString(),
                             Mockito.any(),
                             Mockito.any(),
                             Mockito.any(),
                             Mockito.anyInt(),
                             messagesCaptor.capture(),
                             Mockito.any());

        // the sessionName pattern is invalid, check that the default session name is set in that case
        checkNotificationsSent(false, false);
    }

    // TEST UTILS

    public void checkNotificationsSent(boolean overriddenSessionName, boolean errorSessionName) {
        // THEN
        // check that X events were sent corresponding to X notification request events
        List<Message> messagesSent = (List<Message>) messagesCaptor.getValue();
        Assert.assertEquals(String.format("%d messages should have been published", notificationRequests.size()),
                            notificationRequests.size(),
                            messagesSent.size());
        // check messages content match, especially sessionName, which should follow sessionNamePattern
        List<Message> expectedMessages = Lists.newArrayList();
        int count = 0;
        for (NotificationRequest notificationRequest : notificationRequests) {
            String reqPayloadStr = notificationRequest.getPayload().toString();
            String currentDate = OffsetDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            String sourceName;
            String sessionName;
            if (overriddenSessionName) {
                sourceName = SessionUtils.DEFAULT_SESSION_OWNER_TOKEN + TENANT;
                sessionName = errorSessionName ?
                    String.format("sessionNamePatternError-%s", currentDate) :
                    String.format("L0A_LR_Packet-%s-swot", currentDate);
            } else {
                sourceName = "testSessionOwner";
                sessionName = "testSession";
            }
            expectedMessages.add(RawMessageBuilder.build(TENANT,
                                                         CONTENT_TYPE,
                                                         sourceName,
                                                         sessionName,
                                                         messagesSent.get(count)
                                                                     .getMessageProperties()
                                                                     .getHeader(EventHeadersHelper.REQUEST_ID_HEADER),
                                                         reqPayloadStr.getBytes(StandardCharsets.UTF_8)));
            count++;
        }
        Assert.assertEquals("Unexpected messages sent", expectedMessages, messagesSent);
        LOGGER.info("Messages successfully sent {}", messagesSent);
    }

    private IRecipientNotifier initWorkerSenderPluginWithSessionPattern(String sessionNamePattern)
        throws NotAvailablePluginConfigurationException {
        Set<IPluginParam> parameters = initCommonPluginParams();
        parameters.add(IPluginParam.build(WorkerManagerSender.WS_SESSION_NAME_PATTERN_NAME, sessionNamePattern));
        IRecipientNotifier workerMngPlugin = PluginUtils.getPlugin(PluginConfiguration.build(WorkerManagerSender.class,
                                                                                             UUID.randomUUID()
                                                                                                 .toString(),
                                                                                             parameters),
                                                                   new ConcurrentHashMap<>());
        Assert.assertNotNull(workerMngPlugin);
        return workerMngPlugin;
    }

    private IRecipientNotifier initWorkerSenderPluginWithoutSessionPattern()
        throws NotAvailablePluginConfigurationException {
        Set<IPluginParam> parameters = initCommonPluginParams();
        IRecipientNotifier workerMngPlugin = PluginUtils.getPlugin(PluginConfiguration.build(WorkerManagerSender.class,
                                                                                             UUID.randomUUID()
                                                                                                 .toString(),
                                                                                             parameters),
                                                                   new ConcurrentHashMap<>());
        Assert.assertNotNull(workerMngPlugin);
        return workerMngPlugin;
    }

    private Set<IPluginParam> initCommonPluginParams() {
        Set<IPluginParam> parameters = Sets.newHashSet();
        parameters.add(IPluginParam.build(WorkerManagerSender.EXCHANGE_PARAM_NAME, EXCHANGE));
        parameters.add(IPluginParam.build(WorkerManagerSender.QUEUE_PARAM_NAME, QUEUE));
        parameters.add(IPluginParam.build(WorkerManagerSender.RECIPIENT_LABEL_PARAM_NAME, RECIPIENT_LABEL));
        parameters.add(IPluginParam.build(WorkerManagerSender.WS_CONTENT_TYPE_NAME, CONTENT_TYPE));
        parameters.add(IPluginParam.build(WorkerManagerSender.ACK_REQUIRED_PARAM_NAME, ACK_REQUIRED));
        return parameters;
    }

    private void initNotificationRequests() {
        JsonParser jsonParser = new JsonParser();
        Set<JsonObject> featuresSamples = Sets.newHashSet();
        featuresSamples.add(jsonParser.parse("{\"feature\": {\"id\": \"TEST:2020\", \"type\": \"Feature\","
                                             + "\"entityType\": \"DATA\", \"model\": \"SWOT0001\", \"properties\": {\"data\": {"
                                             + "\"type\": \"L0A_LR_Packet\"}}}}").getAsJsonObject());
        featuresSamples.add(jsonParser.parse("{\"feature\": {\"id\": \"TEST:2021\", \"type\": \"Feature\","
                                             + "\"entityType\": \"DATA\", \"model\": \"SWOT0001\", \"properties\": {\"data\": {"
                                             + "\"type\": \"L0A_LR_Packet\"}}}}").getAsJsonObject());
        featuresSamples.add(jsonParser.parse("{\"feature\": {\"id\": \"TEST:2022\", \"type\": \"Feature\","
                                             + "\"entityType\": \"DATA\", \"model\": \"SWOT0001\", \"properties\": {\"data\": {"
                                             + "\"type\": \"L0A_LR_Packet\"}}}}").getAsJsonObject());

        JsonElement metadata = jsonParser.parse("{\""
                                                + SessionUtils.SESSION_OWNER_METADATA_PATH
                                                + "\":\"testSessionOwner"
                                                + "\",\""
                                                + SessionUtils.SESSION_METADATA_PATH
                                                + "\":\"testSession\"}");
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