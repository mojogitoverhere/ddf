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
package org.codice.ddf.catalog.ui.offline;

import com.google.common.collect.ImmutableMap;
import ddf.catalog.CatalogFramework;
import ddf.catalog.content.StorageException;
import ddf.catalog.content.StorageProvider;
import ddf.catalog.data.Metacard;
import ddf.catalog.filter.FilterBuilder;
import ddf.catalog.operation.ProcessingDetails;
import ddf.catalog.operation.UpdateResponse;
import ddf.catalog.operation.impl.UpdateRequestImpl;
import ddf.catalog.resource.ResourceNotSupportedException;
import ddf.catalog.source.IngestException;
import ddf.catalog.source.SourceUnavailableException;
import ddf.catalog.transform.CatalogTransformerException;
import ddf.catalog.transform.MetacardTransformer;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.lingala.zip4j.exception.ZipException;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.codice.ddf.catalog.ui.config.ConfigurationApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OfflineResources {

  private static final Logger LOGGER = LoggerFactory.getLogger(OfflineResources.class);

  private final PathGenerator pathGenerator;

  private final OfflineAttributeSetter offlineAttributeSetter;

  private final OfflineCatalog offlineCatalog;

  private final ConfigurationApplication configurationApplication;

  private final CatalogFramework catalogFramework;

  private final MetacardTransformer xmlTransformer;

  private final Content content;

  @SuppressWarnings("WeakerAccess" /* public for blueprint */)
  public OfflineResources(
      CatalogFramework catalogFramework,
      MetacardTransformer xmlTransformer,
      ConfigurationApplication configurationApplication,
      StorageProvider storageProvider,
      FilterBuilder filterBuilder) {
    this.offlineCatalog = new OfflineCatalog(catalogFramework, filterBuilder);
    this.configurationApplication = configurationApplication;
    this.catalogFramework = catalogFramework;
    this.xmlTransformer = xmlTransformer;
    this.content = new Content(storageProvider);
    this.pathGenerator = new PathGenerator();
    offlineAttributeSetter = new OfflineAttributeSetter();
  }

  public Map<String, String> moveResourceOffline(String metacardId, String location) {

    if (!isRootPathIsSetAndExists()) {
      return ImmutableMap.of(metacardId, "Root output path must be configured and exist.");
    }

    String relativeBasePath;
    try {
      relativeBasePath = generateRelativeBasePath(metacardId);
    } catch (OfflineException e) {
      LOGGER.debug("Unable to generate the path for the addToZip zip: metacardId=[{}]", e);
      return ImmutableMap.of(metacardId, "Unable to generate the path for the addToZip zip.");
    }

    List<Metacard> metacards = queryCatalog(metacardId);

    boolean wasMetacardFound =
        metacards.stream().map(Metacard::getId).anyMatch(id -> id.equals(metacardId));
    if (!wasMetacardFound) {
      LOGGER.debug("The metacard was not found: [{}]", metacardId);
      return ImmutableMap.of(metacardId, "The metacard was not found.");
    }

    boolean isOfflined = metacards.stream().anyMatch(MetacardUtils::isOfflined);
    if (isOfflined) {
      LOGGER.debug("The metacard is already offlined: metacardId=[{}]", metacardId);
      return ImmutableMap.of(metacardId, "The metacard is already offlined.");
    }

    ResourceZipper resourceZipper;
    try {
      resourceZipper =
          new ResourceZipper(
              catalogFramework, xmlTransformer, getOfflineRootPath(), relativeBasePath, metacardId);
    } catch (IOException e) {
      LOGGER.debug(
          "Failed to create the ResourceZipper: outputPath=[%s] metacardId=[%s]",
          relativeBasePath, metacardId, e);
      return ImmutableMap.of(metacardId, "Failed to create the ResourceZipper.");
    }

    boolean success = addToZip(metacardId, metacards, resourceZipper);
    if (!success) {
      return ImmutableMap.of(metacardId, "Unable to create the addToZip zip.");
    }

    ImmutableMap.Builder<String, String> builder = new ImmutableMap.Builder<>();

    for (Metacard metacard : metacards) {
      builder.put(
          metacard.getId(), processMetacard(metacard, location, resourceZipper.getRelativePath()));
    }

    return builder.build();
  }

  private boolean isRootPathIsSetAndExists() {
    return StringUtils.isNotEmpty(getOfflineRootPath()) && new File(getOfflineRootPath()).exists();
  }

  private boolean addToZip(
      String metacardId, List<Metacard> metacards, ResourceZipper resourceZipper) {
    try {
      for (Metacard metacard : metacards) {
        addToResourceZipper(resourceZipper, metacardId, metacard);
      }
      return true;
    } catch (OfflineException e) {
      String historyMetacardIds =
          metacards
              .stream()
              .map(Metacard::getId)
              .filter(s -> !s.equals(metacardId))
              .collect(Collectors.joining(","));
      LOGGER.debug(
          "Unable to create the addToZip zip: metacardId=[{}] historyMetacardIds=[{}]",
          metacardId,
          historyMetacardIds,
          e);
    }
    return false;
  }

  private String processMetacard(Metacard metacard, String location, String outputPath) {
    if (!deleteStorageProviderContent(metacard)) {
      return "Failed to delete the metacard content.";
    }
    if (!MetacardUtils.isRevision(metacard)) {
      Metacard newMetacard = setMetacardAttributes(metacard, location, outputPath);
      if (!update(newMetacard)) {
        return "Failed to update the metacard attributes.";
      }
    }
    return "";
  }

  private boolean update(Metacard metacard) {
    try {
      UpdateResponse updateResponse =
          catalogFramework.update(new UpdateRequestImpl(metacard.getId(), metacard));
      if (CollectionUtils.isNotEmpty(updateResponse.getProcessingErrors())) {
        LOGGER.debug(
            "Failed to update the catalog: metacardId=[{}] details=[{}]",
            metacard.getId(),
            stringifyProcessingErrors(updateResponse.getProcessingErrors()));
        return false;
      }
      return true;
    } catch (SourceUnavailableException | IngestException e) {
      LOGGER.debug(
          "Unable to update the metacard in the framework: metacardId=[{}]", metacard.getId(), e);
    }
    return false;
  }

  private String stringifyProcessingErrors(Set<ProcessingDetails> details) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);

    for (ProcessingDetails detail : details) {
      pw.append(detail.getSourceId());
      pw.append(":");
      if (detail.hasException()) {
        detail.getException().printStackTrace(pw);
      }
      pw.append(System.lineSeparator());
    }
    return pw.toString();
  }

  private void addToResourceZipper(
      ResourceZipper resourceZipper, String metacardId, Metacard metacard) throws OfflineException {
    try {
      resourceZipper.add(metacard);
    } catch (IOException
        | CatalogTransformerException
        | ResourceNotSupportedException
        | ZipException e) {
      throw new OfflineException(
          String.format(
              "Failed to add a child resource to the zip file: metacardId=[%s] historyMetacardId=[%s]",
              metacardId, metacard.getId()),
          e);
    }
  }

  private Metacard setMetacardAttributes(Metacard metacard, String location, String outputPath) {
    return offlineAttributeSetter.setOfflineAttributes(metacard, location, outputPath);
  }

  private boolean deleteStorageProviderContent(Metacard metacard) {
    try {
      content.deleteContent(metacard);
      return true;
    } catch (StorageException e) {
      LOGGER.debug("Failed to delete content: metacardId=[%s]", metacard.getId(), e);
    }
    return false;
  }

  private String generateRelativeBasePath(String metacardId) throws OfflineException {
    try {
      return pathGenerator.generatePath(getOfflineRootPath(), metacardId);
    } catch (IOException e) {
      throw new OfflineException(
          String.format("Unable to generate the addToZip file path: metacardId=[%s]", metacardId),
          e);
    }
  }

  private String getOfflineRootPath() {
    return configurationApplication.getOfflineRootPath();
  }

  private List<Metacard> queryCatalog(String metacardId) {
    return offlineCatalog.query(metacardId);
  }
}
