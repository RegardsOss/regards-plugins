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

import java.util.HashMap;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;

import com.google.gson.JsonElement;

import fr.cnes.regards.framework.amqp.IPublisher;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.modules.notifier.plugin.IRecipientNotifier;

/**
 * Sender to send CHRONOS formatted feature notification
 *
 * @author Sébastien Binda
 *
 */
@Plugin(author = "REGARDS Team", description = "Sender to send CHRONOS formatted feature notification",
        id = ChronosRecipientSender.PLUGIN_ID, version = "1.0.0", contact = "regards@c-s.fr", license = "GPLv3",
        owner = "CNES", url = "https://regardsoss.github.io/")
public class ChronosRecipientSender implements IRecipientNotifier {

    public static final String PLUGIN_ID = "ChronosRecipientSender";

    @Autowired
    private IPublisher publisher;

    @PluginParameter(label = "RabbitMQ exchange name", name = "exchange")
    private String exchange;

    @PluginParameter(label = "RabbitMQ queue name", name = "queueName", optional = true)
    private String queueName;

    @PluginParameter(label = "Feature created_by property path", name = "createdByPropertyPath", optional = true,
            defaultValue = "history.created_by")
    private String createdByPropertyPath;

    @PluginParameter(label = "Feature updated_by property path", name = "updatedByPropertyPath", optional = true,
            defaultValue = "history.updated_by")
    private String updatedByPropertyPath;

    @PluginParameter(label = "Feature updated_by property path", name = "gpfsUrlPropertyPath", optional = true,
            defaultValue = "properties.system.gpfs_url")
    private String gpfsUrlPropertyPath;

    @Override
    public boolean send(JsonElement element, String action) {
        String createdBy = getValue(element, createdByPropertyPath).orElse(null);
        String updatedBy = getValue(element, updatedByPropertyPath).orElse(null);
        String uri = getValue(element, gpfsUrlPropertyPath).orElse(null);
        this.publisher.broadcast(exchange, Optional.ofNullable(queueName), 0,
                                 ChronosNotificationEvent.build(action, createdBy, updatedBy, uri), new HashMap<>());
        return true;
    }

    public Optional<String> getValue(JsonElement element, String key) {
        if (key.contains(".")) {
            String[] paths = key.split("\\.");
            JsonElement obj = element.getAsJsonObject().get(paths[0]);
            if (obj != null) {
                String newKey = key.substring(key.indexOf(".") + 1, key.length());
                return this.getValue(obj, newKey);
            }
        } else {
            JsonElement obj = element.getAsJsonObject().get(key);
            if (obj != null) {
                return Optional.of(obj.toString());
            }
        }
        return Optional.empty();
    }
}
