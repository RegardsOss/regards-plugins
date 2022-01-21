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
package fr.cnes.regards.modules.crawler.service.ds.plugin;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import fr.cnes.regards.db.datasources.plugins.common.AbstractDataSourcePlugin;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.oais.urn.OAISIdentifier;
import fr.cnes.regards.framework.oais.urn.OaisUniformResourceName;
import fr.cnes.regards.framework.urn.EntityType;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.DataSourceException;
import fr.cnes.regards.modules.dam.domain.datasources.plugins.IDataSourcePlugin;
import fr.cnes.regards.modules.dam.domain.entities.feature.DataObjectFeature;
import fr.cnes.regards.modules.model.dto.properties.IProperty;

/**
 * @author oroussel
 */
@Plugin(id = "test-datasource", version = "1.0-SNAPSHOT", description = "Allows invalid data extraction from nothing",
        author = "REGARDS Team", contact = "regards@c-s.fr", license = "GPLv3", owner = "CSSI",
        url = "https://github.com/RegardsOss")
public class TestDsPlugin extends AbstractDataSourcePlugin implements IDataSourcePlugin {

    @Override
    public int getRefreshRate() {
        return 1000000;
    }

    @Override
    public Page<DataObjectFeature> findAll(String tenant, Pageable pageable, OffsetDateTime date)
            throws DataSourceException {
        List<DataObjectFeature> list = new ArrayList<>();
        DataObjectFeature o = new DataObjectFeature(
                OaisUniformResourceName.pseudoRandomUrn(OAISIdentifier.AIP, EntityType.DATA, tenant, 1), "DO1", "");
        // toto isn't expected by the model
        o.addProperty(IProperty.buildString("toto", "texte"));
        // tutu.titi isn't expected by the model
        // tutu.toto is expected as mandatory
        o.addProperty(IProperty.buildObject("tutu", IProperty.buildString("titi", "texte"),
                                            IProperty.buildString("toto", "texte")));
        list.add(o);
        return new PageImpl<>(list);
    }
}
