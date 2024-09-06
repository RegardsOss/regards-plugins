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
package fr.cnes.regards.modules.test.plugins.batchretry.service;

import fr.cnes.regards.framework.amqp.configuration.AmqpConstants;
import fr.cnes.regards.framework.amqp.configuration.IAmqpAdmin;
import fr.cnes.regards.framework.amqp.event.Target;
import fr.cnes.regards.framework.jpa.multitenant.test.AbstractMultitenantServiceIT;
import fr.cnes.regards.modules.test.plugins.batchretry.dto.RequestDto;
import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

/**
 * @author Olivier Rousselot
 */
@TestPropertySource(properties = { "spring.jpa.properties.hibernate.default_schema=batch_retry_test_it",
                                   "regards.amqp.enabled=true" }, locations = { "classpath:retry.properties" })
@ActiveProfiles(value = { "testAmqp", "noscheduler", "test" })
public class BatchRetryTestServiceHandlerIT extends AbstractMultitenantServiceIT {

    //    @Autowired
    @SpyBean
    private BatchRetryTestServiceHandler batchRetryTestServiceHandler;

    @Autowired
    private ResponseMessageHandler responseMessageHandler;

    @Autowired
    private IAmqpAdmin amqpAdmin;

    @Before
    public void before() {
        runtimeTenantResolver.forceTenant(getDefaultTenant());
        simulateApplicationStartedEvent();
        simulateApplicationReadyEvent();
        runtimeTenantResolver.forceTenant(getDefaultTenant());
    }

    @Before
    public void beforeEach() {
        batchRetryTestServiceHandler.clear();
        responseMessageHandler.clear();
    }

    @Test
    public void testInvalidMessages() {
        // 5 first messages will be invalid
        for (int i = 1; i <= 20; i++) {
            publisher.publish(new RequestDto(i, i > 5));
        }

        Awaitility.await()
                  .atMost(Duration.ofSeconds(5))
                  .until(() -> responseMessageHandler.getInvalidMessagesCount() == 5);
        Assert.assertEquals(15, responseMessageHandler.getOkMessagesCount());
        Assert.assertEquals(0, responseMessageHandler.getBadMessageFormatCount());
    }

    @Test
    public void testBadFormatMessages() {
        for (int i = 1; i <= 20; i++) {
            if (i <= 15) {
                publisher.publish(new RequestDto(i, true));
            } else {
                Message invalidMessage = new Message("toto".getBytes());
                invalidMessage.getMessageProperties()
                              .setHeader(AmqpConstants.REGARDS_TENANT_HEADER, getDefaultTenant());
                publisher.basicPublish(getDefaultTenant(),
                                       amqpAdmin.getBroadcastExchangeName(RequestDto.class.getName(), Target.ALL),
                                       null,
                                       invalidMessage);
            }
        }

        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> responseMessageHandler.getOkMessagesCount() == 15);
        Assert.assertEquals(15, responseMessageHandler.getOkMessagesCount());
        Assert.assertEquals(5, responseMessageHandler.getBadMessageFormatCount());
    }

    @Test
    public void testUnexpectedMessagesFailing3Times() {
        // 5 first messages will be invalid
        batchRetryTestServiceHandler.setUnexpectedErrorsThrownCount(3);

        for (int i = 1; i <= 20; i++) {
            publisher.publish(new RequestDto(i, true));
        }
        Awaitility.await()
                  .atMost(Duration.ofSeconds(10))
                  .until(() -> responseMessageHandler.getOkMessagesCount() == 20);
        Assert.assertEquals(0, responseMessageHandler.getInvalidMessagesCount());
        Assert.assertEquals(0, responseMessageHandler.getBadMessageFormatCount());
    }

    @Test
    public void testUnexpectedMessagesFailing5TimesAndSoGoToDlq() {
        // 5 first messages will be invalid
        batchRetryTestServiceHandler.setUnexpectedErrorsThrownCount(5);

        for (int i = 1; i <= 20; i++) {
            publisher.publish(new RequestDto(i, true));
        }

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        Assert.assertEquals(0, responseMessageHandler.getOkMessagesCount());
        Assert.assertEquals(0, responseMessageHandler.getInvalidMessagesCount());
        Assert.assertEquals(0, responseMessageHandler.getBadMessageFormatCount());
    }
}
