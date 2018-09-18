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
import static spark.Spark.get;
import static spark.Spark.post;

import com.google.common.collect.ImmutableMap;
import ddf.catalog.transform.ExportableMetadataTransformer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.boon.json.JsonFactory;
import org.boon.json.JsonParserAndMapper;
import org.boon.json.JsonParserFactory;
import org.boon.json.JsonSerializerFactory;
import org.boon.json.ObjectMapper;
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
        this::toJson);

    get(
        "/resources/export/formats",
        (req, res) -> {
          res.type(APPLICATION_JSON);
          return ImmutableMap.of("formats", metadataFormats.getIds());
        },
        this::toJson);

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

          // TODO TIB-731 do the export asynchronously
          Pair<String, List<String>> results =
              exportResources.export(
                  cql, metadataTransformer.get(), type, exportDirectory.toString());

          return ImmutableMap.of("filename", results.getLeft(), "failed", results.getRight());
        },
        this::toJson);
  }

  private Map<String, Object> error(Response res, int statusCode, String message) {
    res.status(statusCode);
    res.header(CONTENT_TYPE, APPLICATION_JSON);
    return errorJson(message);
  }

  private Map<String, Object> errorJson(String message) {
    return ImmutableMap.of("message", message);
  }

  private String toJson(Object result) {
    return JSON_MAPPER.toJson(result);
  }

  private static class ExportOptions {

    private static final JsonParserAndMapper JSON_PARSER = JsonFactory.create().parser();

    private static final String DEFAULT_METADATA_FORMAT = "xml";

    private String cql;

    private String metadataFormat;

    private ExportType exportType;

    private ExportOptions(String cql, String metadataFormat, ExportType exportType) {
      this.cql = cql;
      this.metadataFormat = metadataFormat;
      this.exportType = exportType;
    }

    public static ExportOptions fromRequest(Request request) {
      Map<String, Object> parsedBody = JSON_PARSER.parseMap(request.body());
      String cql = null;
      String metadataFormat = DEFAULT_METADATA_FORMAT;
      ExportType exportType = ExportType.INVALID;

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

      return new ExportOptions(cql, metadataFormat, exportType);
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
  }
}
