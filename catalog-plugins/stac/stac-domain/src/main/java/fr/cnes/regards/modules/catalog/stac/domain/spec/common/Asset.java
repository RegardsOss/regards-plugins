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

package fr.cnes.regards.modules.catalog.stac.domain.spec.common;

import com.google.gson.annotations.JsonAdapter;
import fr.cnes.regards.framework.urn.DataType;
import fr.cnes.regards.modules.catalog.stac.domain.api.gson.RoleTypeAdapter;
import fr.cnes.regards.modules.catalog.stac.domain.spec.Item;
import fr.cnes.regards.modules.catalog.stac.domain.spec.extensions.FileInfoExtension;
import io.vavr.collection.Set;

import java.net.URI;

/**
 * An asset is an object that contains a link to data associated with the Item that can be downloaded or streamed.
 * It is allowed to add additional fields.
 * Refer to {@link Item} for more information.
 */
public class Asset extends FileInfoExtension {

    private static final String ROLE_THUMBNAIL = "thumbnail";

    private static final String ROLE_OVERVIEW = "overview";

    private static final String ROLE_DATA = "data";

    private static final String ROLE_METADATA = "metadata";

    URI href;

    String title;

    String description;

    /**
     * Media type of the asset
     */
    String type;

    @JsonAdapter(RoleTypeAdapter.class)
    Set<String> roles;

    public Asset(String checksum,
                 String algorithm,
                 Long size,
                 URI href,
                 String title,
                 String description,
                 String type,
                 Set<String> roles) {
        super(checksum, algorithm, size);
        this.href = href;
        this.title = title;
        this.description = description;
        this.type = type;
        this.roles = roles;
    }

    public static String fromDataType(DataType dataType) {
        return switch (dataType) {
            case QUICKLOOK_HD, QUICKLOOK_MD, QUICKLOOK_SD -> ROLE_OVERVIEW;
            case THUMBNAIL -> ROLE_THUMBNAIL;
            case DOCUMENT, DESCRIPTION -> ROLE_METADATA;
            default -> ROLE_DATA;
        };
    }

    public URI getHref() {
        return href;
    }
}
