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

import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import fr.cnes.regards.framework.jpa.multitenant.test.AbstractMultitenantServiceTest;
import fr.cnes.regards.modules.feature.domain.request.FeatureReferenceRequest;
import fr.cnes.regards.modules.feature.dto.Feature;

/**
 * @author kevin
 *
 */
public class FeatureFactoryServiceIT extends AbstractMultitenantServiceTest {

    @Autowired
    private IFeatureFactoryService factoryService;

    @Test
    public void testFactory() {
        String[] testFiles = { "SWOT_IVK_20210612T081400_20210612T072103_20210612T080137_O_APID1070.PTM_1",
                "SWOT_L0A_LR_Prime_IVK_20210612T072203_20210612T072500_1070_PGA2_03.nc",
                "SWOT_L0B_LR_Frame_001_005_20210612T072103_20210612T072113_PGA2_03.nc",
                "SWOT_L1B_LR_INTF_001_005_20210612T072103_20210612T072153_PGA2_03.nc",
                "SWOT_L2_LR_PreCalSSH_Basic_001_005_20210612T072103_20210612T072153_PGA2_03.nc",
                "SWOT_L2_LR_PreCalSSH_WindWave_001_005_20210612T072103_20210612T081226_PGA2_03.nc",
                "SWOT_L2_LR_PreCalSSH_Expert_001_005_20210612T072103_20210612T081226_PGA2_03.nc",
                "SWOT_L2_LR_PreCalSSH_Unsmoothed_001_005_20210612T072103_20210612T081226_PGA2_03.nc",
                "SWOT_INT_LR_XOverCal_20210612_PGA2_03.nc",
                "SWOT_L2_LR_SSH_Basic_001_005_20210612T072103_20210612T072153_PGA2_03.nc",
                "SWOT_L2_LR_SSH_WindWave_001_005_20210612T072103_20210612T081226_PGA2_03.nc",
                "SWOT_L2_LR_SSH_Expert_001_005_20210612T072103_20210612T081226_PGA2_03.nc",
                "SWOT_L2_LR_SSH_Unsmoothed_001_005_20210612T072103_20210612T081226_PGA2_03.nc",
                "SWOT_IVK_20210612T081400_20210612T072103_20210612T080137_O_APID1071.PTM_1",
                "SWOT_L0A_HR_Prime_IVK_20210612T072203_20210612T072500_1071_PGA2_03.nc",
                "SWOT_L0B_HR_Frame_001_005_001F_20210612T072103_20210612T072113_PGA2_03.nc",
                "SWOT_L1B_HR_SLC_001_005_001L_20210612T072103_20210612T072153_PGA2_03.nc",
                "SWOT_L2_HR_PIXC_001_005_012R_20210612T072103_20210612T072153_PGA2_03.nc",
                "SWOT_L2_HR_RiverTile_001_005_012R_20210612T072103_20210612T072153_PGA2_03.shp",
                "SWOT_L2_HR_PIXCVecRiver_001_005_012R_20210612T072103_20210612T072154_PGA2_03.nc",
                "SWOT_L2_HR_RiverSP_001_005_1_20210612T072103_20210612T075103_PGA2_03.shp",
                "SWOT_L2_HR_RiverAvg_001_002_20210612T072103_20210612T075103_PGA2_03.shp",
                "SWOT_L2_HR_LakeTile_001_005_012R_20210612T072103_20210612T075103_PGA2_03.shp",
                "SWOT_L2_HR_LakeSP_001_005_1_20210612T072103_20210612T075103_PGA2_03.shp",
                "SWOT_L2_HR_PIXCVec_001_005_012R_20210612T072103_20210612T075103_PGA2_03.nc",
                "SWOT_L2_HR_LakeAvg_001_002_20210612T072103_20210612T075103_PGA2_03.shp",
                "SWOT_L2_HR_Raster_001_005_012F_20210612T072103_20210612T072153_PGA2_03.shp" };

        for (String name : testFiles) {
            FeatureReferenceRequest request = new FeatureReferenceRequest();
            request.setLocation(name);
            Feature feature = factoryService.createFeature(request);
            assertNotNull(String.format("The file name %s should be parsed", name), feature);
        }

    }
}
