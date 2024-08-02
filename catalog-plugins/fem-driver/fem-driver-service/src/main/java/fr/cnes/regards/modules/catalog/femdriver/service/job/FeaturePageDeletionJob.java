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
package fr.cnes.regards.modules.catalog.femdriver.service.job;

import com.google.gson.reflect.TypeToken;
import fr.cnes.regards.framework.modules.jobs.domain.AbstractJob;
import fr.cnes.regards.framework.modules.jobs.domain.JobParameter;
import fr.cnes.regards.framework.modules.jobs.domain.exception.JobParameterInvalidException;
import fr.cnes.regards.framework.modules.jobs.domain.exception.JobParameterMissingException;
import fr.cnes.regards.framework.modules.jobs.service.IJobInfoService;
import fr.cnes.regards.modules.feature.client.FeatureClient;
import fr.cnes.regards.modules.feature.dto.PriorityLevel;
import fr.cnes.regards.modules.feature.dto.urn.FeatureUniformResourceName;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Marc SORDI
 */
public class FeaturePageDeletionJob extends AbstractJob<Void> {

    public static final String IDS_PARAMETER = "ids";

    private Set<String> ids;

    @Autowired
    private FeatureClient featureClient;

    @Autowired
    private IJobInfoService jobInfoService;

    private String jobOwner;

    @Override
    public void setParameters(Map<String, JobParameter> parameters)
        throws JobParameterMissingException, JobParameterInvalidException {
        Type type = new TypeToken<Set<String>>() {

        }.getType();
        ids = getValue(parameters, IDS_PARAMETER, type);
        jobOwner = jobInfoService.retrieveJob(this.getJobInfoId()).getOwner();
    }

    @Override
    public void run() {

        // Prepare features
        List<FeatureUniformResourceName> features = new ArrayList<>();
        for (String id : ids) {
            try {
                features.add(FeatureUniformResourceName.fromString(id));
            } catch (IllegalArgumentException e) {
                logger.error(
                    "Error trying to delete feature {} from FEM microservice. Feature identifier is not a valid FeatureUniformResourceName. Cause: {}",
                    id,
                    e.getMessage());
            }
        }

        // Delete features
        featureClient.deleteFeatures(jobOwner, features, PriorityLevel.NORMAL);
        logger.info("{} feature deletion requests sended.", features.size());
    }
}
