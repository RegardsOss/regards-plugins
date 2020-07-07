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

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.google.common.io.CharStreams;
import com.google.gson.Gson;

import fr.cnes.regards.framework.jpa.multitenant.test.AbstractMultitenantServiceTest;
import fr.cnes.regards.modules.notifier.dto.in.NotificationActionEvent;

@TestPropertySource(
        properties = { "spring.jpa.properties.hibernate.default_schema=chronos", "regards.amqp.enabled=true" })
@ActiveProfiles(value = { "testAmqp", "noscheduler" })
public class ChronosRecipientSenderTest extends AbstractMultitenantServiceTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChronosRecipientSenderTest.class);

    @Autowired
    private AutowireCapableBeanFactory beanFactory;

    @Autowired
    private Gson gson;

    @Test
    public void sendTest() {
        ChronosRecipientSender sender = new ChronosRecipientSender();
        beanFactory.autowireBean(sender);
        sender.setExchange("chronos.exchange");
        sender.setQueueName("chronos.queue");
        sender.setCreatedByPropertyPath("history.createdBy");
        sender.setUpdatedByPropertyPath("history.updatedBy");
        sender.setDeletedByPropertyPath("history.deletedBy");
        sender.setGpfsUrlPropertyPath("properties.system.gpfs_url");

        NotificationActionEvent event = getEvent("input-chronos.json");

        sender.send(event.getElement(), event.getAction());
    }

    protected NotificationActionEvent getEvent(String name) {
        try (InputStream input = this.getClass().getResourceAsStream(name);
                Reader reader = new InputStreamReader(input)) {
            return gson.fromJson(CharStreams.toString(reader), NotificationActionEvent.class);
        } catch (IOException e) {
            String errorMessage = "Cannot import event";
            LOGGER.debug(errorMessage);
            throw new AssertionError(errorMessage);
        }
    }
}
