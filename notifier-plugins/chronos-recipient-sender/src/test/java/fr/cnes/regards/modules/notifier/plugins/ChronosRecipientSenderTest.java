/*
 * Copyright 2017-2020 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import com.google.common.collect.Sets;
import com.google.common.io.CharStreams;
import com.google.gson.Gson;
import fr.cnes.regards.framework.amqp.IPublisher;
import fr.cnes.regards.modules.notifier.domain.NotificationRequest;
import fr.cnes.regards.modules.notifier.dto.in.NotificationRequestEvent;
import fr.cnes.regards.modules.notifier.dto.out.NotificationState;

@RunWith(SpringRunner.class)
@ActiveProfiles(value = { "test", "noscheduler" })
@ContextConfiguration(classes = { ChronosRecipientSenderTest.ScanningConfiguration.class })
@EnableAutoConfiguration(exclude = { JpaRepositoriesAutoConfiguration.class, FlywayAutoConfiguration.class })
@PropertySource({ "classpath:amqp.properties", "classpath:cloud.properties" })
@TestPropertySource(properties = { "regards.amqp.enabled=true", "spring.application.name=rs-test",
        "regards.cipher.iv=1234567812345678", "regards.cipher.keyLocation=src/test/resources/testKey" })
public class ChronosRecipientSenderTest {

    @Configuration
    @ComponentScan(basePackages = { "fr.cnes.regards.modules" })
    public static class ScanningConfiguration {

    }

    public static final String CHRONOS_EXCHANGE = "chronos.exchange";

    public static final String CHRONOS_QUEUE = "chronos.queue";

    private static final Logger LOGGER = LoggerFactory.getLogger(ChronosRecipientSenderTest.class);

    @Autowired
    private Gson gson;

    @Spy
    private IPublisher publisher;

    @Test
    public void sendTest() {
        ChronosRecipientSender sender = new ChronosRecipientSender(publisher);
        sender.setExchange(CHRONOS_EXCHANGE);
        sender.setQueueName(CHRONOS_QUEUE);
        sender.setCreatedByPropertyPath("history.createdBy");
        sender.setUpdatedByPropertyPath("history.updatedBy");
        sender.setDeletedByPropertyPath("history.deletedBy");
        sender.setGpfsUrlPropertyPath("properties.system.gpfs_url");

        NotificationRequestEvent event = getEvent("input-chronos.json");

        sender.send(Sets.newHashSet(new NotificationRequest(event.getPayload(),
                                                            event.getMetadata(),
                                                            event.getRequestId(),
                                                            event.getRequestOwner(),
                                                            event.getRequestDate(),
                                                            NotificationState.SCHEDULED,
                                                            new HashSet<>())));
        String actionOwner = "DeletedBy";
        String action = event.getMetadata().getAsJsonObject().get("action").getAsString();
        Map<String, Object> headers = new HashMap<>();
        headers.put("actionOwner", actionOwner);
        headers.put("action", action);
        ArgumentCaptor<List> listCaptor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(publisher).broadcastAll(Mockito.eq(CHRONOS_EXCHANGE),
                                               Mockito.eq(Optional.of(CHRONOS_QUEUE)),
                                               Mockito.eq(0),
                                               listCaptor.capture(),
                                               Mockito.eq(headers));
        Assert.assertTrue("Message send as notification to chronos is not valid",
                          listCaptor.getValue().contains(ChronosNotificationEvent.build(action,
                                                                                        actionOwner,
                                                                                        "file://home/geode/test.tar")));
        Assert.assertEquals("Message send as notification to chronos is not valid", 1, listCaptor.getValue().size());

    }

    protected NotificationRequestEvent getEvent(String name) {
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



