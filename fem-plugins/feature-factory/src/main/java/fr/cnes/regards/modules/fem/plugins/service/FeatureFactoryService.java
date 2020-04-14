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

import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.common.collect.ImmutableMap;

import fr.cnes.regards.framework.jpa.multitenant.transactional.MultitenantTransactional;
import fr.cnes.regards.modules.feature.domain.request.FeatureReferenceRequest;
import fr.cnes.regards.modules.feature.dto.Feature;
import fr.cnes.regards.modules.fem.plugins.service.factory.AbstractFeatureFactory;
import fr.cnes.regards.modules.fem.plugins.service.factory.INT_LR_XOverCal;
import fr.cnes.regards.modules.fem.plugins.service.factory.L0A_LR_Packet;
import fr.cnes.regards.modules.fem.plugins.service.factory.L0A_LR_Prime;
import fr.cnes.regards.modules.fem.plugins.service.factory.L0B_HR_Frame;
import fr.cnes.regards.modules.fem.plugins.service.factory.L0B_LR_Frame;
import fr.cnes.regards.modules.fem.plugins.service.factory.L1B_HR_SLC;
import fr.cnes.regards.modules.fem.plugins.service.factory.L2_HR_Raster;
import fr.cnes.regards.modules.fem.plugins.service.factory.L2_HR_RiverAvg;
import fr.cnes.regards.modules.fem.plugins.service.factory.L2_HR_RiverSP;
import fr.cnes.regards.modules.fem.plugins.service.factory.L2_LR_PreCalSSH;

/**
 * @author Kevin Marchois
 *
 */
@Service("featurePluginFactoryService")
@MultitenantTransactional
public class FeatureFactoryService implements IFeatureFactoryService {

