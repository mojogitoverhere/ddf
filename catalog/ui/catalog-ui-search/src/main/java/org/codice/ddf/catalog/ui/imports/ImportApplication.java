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
import static spark.Spark.delete;
import static spark.Spark.get;
import static spark.Spark.post;

import com.google.common.collect.ImmutableMap;
import ddf.security.common.audit.SecurityLogger;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.boon.json.JsonFactory;
import org.boon.json.JsonParserFactory;
import org.boon.json.JsonSerializerFactory;
import org.boon.json.ObjectMapper;
import org.codice.ddf.catalog.ui.task.TaskMonitor;
import org.codice.ddf.catalog.ui.task.TaskMonitor.Task;
import org.codice.ddf.platform.util.PrettyBytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.servlet.SparkApplication;

public class ImportApplication implements SparkApplication {

  private static final Logger LOGGER = LoggerFactory.getLogger(ImportApplication.class);

  private static final ObjectMapper JSON_MAPPER =
      JsonFactory.create(
          new JsonParserFactory(), new JsonSerializerFactory().includeNulls().includeEmpty());

  private static final String IMPORT_PATH_ARG = "path";

  private ImportDirectory importDirectory;

  private final Importer importer;

  private TaskMonitor importMonitor = new TaskMonitor();

  private ExecutorService executor = Executors.newSingleThreadExecutor();

  public ImportApplication(ImportDirectory importDirectory, Importer importer) {
    this.importDirectory = importDirectory;
    this.importer = importer;
  }

  @Override
  public void init() {
    get(
        "/resources/import/available",
        (req, res) ->
            makeAvailableImportsResponse(importDirectory.toString(), importDirectory.getPaths()),
        JSON_MAPPER::toJson);

    post(
        "/import",
        APPLICATION_JSON,
        (req, res) -> {
          res.type(APPLICATION_JSON);
          String rootPath = importDirectory.toString();

          if (StringUtils.isEmpty(rootPath) && existsAndIsReadable(rootPath)) {
            res.status(500);
            return ImmutableMap.of("message", "The import directory has not been configured.");
          }

          Map<String, Object> importArguments = JSON_MAPPER.parser().parseMap(req.body());

          if (!(importArguments.get(IMPORT_PATH_ARG) instanceof List)
              || ((List<String>) importArguments.get(IMPORT_PATH_ARG)).isEmpty()
              || ((List<String>) importArguments.get(IMPORT_PATH_ARG))
                  .stream()
                  .anyMatch(StringUtils::isEmpty)) {
            res.status(400);
            return Collections.singletonMap(
                "message",
                String.format(
                    "The required argument '%s' must be a non-empty list of strings.",
                    IMPORT_PATH_ARG));
          }
          List<String> imports = (List<String>) importArguments.get(IMPORT_PATH_ARG);

          for (String path : imports) {
            try {
              File archiveFile = Paths.get(rootPath, path).toFile();
              importArchive(path, archiveFile);
            } catch (ImportException e) {
              LOGGER.debug("Could not import specified file", e);
            }
          }
          return "";
        },
        JSON_MAPPER::toJson);

    get("/resources/import/tasks", (req, res) -> importMonitor.getTasks(), JSON_MAPPER::toJson);

    get(
        "/resources/import/task/:id",
        (req, res) -> {
          res.type(APPLICATION_JSON);
          return importMonitor.getTask(req.params(":id"));
        },
        JSON_MAPPER::toJson);

    delete(
        "/resources/import/task/:id",
        (req, res) -> {
          String id = req.params(":id");
          importMonitor.removeTask(id);
          return "";
        });
  }

  private void importArchive(String relativePath, File archiveFile) throws ImportException {
    File[] filesArray = null;
    if (archiveFile.isDirectory()) {
      filesArray = archiveFile.listFiles((dir, name) -> name.endsWith(".zip"));
      if (filesArray == null) {
        Task task = importMonitor.newTask();
        task.started();
        task.failed();
        task.putDetails("message", "No files to import in directory");
        task.putDetails("importFile", relativePath);
        throw new ImportException("No files to import in directory");
      }
    } else {
      filesArray = new File[] {archiveFile};
    }

    List<Pair<File, Task>> toDo =
        Arrays.stream(filesArray)
            .map(
                file -> {
                  Task task = importMonitor.newTask();
                  task.putDetails(
                      "importFile", file.toString().replace(importDirectory.toString(), ""));
                  return new ImmutablePair<>(file, task);
                })
            .collect(Collectors.toList());

    executor.submit(
        () -> {
          for (Pair<File, Task> current : toDo) {
            Task task = current.getRight();
            File file = current.getLeft();
            task.started();
            Set<String> failedMetacardImports = null;
            try {
              failedMetacardImports = importer.importArchive(file, task);
            } catch (ImportException e) {
              LOGGER.debug("Failed to import: relativePath=[{}]", relativePath, e);
              SecurityLogger.audit(
                  "Failed to import the archive [{}] and a partial import was not possible. {}",
                  archiveFile,
                  e.getMessage());
              task.failed();
              task.putDetails(
                  "message", "Failed to import the archive. A partial import was not possible.");
              return;
            }
            if (CollectionUtils.isNotEmpty(failedMetacardImports)) {
              task.putDetails(
                  new ImmutableMap.Builder<String, Object>()
                      .put("message", "Some metacards failed to import.")
                      .put("failed", new ArrayList<>(failedMetacardImports))
                      .build());
            }
            SecurityLogger.audit(
                "Import of archive [{}] completed with {} failed result(s)",
                archiveFile,
                failedMetacardImports.size());
          }
        });
  }

  private boolean existsAndIsReadable(String path) {
    File file = new File(path);
    return file.isDirectory() && file.canRead();
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
    String normalizedPath = stripRoot(path.toString(), root + File.separator);
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
