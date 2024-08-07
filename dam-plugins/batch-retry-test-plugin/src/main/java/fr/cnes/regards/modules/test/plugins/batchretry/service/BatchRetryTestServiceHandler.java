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

import fr.cnes.regards.framework.amqp.IPublisher;
import fr.cnes.regards.framework.amqp.ISubscriber;
import fr.cnes.regards.framework.amqp.batch.IBatchHandler;
import fr.cnes.regards.framework.amqp.batch.dto.BatchMessage;
import fr.cnes.regards.framework.amqp.batch.dto.ResponseMessage;
import fr.cnes.regards.framework.amqp.configuration.IAmqpAdmin;
import fr.cnes.regards.framework.amqp.event.ISubscribable;
import fr.cnes.regards.framework.multitenant.ITenantResolver;
import fr.cnes.regards.modules.test.plugins.batchretry.dto.RequestDto;
import fr.cnes.regards.modules.test.plugins.batchretry.dto.ResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;
import org.springframework.validation.Errors;
import org.springframework.validation.SimpleErrors;

import java.util.List;

/**
 * @author Olivier Rousselot
 */
@Service
public class BatchRetryTestServiceHandler
    implements ApplicationListener<ApplicationReadyEvent>, IBatchHandler<RequestDto> {

    private static final Logger LOGGER = LoggerFactory.getLogger(BatchRetryTestServiceHandler.class);

    private ISubscriber subscriber;

    /**
     * For sending response
     */
    private final IPublisher publisher;

    private final IAmqpAdmin amqpAdmin;

    private final ITenantResolver tenantResolver;

    /**
     * Number of remaining unexpected errors to be thrown (by handleBatch method)
     */
    private int unexpectedErrorsThrownCount = 0;

    public BatchRetryTestServiceHandler(ISubscriber subscriber,
                                        IPublisher publisher,
                                        IAmqpAdmin amqpAdmin,
                                        ITenantResolver tenantResolver) {
        this.subscriber = subscriber;
        this.publisher = publisher;
        this.amqpAdmin = amqpAdmin;
        this.tenantResolver = tenantResolver;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        subscriber.subscribeTo(RequestDto.class, this);
        // Init response exchange
        publisher.initExchange(tenantResolver.getAllActiveTenants(), ResponseDto.class);
    }

    @Override
    public Errors validate(RequestDto message) {
        if (!message.valid()) {
            LOGGER.error("Message invalid (id = {})", message.id());
            Errors errors = new SimpleErrors(message, "message " + message.id());
            errors.rejectValue("id", "invalid");
            return errors;
        }
        return null;
    }

    @Override
    public void handleBatch(List<RequestDto> messages) {
        if (unexpectedErrorsThrownCount == 0) {
            messages.forEach(message -> LOGGER.info("Message {} successfully processed", message.id()));

            publisher.publish(messages.stream().map(msg -> new ResponseDto(msg, ResponseDto.Status.OK)).toList());
        } else {
            unexpectedErrorsThrownCount--;
            throw new RuntimeException("Unexpected error ("
                                       + unexpectedErrorsThrownCount
                                       + " remaining retries to "
                                       + "fail)");
        }
    }

    @Override
    public ResponseMessage<? extends ISubscribable> buildDeniedResponseForInvalidMessage(BatchMessage batchMessage,
                                                                                         String errorMessage) {
        return ResponseMessage.buildResponse(new ResponseDto("Invalid message : " + errorMessage,
                                                             ResponseDto.Status.INVALID));
    }

    @Override
    public ResponseMessage<? extends ISubscribable> buildDeniedResponseForNotConvertedMessage(Message message,
                                                                                              String errorMessage) {
        return ResponseMessage.buildResponse(new ResponseDto("Bad Formatted Message : " + errorMessage,
                                                             ResponseDto.Status.BAD_FORMAT));
    }

    @Override
    public boolean isDedicatedDLQEnabled() {
        return true;
    }

    @Override
    public boolean isRetryEnabled() {
        return true;
    }

    @Override
    public Class<RequestDto> getMType() {
        return RequestDto.class;
    }

    /**
     * Set how many times unexpected errors will be thrown when calling handleBatch
     */
    public BatchRetryTestServiceHandler setUnexpectedErrorsThrownCount(int unexpectedErrorsThrownCount) {
        this.unexpectedErrorsThrownCount = unexpectedErrorsThrownCount;
        return this;
    }

    /**
     * Reset all problems to simulate
     */
    public void clear() {
        // No unexpected errors to be thrown by handleBatch()
        unexpectedErrorsThrownCount = 0;
    }
}
