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
package fr.cnes.regards.modules.dataprovider.plugins.validation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.collect.Lists;

import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.annotations.Plugin;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginInit;
import fr.cnes.regards.framework.modules.plugins.annotations.PluginParameter;
import fr.cnes.regards.framework.modules.workspace.service.IWorkspaceService;
import fr.cnes.regards.modules.acquisition.plugins.IValidationPlugin;

/**
 * Acquisition plugin to validate files to acquire by executing a custom system command.
 * @author sbinda
 */
@Plugin(id = "custom-command-file-validation", version = "0.4.0",
        description = "Plugin to validate file to acquire by running a custom command", author = "REGARDS Team",
        contact = "regards@c-s.fr", license = "GPLv3", owner = "CSSI", url = "https://github.com/RegardsOss")
public class CustomCommandFileValidation implements IValidationPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomCommandFileValidation.class);

    @PluginParameter(label = "Initialization command",
            description = "An optional command to execute before files validation process.", name = "initCommand",
            optional = true)
    private String initCommand;

    @PluginParameter(label = "File validation command",
            description = "Custom command to execute to valid each file. Full path to file to validate is added at the end of the command.",
            name = "customCommand", optional = false)
    private String customCommand;

    @PluginParameter(label = "Command valid expected result value",
            description = "Expected result value from the command for valid files", name = "expectedCommandResult",
            optional = false)
    private final List<Integer> expectedCommandResults = Lists.newArrayList();

    @PluginParameter(label = "Command timeout in ms", description = "Maximum number of ms to wait for command ends",
            name = "commandTimeout", optional = true, defaultValue = "10000")
    private long commandTimeout;

    @Autowired
    private IWorkspaceService workspaceService;

    @PluginInit
    public void init() {
        if (this.initCommand != null) {
            try {
                Path commandWorkspace = workspaceService.getMicroserviceWorkspace();
                Process p = Runtime.getRuntime().exec(initCommand, null, commandWorkspace.toFile());
                p.waitFor(commandTimeout, TimeUnit.MILLISECONDS);
            } catch (IOException | InterruptedException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }

    @Override
    public boolean validate(Path filePath) throws ModuleException {
        try {
            Path commandWorkspace = workspaceService.getMicroserviceWorkspace();
            Process p = Runtime.getRuntime().exec(String.format("%s %s", customCommand, filePath.toString()), null,
                                                  commandWorkspace.toFile());
            p.waitFor(commandTimeout, TimeUnit.MILLISECONDS);
            if (expectedCommandResults.contains(p.exitValue())) {
                return true;
            } else {
                LOGGER.warn("File '{}' is not valid.", filePath.toString());
                return false;
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return false;
    }

}
