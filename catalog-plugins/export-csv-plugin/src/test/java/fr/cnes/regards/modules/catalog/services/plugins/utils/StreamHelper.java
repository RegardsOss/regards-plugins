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
package fr.cnes.regards.modules.catalog.services.plugins.utils;

import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Helper to handle stream of data.
 *
 * @author Iliana Ghazali
 **/
public class StreamHelper {

    private StreamHelper() {
        // helper class
    }

    public static void writeResponseBodyToFile(StreamingResponseBody body, String filePath) throws IOException {
        FileOutputStream outputStream = new FileOutputStream(filePath);
        body.writeTo(outputStream);
        outputStream.close();
    }

    public static StreamingResponseBody getMockStream() {
        return outputStream -> {
            outputStream.write("Col1,Col2,Col3\n".getBytes());
            outputStream.write("Val1,Val2,Val3\n".getBytes());
        };
    }

    public static String getBodyContent(StreamingResponseBody resultBody) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        resultBody.writeTo(byteArrayOutputStream);
        return byteArrayOutputStream.toString(Charset.defaultCharset());
    }

}
