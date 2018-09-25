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

import com.google.common.collect.ImmutableMap;
import ddf.catalog.CatalogFramework;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.Result;
import ddf.catalog.resource.ResourceNotSupportedException;
import ddf.catalog.transform.CatalogTransformerException;
import ddf.catalog.transform.MetacardTransformer;
import ddf.security.common.audit.SecurityLogger;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.boon.json.JsonFactory;
import org.boon.json.JsonParserFactory;
import org.boon.json.JsonSerializerFactory;
import org.boon.json.ObjectMapper;
import org.codice.ddf.catalog.ui.offline.ResourceZipper;
import org.codice.ddf.commands.util.QueryResulterable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExportResources {

  private static final Logger LOGGER = LoggerFactory.getLogger(ExportResources.class);

  private static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss.SSS'Z'");

  private static final ObjectMapper JSON_MAPPER =
      JsonFactory.create(
          new JsonParserFactory(), new JsonSerializerFactory().includeNulls().includeEmpty());

  private ExportCatalog exportCatalog;

  private CatalogFramework catalogFramework;

  public ExportResources(ExportCatalog exportCatalog, CatalogFramework catalogFramework) {
    this.exportCatalog = exportCatalog;
    this.catalogFramework = catalogFramework;
  }

  public Pair<String, List<String>> export(
      String cql, MetacardTransformer metadataTransformer, ExportType exportType, String directory)
      throws IOException {

    String filename = makeZipFileName(directory);
    ResourceZipper resourceZipper =
        new ResourceZipper(catalogFramework, metadataTransformer, filename);

    SecurityLogger.audit(
        "Exporting [{}] of local results from cql [{}] to zip file [{}]",
        exportType,
        cql,
        filename);
    List<String> failedIds = doExport(cql, resourceZipper, exportType, filename);
    SecurityLogger.audit(
        "Export to zip file [{}] completed with {} failed result(s)", filename, failedIds.size());
    return new ImmutablePair<>(filename, failedIds);
  }

  private List<String> doExport(
      String cql, ResourceZipper resourceZipper, ExportType exportType, String filename) {
    List<Metacard> failedMetacards = new ArrayList<>();

    QueryResulterable primaryResults = exportCatalog.queryAsLocalOnly(cql);
    for (Result primaryResult : primaryResults) {
      QueryResulterable primaryAndHistoryResults =
          exportCatalog.getLocalHistory(primaryResult.getMetacard().getId());
      for (Result primaryOrHistory : primaryAndHistoryResults) {
        Metacard metacard = primaryOrHistory.getMetacard();
        try {
          resourceZipper.add(metacard, exportType);
          SecurityLogger.audit(
              "Metacard [{}] was exported successfully to zip file [{}]",
              metacard.getId(),
              filename);
        } catch (IOException | CatalogTransformerException | ResourceNotSupportedException e) {
          LOGGER.debug("Metacard [{}] could not be added to the zip file", metacard.getId(), e);
          SecurityLogger.audit(
              "Metacard [{}] could not be added to the zip file [{}]. {}",
              metacard.getId(),
              filename,
              e.getMessage());
          failedMetacards.add(metacard);
        }
      }
    }

    if (!failedMetacards.isEmpty()) {
      addErrorFile(resourceZipper, failedMetacards);
    }
    return failedMetacards.stream().map(Metacard::getId).collect(Collectors.toList());
  }

  private void addErrorFile(ResourceZipper resourceZipper, List<Metacard> failures) {
    List<Object> errors = new ArrayList<>(failures.size());
    failures
        .stream()
        .map(failure -> ImmutableMap.of("id", failure.getId(), "title", failure.getTitle()))
        .forEach(errors::add);
    try {
      resourceZipper.addErrorsFile(
          IOUtils.toInputStream(JSON_MAPPER.toJson(errors), StandardCharsets.UTF_8));
    } catch (IOException e) {
      LOGGER.debug("Failed to add errors file to zip.", e);
    }
  }

  private String makeZipFileName(String directory) {
    return String.format("%s/export-%s.zip", directory, LocalDateTime.now().format(DATE_FORMAT));
  }
}
