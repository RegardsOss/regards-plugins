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

import fr.cnes.regards.framework.amqp.event.Event;
import fr.cnes.regards.framework.amqp.event.ISubscribable;
import fr.cnes.regards.framework.amqp.event.JsonMessageConverter;
import fr.cnes.regards.framework.amqp.event.Target;

/**
 *
 * @author Sébastien Binda
 *
 */
@Event(target = Target.ONE_PER_MICROSERVICE_TYPE, converter = JsonMessageConverter.GSON)
public class ChronosNotificationEvent implements ISubscribable {

    private String action;

    private String created_by;

    private String modified_by;

    private String uri;

    public static ChronosNotificationEvent build(String action, String created_by, String updated_by, String uri) {
        ChronosNotificationEvent event = new ChronosNotificationEvent();
        event.action = action;
        event.created_by = created_by;
        event.modified_by = updated_by;
        event.uri = uri;
        return event;
    }

    public String getAction() {
        return action;
    }

    public String getCreatedBy() {
        return created_by;
    }

    public String getModifiedBy() {
        return modified_by;
    }

    public String getUri() {
        return uri;
    }

}
