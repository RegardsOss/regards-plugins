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

import fr.cnes.regards.framework.amqp.ISubscriber;
import fr.cnes.regards.framework.amqp.batch.IBatchHandler;
import fr.cnes.regards.modules.test.plugins.batchretry.dto.ResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;
import org.springframework.validation.Errors;

import java.util.List;

/**
 * Handler for reponse messages
 *
 * @author Olivier Rousselot
 */
@Service
public class ResponseMessageHandler implements ApplicationListener<ApplicationReadyEvent>, IBatchHandler<ResponseDto> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResponseMessageHandler.class);

    private ISubscriber subscriber;

    private int badMessageFormatCount = 0;

    private int invalidMessagesCount = 0;

    private int okMessagesCount = 0;

    public ResponseMessageHandler(ISubscriber subscriber) {
        this.subscriber = subscriber;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        subscriber.subscribeTo(ResponseDto.class, this);
    }

    @Override
    public Errors validate(ResponseDto message) {
        return null;
    }

    @Override
    public void handleBatch(List<ResponseDto> messages) {
        LOGGER.info("Receive ResponseMessage : " + messages);
        badMessageFormatCount += (int) messages.stream()
                                               .filter(msg -> msg.status() == ResponseDto.Status.BAD_FORMAT)
                                               .count();
        invalidMessagesCount += (int) messages.stream()
                                              .filter(msg -> msg.status() == ResponseDto.Status.INVALID)
                                              .count();
        okMessagesCount += (int) messages.stream().filter(msg -> msg.status() == ResponseDto.Status.OK).count();
    }

    @Override
    public boolean isRetryEnabled() {
        return false;
    }

    @Override
    public boolean isDedicatedDLQEnabled() {
        return false;
    }

    public int getBadMessageFormatCount() {
        return badMessageFormatCount;
    }

    public int getInvalidMessagesCount() {
        return invalidMessagesCount;
    }

    public int getOkMessagesCount() {
        return okMessagesCount;
    }

    public void clear() {
        badMessageFormatCount = 0;
        invalidMessagesCount = 0;
        okMessagesCount = 0;
    }
}
