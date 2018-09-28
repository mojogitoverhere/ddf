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
package org.codice.ddf.catalog.ui.content.exports;

import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static spark.Spark.delete;
import static spark.Spark.get;
import static spark.Spark.post;

import com.google.common.collect.ImmutableMap;
import ddf.catalog.transform.ExportableMetadataTransformer;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.boon.json.JsonFactory;
import org.boon.json.JsonParserAndMapper;
import org.boon.json.JsonParserFactory;
import org.boon.json.JsonSerializerFactory;
import org.boon.json.ObjectMapper;
import org.codice.ddf.catalog.ui.task.TaskMonitor;
import org.codice.ddf.catalog.ui.task.TaskMonitor.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.servlet.SparkApplication;

public class ExportApplication implements SparkApplication {

  private static final Logger LOGGER = LoggerFactory.getLogger(ExportApplication.class);

  private static final ObjectMapper JSON_MAPPER =
      JsonFactory.create(
          new JsonParserFactory(), new JsonSerializerFactory().includeNulls().includeEmpty());

  private ExportResources exportResources;

  private MetadataFormats metadataFormats;

  private ExportDirectory exportDirectory;

  private TaskMonitor exportMonitor = new TaskMonitor();

  private ExecutorService executor = Executors.newSingleThreadExecutor();

  public ExportApplication(
      ExportResources exportResources,
      MetadataFormats metadataFormats,
      ExportDirectory exportDirectory) {
    this.exportResources = exportResources;
    this.metadataFormats = metadataFormats;
    this.exportDirectory = exportDirectory;
  }

  @Override
  public void init() {
    get(
        "/resources/export/location",
        (req, res) -> {
          res.type(APPLICATION_JSON);
          return ImmutableMap.of("root", exportDirectory.toString());
        },
        JSON_MAPPER::toJson);

    get(
        "/resources/export/formats",
        (req, res) -> {
          res.type(APPLICATION_JSON);
          return ImmutableMap.of("formats", metadataFormats.getIds());
        },
        JSON_MAPPER::toJson);

    post(
        "/resources/export",
        APPLICATION_JSON,
        (req, res) -> {
          if (!exportDirectory.existsAndIsWritable()) {
            return error(res, 500, "The configured export directory must exist and be writable");
          }

          ExportOptions exportOptions = ExportOptions.fromRequest(req);
          String cql = exportOptions.getCql();
          String metadataFormat = exportOptions.getMetadataFormat();
          ExportType type = exportOptions.getType();

          if (StringUtils.isBlank(cql)) {
            return error(res, 400, "A CQL string is required");
          }

          if (type == ExportType.INVALID) {
            return error(
                res,
                400,
                String.format(
                    "Invalid export type. Should be %s, %s, or %s.",
                    ExportType.METADATA_AND_CONTENT,
                    ExportType.METADATA_ONLY,
                    ExportType.CONTENT_ONLY));
          }

          Optional<ExportableMetadataTransformer> metadataTransformer =
              metadataFormats.getTransformerById(metadataFormat);
          if (!metadataTransformer.isPresent()) {
            return error(
                res,
                400,
                String.format(
                    "No transformer available for the [%s] metadata format.", metadataFormat));
          }

          LOGGER.trace(
              "Doing export with cql={}, metadataFormat={}, and type={}",
              cql,
              metadataFormat,
              type);

          Task task = exportMonitor.newTask();
          task.putDetails("cql", cql);
          task.putDetails("metadataFormat ", metadataFormat);
          task.putDetails("type", type);
          task.putDetails("title", exportOptions.getTitle());
          executor.submit(
              () -> {
                task.started();
                Pair<String, List<String>> results = null;
                try {
                  results =
                      exportResources.export(
                          cql, metadataTransformer.get(), type, exportDirectory.toString(), task);
                } catch (IOException e) {
                  LOGGER.warn(
                      "An exception occurred while attempting to export items. Archive may be incomplete or missing. You may wish to try the export again. If you still encounter problems ensure there is sufficient disk space available and the proper permissions are set.",
                      e);
                  task.failed();
                  task.putDetails(
                      "details",
                      "An error occurred while exporting, please see the logs for more information");
                  return;
                } catch (ExportException e) {
                  LOGGER.debug(
                      "Query used for export could not find any results to export. Query=(%s)",
                      cql, e);
                  task.failed();
                  task.putDetails("details", e.getMessage());
                  return;
                }

                task.putDetails(
                    ImmutableMap.of(
                        "filename",
                        new File(results.getLeft()).getName(),
                        "failed",
                        results.getRight()));
              });

          return Collections.singletonMap("task-id", task.getId());
        },
        JSON_MAPPER::toJson);

    get("/resources/export/tasks", (req, res) -> exportMonitor.getTasks(), JSON_MAPPER::toJson);

    get(
        "/resources/export/task/:id",
        (req, res) -> {
          res.type(APPLICATION_JSON);
          return exportMonitor.getTask(req.params(":id"));
        },
        JSON_MAPPER::toJson);

    delete(
        "/resources/export/task/:id",
        (req, res) -> {
          String id = req.params(":id");
          exportMonitor.removeTask(id);
          return "";
        });
  }

  private Map<String, Object> error(Response res, int statusCode, String message) {
    res.status(statusCode);
    res.header(CONTENT_TYPE, APPLICATION_JSON);
    return errorJson(message);
  }

  private Map<String, Object> errorJson(String message) {
    return ImmutableMap.of("message", message);
  }

  private static class ExportOptions {

    private static final JsonParserAndMapper JSON_PARSER = JsonFactory.create().parser();

    private static final String DEFAULT_METADATA_FORMAT = "xml";

    private String cql;

    private String metadataFormat;

    private String title;

    private ExportType exportType;

    private ExportOptions(String cql, String metadataFormat, ExportType exportType, String title) {
      this.cql = cql;
      this.metadataFormat = metadataFormat;
      this.exportType = exportType;
      this.title = title;
    }

    public static ExportOptions fromRequest(Request request) {
      Map<String, Object> parsedBody = JSON_PARSER.parseMap(request.body());
      String cql = null;
      String metadataFormat = DEFAULT_METADATA_FORMAT;
      ExportType exportType = ExportType.INVALID;
      String title = "No title";

      Object cqlRaw = parsedBody.get("cql");
      if (cqlRaw instanceof String && StringUtils.isNotBlank((String) cqlRaw)) {
        cql = (String) cqlRaw;
      }

      Object metadataFormatRaw = parsedBody.get("metadataFormat");
      if (metadataFormatRaw instanceof String
          && StringUtils.isNotBlank((String) metadataFormatRaw)) {
        metadataFormat = (String) metadataFormatRaw;
      }

      Object exportTypeRaw = parsedBody.get("type");
      if (exportTypeRaw instanceof String) {
        exportType = ExportType.fromString((String) exportTypeRaw);
      }

      Object titleRaw = parsedBody.get("title");
      if (titleRaw instanceof String) {
        title = (String) titleRaw;
      }

      return new ExportOptions(cql, metadataFormat, exportType, title);
    }

    public String getCql() {
      return this.cql;
    }

    public String getMetadataFormat() {
      return this.metadataFormat;
    }

    public ExportType getType() {
      return this.exportType;
    }

    public String getTitle() {
      return this.title;
    }
  }
}
