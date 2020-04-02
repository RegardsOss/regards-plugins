/*
 * Copyright 2017-2019 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.catalog.femdriver.service;

import java.util.List;
import java.util.Map;

import org.apache.commons.compress.utils.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import fr.cnes.regards.framework.jpa.multitenant.transactional.MultitenantTransactional;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.modules.catalog.services.helper.IServiceHelper;
import fr.cnes.regards.modules.dam.domain.entities.DataObject;
import fr.cnes.regards.modules.feature.client.FeatureClient;
import fr.cnes.regards.modules.feature.dto.Feature;
import fr.cnes.regards.modules.model.dto.properties.IProperty;
import fr.cnes.regards.modules.search.domain.SearchRequest;

/**
 * @author sbinda
 *
 */
@Service
@MultitenantTransactional
public class FemDriverService {

    @Autowired
    private IServiceHelper serviceHelper;

    @Autowired
    private FeatureClient featureClient;

    // TODO :  A faire dans un job
    public void update(SearchRequest searchRequest, Map<String, IProperty<?>> propertyMap) throws ModuleException {
        Page<DataObject> results = serviceHelper.getDataObjects(searchRequest, 0, 1000);
        List<Feature> features = Lists.newArrayList();
        for (DataObject dobj : results.getContent()) {
            Feature feature = Feature.build(dobj.getIpId().toString(), null, null, null, null);
            for (IProperty<?> prop : propertyMap.values()) {
                feature.addProperty(prop);
            }
            features.add(feature);
        }
        featureClient.updateFeatures(features);
    }

}
