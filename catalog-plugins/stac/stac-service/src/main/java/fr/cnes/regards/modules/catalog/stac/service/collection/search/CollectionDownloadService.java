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
package fr.cnes.regards.modules.catalog.stac.service.collection.search;

import fr.cnes.regards.modules.catalog.stac.service.link.DownloadLinkCreator;
import io.vavr.control.Try;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStream;
import java.util.Optional;

/**
 * Prepare mod_zip descriptor file according to passed tiny url
 *
 * @author Marc SORDI
 * @see <a href="https://www.nginx.com/resources/wiki/modules/zip/">Zip NGINX</a>
 */
public interface CollectionDownloadService {

    /**
     * Use standard response output stream
     */
    void prepareDescriptor(OutputStream outputStream, Optional<String> collectionId, final String tinyurl,
            DownloadLinkCreator downloadLinkCreator, boolean onlySample);

    /**
     * Use a non-blocking stream
     */
    Try<StreamingResponseBody> prepareDescriptorAsStream(Optional<String> collectionId, final String tinyurl,
            DownloadLinkCreator downloadLinkCreator, boolean onlySample);

    /**
     * Generate a script according to the given context through tinyurl
     * @param outputStream current output stream for sending file content
     * @param collectionId optional collection id
     * @param tinyurl context (store all criteria)
     */
    void generateEOdagScript(OutputStream outputStream, Optional<String> collectionId, final String tinyurl);
}