    private final Map<String, AbstractFeatureFactory> factoryByRegexMap = ImmutableMap
            .<String, AbstractFeatureFactory> builder()
            .put("SWOT_(\\p{Upper}{3})_([0-9]{8}T[0-9]{6})_([0-9]{8}T[0-9]{6})_([0-9]{8}T[0-9]{6})_(O|T|S)_(APID[0-9]{4})\\.PTM_[0-9]+",
                 new L0A_LR_Packet()) // same factory for L0A_HR_Packet
            .put("SWOT_(L0A_(?:HR|LR|KCAL|RAD|GPSP)_Prime)_(\\p{Upper}{3})_([0-9]{8}T[0-9]{6})_([0-9]{8}T[0-9]{6})_([0-9]{4})_([\\p{Upper}\\p{Digit}]+)_([0-9]+)\\..+",
                 new L0A_LR_Prime()) // same factory for  L0A_HR_Prime
            .put("SWOT_(L0B_(?:HR|LR|KCAL|RAD|GPSP)_Frame)_(\\p{Digit}{3})_(5[0-7][0-9]|58[0-4]|[1-4][0-9][0-9]|0[1-9][0-9]|00[1-9])_([0-9]{8}T[0-9]{6})_([0-9]{8}T[0-9]{6})_([\\p{Upper}\\p{Digit}]+)_([0-9]+)\\..+",
                 new L0B_LR_Frame())
            .put("SWOT_(L1B_(?:HR|LR|KCAL|RAD|GPSP)_INTF)_(\\p{Digit}{3})_(5[0-7][0-9]|58[0-4]|[1-4][0-9][0-9]|0[1-9][0-9]|00[1-9])_([0-9]{8}T[0-9]{6})_([0-9]{8}T[0-9]{6})_([\\p{Upper}\\p{Digit}]+)_([0-9]+)\\..+",
                 new L0B_LR_Frame())// same factory for L1B_LR_INTF and L0B_LR_Frame
            .put("SWOT_(L2_(?:HR|LR|KCAL|RAD|GPSP)_PreCalSSH)_\\p{Alnum}+_(\\p{Digit}{3})_(5[0-7][0-9]|58[0-4]|[1-4][0-9][0-9]|0[1-9][0-9]|00[1-9])_([0-9]{8}T[0-9]{6})_([0-9]{8}T[0-9]{6})_([\\p{Upper}\\p{Digit}]+)_([0-9]+)\\..+",
                 new L2_LR_PreCalSSH())
            .put("SWOT_(INT_(?:HR|LR|KCAL|RAD|GPSP)_XOverCal)_\\p{Digit}{8}_([\\p{Upper}\\p{Digit}]+)_([0-9])+\\..+",
                 new INT_LR_XOverCal())
            .put("SWOT_(L2_(?:HR|LR|KCAL|RAD|GPSP)_SSH)_\\p{Alnum}+_(\\p{Digit}{3})_(5[0-7][0-9]|58[0-4]|[1-4][0-9][0-9]|0[1-9][0-9]|00[1-9])_([0-9]{8}T[0-9]{6})_([0-9]{8}T[0-9]{6})_([\\p{Upper}\\p{Digit}]+)_([0-9]+)\\..+",
                 new L2_LR_PreCalSSH()) // L2_LR_SSH and L2_LR_PreCalSSH need the same factory
            .put("SWOT_(L0B_(?:HR|LR|KCAL|RAD|GPSP)_Frame)_(\\p{Digit}{3})_(5[0-7][0-9]|58[0-4]|[1-4][0-9][0-9]|0[1-9][0-9]|00[1-9])_(30[0-8]|[0-2][0-9][1-9]|001)(F)_([0-9]{8}T[0-9]{6})_([0-9]{8}T[0-9]{6})_([\\p{Upper}\\p{Digit}]+)_([0-9]+)\\..+",
                 new L0B_HR_Frame())
            .put("SWOT_(L1B_(?:HR|LR|KCAL|RAD|GPSP)_SLC)_(\\p{Digit}{3})_(5[0-7][0-9]|58[0-4]|[1-4][0-9][0-9]|0[1-9][0-9]|00[1-9])_(30[0-8]|[0-2][0-9][1-9]|001)(L|R)_([0-9]{8}T[0-9]{6})_([0-9]{8}T[0-9]{6})_([\\p{Upper}\\p{Digit}]+)_([0-9]+)\\..+",
                 new L1B_HR_SLC())
            .put("SWOT_(L2_(?:HR|LR|KCAL|RAD|GPSP)_PIXC)_(\\p{Digit}{3})_(5[0-7][0-9]|58[0-4]|[1-4][0-9][0-9]|0[1-9][0-9]|00[1-9])_(30[0-8]|[0-2][0-9][1-9]|001)(L|R)_([0-9]{8}T[0-9]{6})_([0-9]{8}T[0-9]{6})_([\\p{Upper}\\p{Digit}]+)_([0-9]+)\\..+",
                 new L1B_HR_SLC()) // factory is the same for L2_HR_PIXC and L1B_HR_SLC
            .put("SWOT_(L2_(?:HR|LR|KCAL|RAD|GPSP)_RiverTile)_(\\p{Digit}{3})_(5[0-7][0-9]|58[0-4]|[1-4][0-9][0-9]|0[1-9][0-9]|00[1-9])_(30[0-8]|[0-2][0-9][1-9]|001)(L|R)_([0-9]{8}T[0-9]{6})_([0-9]{8}T[0-9]{6})_([\\p{Upper}\\p{Digit}]+)_([0-9]+)\\..+",
                 new L1B_HR_SLC()) // factory is the same for L2_HR_RiverTile and L1B_HR_SLC
            .put("SWOT_(L2_(?:HR|LR|KCAL|RAD|GPSP)_PIXCVecRiver)_(\\p{Digit}{3})_(5[0-7][0-9]|58[0-4]|[1-4][0-9][0-9]|0[1-9][0-9]|00[1-9])_(30[0-8]|[0-2][0-9][1-9]|001)(L|R)_([0-9]{8}T[0-9]{6})_([0-9]{8}T[0-9]{6})_([\\p{Upper}\\p{Digit}]+)_([0-9]+)\\..+",
                 new L1B_HR_SLC()) // factory is the same for L2_HR_PIXCVecRiver and L1B_HR_SLC
            .put("SWOT_(L2_(?:HR|LR|KCAL|RAD|GPSP)_RiverSP)_(\\p{Digit}{3})_(5[0-7][0-9]|58[0-4]|[1-4][0-9][0-9]|0[1-9][0-9]|00[1-9])_([0-9])_([0-9]{8}T[0-9]{6})_([0-9]{8}T[0-9]{6})_([\\p{Upper}\\p{Digit}]+)_([0-9]+)\\..+",
                 new L2_HR_RiverSP())
            .put("SWOT_(L2_(?:HR|LR|KCAL|RAD|GPSP)_RiverAvg)_(\\p{Digit}{3})_(\\p{Digit}{3})_([0-9]{8}T[0-9]{6})_([0-9]{8}T[0-9]{6})_([\\p{Upper}\\p{Digit}]+)_([0-9]+)\\..+",
                 new L2_HR_RiverAvg())
            .put("SWOT_(L2_(?:HR|LR|KCAL|RAD|GPSP)_LakeTile)_(\\p{Digit}{3})_(5[0-7][0-9]|58[0-4]|[1-4][0-9][0-9]|0[1-9][0-9]|00[1-9])_(30[0-8]|[0-2][0-9][1-9]|001)(L|R)_([0-9]{8}T[0-9]{6})_([0-9]{8}T[0-9]{6})_([\\p{Upper}\\p{Digit}]+)_([0-9]+)\\..+",
                 new L1B_HR_SLC()) // factory is the same for L2_HR_LakeTile and L1B_HR_SLC
            .put("SWOT_(L2_(?:HR|LR|KCAL|RAD|GPSP)_LakeSP)_(\\p{Digit}{3})_(5[0-7][0-9]|58[0-4]|[1-4][0-9][0-9]|0[1-9][0-9]|00[1-9])_([0-9])_([0-9]{8}T[0-9]{6})_([0-9]{8}T[0-9]{6})_([\\p{Upper}\\p{Digit}]+)_([0-9]+)\\..+",
                 new L2_HR_RiverSP()) // factory is the same for L2_HR_LakeSP and L2_HR_RiverSP
            .put("SWOT_(L2_(?:HR|LR|KCAL|RAD|GPSP)_PIXCVec)_(\\p{Digit}{3})_(5[0-7][0-9]|58[0-4]|[1-4][0-9][0-9]|0[1-9][0-9]|00[1-9])_(30[0-8]|[0-2][0-9][1-9]|001)(L|R)_([0-9]{8}T[0-9]{6})_([0-9]{8}T[0-9]{6})_([\\p{Upper}\\p{Digit}]+)_([0-9]+)\\..+",
                 new L1B_HR_SLC()) // factory is the same for L2_HR_PIXCVec and L1B_HR_SLC
            .put("SWOT_(L2_(?:HR|LR|KCAL|RAD|GPSP)_LakeAvg)_(\\p{Digit}{3})_(\\p{Digit}{3})_([0-9]{8}T[0-9]{6})_([0-9]{8}T[0-9]{6})_([\\p{Upper}\\p{Digit}]+)_([0-9]+)\\..+",
                 new L2_HR_RiverAvg()) // factory is the same for L2_HR_LakeAvg and L2_HR_RiverAvg
            .put("SWOT_(L2_(?:HR|LR|KCAL|RAD|GPSP)_Raster)_(\\p{Digit}{3})_(5[0-7][0-9]|58[0-4]|[1-4][0-9][0-9]|0[1-9][0-9]|00[1-9])_(?:15[0-4]|1[0-4][0-9]|0[1-9][0-9]|00[1-9])F_([0-9]{8}T[0-9]{6})_([0-9]{8}T[0-9]{6})_([\\p{Upper}\\p{Digit}]+)_([0-9]+)\\..+",
                 new L2_HR_Raster())
            .build();

    @Value("${regards.plugin.model:GEODE001}")
    private String model;

    @Override
    public Feature createFeature(FeatureReferenceRequest reference) {

        for (Entry<String, AbstractFeatureFactory> entry : factoryByRegexMap.entrySet()) {

            if (Pattern.matches(entry.getKey(), reference.getLocation())) {
                return entry.getValue().createFeature(reference.getLocation(), entry.getKey(), model);
            }
        }
        // TODO que faire si rien ne matche?
        return null;
    }

}
