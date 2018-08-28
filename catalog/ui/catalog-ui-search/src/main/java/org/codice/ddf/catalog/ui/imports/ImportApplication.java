/**
 * Copyright (c) Codice Foundation
 *
 * <p>This is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details. A copy of the GNU Lesser General Public
 * License is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package org.codice.ddf.catalog.ui.imports;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static spark.Spark.get;

import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.boon.json.JsonFactory;
import org.boon.json.JsonParserFactory;
import org.boon.json.JsonSerializerFactory;
import org.boon.json.ObjectMapper;
import org.codice.ddf.platform.util.PrettyBytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.servlet.SparkApplication;

public class ImportApplication implements SparkApplication {

  private static final Logger LOGGER = LoggerFactory.getLogger(ImportApplication.class);

  private static final ObjectMapper JSON_MAPPER =
      JsonFactory.create(
          new JsonParserFactory(), new JsonSerializerFactory().includeNulls().includeEmpty());

  private ImportDirectory importDirectory;

  public ImportApplication(ImportDirectory importDirectory) {
    this.importDirectory = importDirectory;
  }

  @Override
  public void init() {
    get(
        "/resources/import/available",
        APPLICATION_JSON,
        (req, res) ->
            makeAvailableImportsResponse(
                importDirectory.getRootDirectory(), importDirectory.getPaths()),
        JSON_MAPPER::toJson);
  }

  private Map<String, Object> makeAvailableImportsResponse(String rootDir, List<Path> paths) {
    return ImmutableMap.of("root", rootDir, "files", makePathResponses(paths, rootDir));
  }

  private List<Map<String, String>> makePathResponses(List<Path> paths, String root) {
    return paths.stream().map(path -> makePathResponse(path, root)).collect(Collectors.toList());
  }

  private Map<String, String> makePathResponse(Path path, String root) {
    return ImmutableMap.of(
        "path", normalizePath(path, root),
        "size", getPrettyFileSize(path));
  }

  private String normalizePath(Path path, String root) {
    String normalizedPath = stripRoot(path.toString(), root);
    if (path.toFile().isDirectory()) {
      normalizedPath = normalizedPath + File.separator;
    }
    return normalizedPath;
  }

  private String stripRoot(String filePath, String root) {
    return filePath.substring(root.length());
  }

  private String getPrettyFileSize(Path path) {
    if (path.toFile().isDirectory()) {
      return "";
    }

    try {
      return PrettyBytes.prettify(Files.size(path));
    } catch (IOException e) {
      LOGGER.debug("Unable to get the size of file [{}]", path, e);
      return "";
    }
  }
}