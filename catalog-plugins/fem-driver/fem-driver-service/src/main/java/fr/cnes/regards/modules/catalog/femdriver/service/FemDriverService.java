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
package fr.cnes.regards.modules.catalog.femdriver.service;

import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Sets;

import fr.cnes.regards.framework.authentication.IAuthenticationResolver;
import fr.cnes.regards.framework.jpa.multitenant.transactional.MultitenantTransactional;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.jobs.domain.JobInfo;
import fr.cnes.regards.framework.modules.jobs.domain.JobParameter;
import fr.cnes.regards.framework.modules.jobs.service.IJobInfoService;
import fr.cnes.regards.modules.catalog.femdriver.dto.FeatureUpdateRequest;
import fr.cnes.regards.modules.catalog.femdriver.service.job.FemUpdateJob;
import fr.cnes.regards.modules.model.dto.properties.IProperty;
import fr.cnes.regards.modules.search.domain.SearchRequest;

/**
 * Business service for FEM Driver
 *
 * @author Sébastien Binda
 *
 */
@Service
@MultitenantTransactional
public class FemDriverService {

    @Autowired
    private IAuthenticationResolver authResolver;

    @Autowired
    private IJobInfoService jobInfoService;

    /**
     * Schedule a job to handle update request.  <br/>
     * An update request is build with : <ul>
     * <li> a {@link SearchRequest} containing the search request parameters to find features to update</li>
     * <li> a Map of {@link IProperty} of each property to update</li>
     * </ul>
     * @param request {@link FeatureUpdateRequest}
     * @return {@link JobInfo} of  scheduled job
     * @throws ModuleException
     */
    public JobInfo scheduleUpdate(FeatureUpdateRequest request) throws ModuleException {
        Set<JobParameter> jobParameters = Sets.newHashSet();
        jobParameters.add(new JobParameter(FemUpdateJob.REQUEST_PARAMETER, request));
        JobInfo jobInfo = new JobInfo(false, 0, jobParameters, authResolver.getUser(), FemUpdateJob.class.getName());
        return jobInfoService.createAsQueued(jobInfo);
    }

}
