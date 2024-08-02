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

import fr.cnes.regards.framework.modules.jobs.domain.AbstractJob;
import fr.cnes.regards.framework.modules.jobs.domain.JobParameter;
import fr.cnes.regards.framework.modules.jobs.domain.exception.JobParameterInvalidException;
import fr.cnes.regards.framework.modules.jobs.domain.exception.JobParameterMissingException;
import fr.cnes.regards.framework.modules.jobs.service.IJobInfoService;
import fr.cnes.regards.modules.catalog.femdriver.service.FemDriverService;
import fr.cnes.regards.modules.search.dto.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

/**
 * @author sbinda
 */
public class FemDeletionJob extends AbstractJob<Void> {

    public static final String REQUEST_PARAMETER = "req";

    @Autowired
    private IJobInfoService jobService;

    private SearchRequest request;

    private String jobOwner;

    @Autowired
    private FemDriverService femDriverService;

    @Override
    public void setParameters(Map<String, JobParameter> parameters)
        throws JobParameterMissingException, JobParameterInvalidException {
        request = getValue(parameters, REQUEST_PARAMETER, SearchRequest.class);
        jobOwner = jobService.retrieveJob(this.getJobInfoId()).getOwner();
    }

    @Override
    public void run() {
        femDriverService.scheduleDeletionByPage(request, jobOwner);
    }
}
