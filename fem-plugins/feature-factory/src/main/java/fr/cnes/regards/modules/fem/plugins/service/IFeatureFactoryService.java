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
package fr.cnes.regards.modules.fem.plugins.service;

import fr.cnes.regards.modules.feature.domain.request.FeatureReferenceRequest;
import fr.cnes.regards.modules.feature.dto.Feature;

/**
 * Create a {@link Feature} from a {@link FeatureReferenceRequest}
 * We will use the file name to extract {@link Feature} metadata
 * @author kevin
 *
 */
public interface IFeatureFactoryService {

    /**
    * Create a {@link Feature} from a {@link FeatureReferenceRequest}
    * We will use the file name to extract {@link Feature} metadata according the right patter of file naming
    * @param reference {@link FeatureReferenceRequest} contain file name to extract metadata
    * @return the created {@link Feature}
    */
    public Feature createFeature(FeatureReferenceRequest reference);

}