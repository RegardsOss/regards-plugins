/*
 * Copyright 2017-2021 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.dataprovider.plugins.product;

import fr.cnes.regards.framework.module.rest.exception.ModuleException;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * Static methods to manipulate regexp
 *
 * @author Binda Sébastien
 */
public final class RegexpHelper {

    /**
     * Remove given groups number from file name of filePath given parameter based on compiled regexp pattern.
     *
     * @param filePath path to the file to remove some groups from file name
     * @param pattern  complied pattern as regexp to find groups to remove
     * @param groups   list of groups to remove from fileName matching pattern
     * @return file name with remove groups from pattern
     * @throws ModuleException
     */
    public static String removeGroups(Path filePath, String pattern, String groups) throws ModuleException {
        try {
            List<Integer> iGroups = Arrays.stream(groups.split(",")).map(Integer::parseInt).collect(Collectors.toList());
            return removeGroups(pattern, filePath.getFileName().toString(), iGroups);
        } catch (NumberFormatException e) {
            throw new ModuleException("Error reading plugin parameter group numbers to remove", e);
        } catch (PatternSyntaxException e) {
            throw new ModuleException("Error reading plugin parameter pattern", e);
        }
    }

    /**
     * Remove given groups number from source given parameter based on compiled regexp pattern.
     *
     * @param source  source to remove some groups from
     * @param pattern complied pattern as regexp to find groups to remove
     * @param groups  list of groups to remove from fileName matching pattern
     * @return file name with remove groups from pattern
     * @throws ModuleException
     */
    public static String removeGroups(String pattern, String source, List<Integer> groups)
            throws ModuleException {
        return replaceGroups(pattern, source, groups, "");
    }

    /**
     * Replace given groups number with given replacement string from source given parameter based on compiled regexp pattern.
     *
     * @param source  source to remove some groups from
     * @param pattern complied pattern as regexp to find groups to remove
     * @param groups  list of groups to remove from fileName matching pattern
     * @return file name with remove groups from pattern
     * @throws ModuleException
     */
    public static String replaceGroups(String pattern, String source, List<Integer> groups, String replacement)
            throws ModuleException {
        Matcher m = Pattern.compile(pattern).matcher(source);
        if (!m.matches()) {
            throw new ModuleException(String.format("Pattern [%s] does not match file name [%s]", pattern, source));
        }
        // Sort groups to replace them starting by the last one in order to use char indexes in original string without
        // recalculation after each modification.
        Collections.sort(groups);
        Collections.reverse(groups);

        String result = source;
        // For each group calculate char index start and end to replace in string
        for (Integer group: groups) {
            int start = m.start(group);
            int end = m.end(group);
            result = new StringBuilder(result).replace(start,end, replacement).toString();
        }
        return result;
    }

}
