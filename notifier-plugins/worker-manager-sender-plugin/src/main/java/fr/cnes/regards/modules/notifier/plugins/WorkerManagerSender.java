/*
 * Copyright 2017-2025 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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

import fr.cnes.regards.common.notifier.plugins.AbstractWorkerManagerSender;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.modules.notifier.domain.NotificationRequest;

/**
 * The purpose of this plugin is to send worker manager processing requests through rs-notifier microservice.
 *
 * @author Iliana Ghazali
 **/
@Plugin(author = "REGARDS Team",
        description = "The purpose of this plugin is to send worker manager processing requests.",
        id = WorkerManagerSender.PLUGIN_ID,
        version = "1.0.0",
        contact = "regards@c-s.fr",
        license = "GPLv3",
        owner = "CNES",
        url = "https://regardsoss.github.io/")
public class WorkerManagerSender extends AbstractWorkerManagerSender {

    public static final String PLUGIN_ID = "WorkerManagerSender";

    public static final String WS_CONTENT_TYPE_NAME = "contentType";

    @PluginParameter(label = "Type of products processed", name = WS_CONTENT_TYPE_NAME)
    private String contentType;

    /**
     * Content type is defined in plugin configuration
     */
    protected String getContentType(NotificationRequest notificationRequest) {
        return contentType;
    }
}
