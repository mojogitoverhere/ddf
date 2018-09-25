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
import ddf.catalog.data.Attribute;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.types.Offline;
import ddf.catalog.filter.FilterBuilder;
import ddf.catalog.resource.ResourceNotSupportedException;
import ddf.catalog.source.IngestException;
import ddf.catalog.source.SourceUnavailableException;
import ddf.catalog.transform.CatalogTransformerException;
import ddf.catalog.transform.MetacardTransformer;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang.StringUtils;
import org.codice.ddf.catalog.ui.config.ConfigurationApplication;
import org.codice.ddf.catalog.ui.content.exports.ExportType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OfflineResources {

  private static final Logger LOGGER = LoggerFactory.getLogger(OfflineResources.class);

  private final OfflineCatalog offlineCatalog;

  private final ConfigurationApplication configurationApplication;

  private final CatalogFramework catalogFramework;

  private final MetacardTransformer xmlTransformer;

  private final OfflineAttributeSetter attributeSetter;

  private final OfflineContentProcessor contentDeleter;

  @SuppressWarnings("WeakerAccess" /* public for blueprint */)
  public OfflineResources(
      CatalogFramework catalogFramework,
      MetacardTransformer xmlTransformer,
      ConfigurationApplication configurationApplication,
      StorageProvider storageProvider,
      FilterBuilder filterBuilder) {
    this.configurationApplication = configurationApplication;
    this.catalogFramework = catalogFramework;
    this.xmlTransformer = xmlTransformer;

    this.offlineCatalog = new OfflineCatalog(catalogFramework, filterBuilder);
    this.attributeSetter = new OfflineAttributeSetter(catalogFramework);
    this.contentDeleter = new OfflineContentProcessor(storageProvider);
  }

  public Map<String, String> moveResourceOffline(String metacardId, String comment) {

    if (!isRootPathIsSetAndExists()) {
      return ImmutableMap.of(metacardId, "Root output path must be configured and exist.");
    }

    List<Metacard> metacards = queryCatalog(metacardId);

    Optional<Metacard> metacardToOffline =
        metacards.stream().filter(metacard -> metacard.getId().equals(metacardId)).findAny();

    if (!metacardToOffline.isPresent()) {
      LOGGER.debug("The metacard was not found: [{}]", metacardId);
      return ImmutableMap.of(metacardId, "The metacard was not found.");
    }

    if (OfflineResources.isOfflined(metacardToOffline.get())) {
      LOGGER.debug("The metacard is already offlined: metacardId=[{}]", metacardId);
      return ImmutableMap.of(metacardId, "The metacard is already offlined.");
    }

    String relativeBasePath = metacardId + ".zip";
    String absolutePathToZip = Paths.get(getOfflineRootPath(), relativeBasePath).toString();
    ResourceZipper resourceZipper;
    try {
      resourceZipper = new ResourceZipper(catalogFramework, xmlTransformer, absolutePathToZip);
    } catch (IOException e) {
      LOGGER.debug("Unable to create zip file at [{}]", absolutePathToZip, e);
      return ImmutableMap.of(metacardId, "Unable to create zip file.");
    }

    return doOffline(
        metacardId,
        metacards,
        resourceZipper,
        attributeSetter,
        contentDeleter,
        comment,
        relativeBasePath);
  }

  private Map<String, String> doOffline(
      String metacardId,
      List<Metacard> metacards,
      ResourceZipper resourceZipper,
      OfflineAttributeSetter attributeSetter,
      OfflineContentProcessor contentDeleter,
      String comment,
      String outputPath) {
    for (Metacard metacard : metacards) {
      try {
        resourceZipper.add(metacard, ExportType.METADATA_AND_CONTENT);
        contentDeleter.deleteContent(metacard);
        attributeSetter.process(metacard, comment, outputPath);
      } catch (IOException
          | CatalogTransformerException
          | ResourceNotSupportedException
          | StorageException
          | SourceUnavailableException
          | IngestException e) {
        LOGGER.debug("Offline failed for metacard(s)", e);
        return ImmutableMap.of(metacardId, "Offline failed during zip creation");
      }
    }
    return ImmutableMap.of(metacardId, "");
  }

  private static boolean isOfflined(Metacard metacard) {
    return Optional.ofNullable(metacard.getAttribute(Offline.OFFLINE_DATE))
        .map(Attribute::getValue)
        .isPresent();
  }

  private boolean isRootPathIsSetAndExists() {
    return StringUtils.isNotEmpty(getOfflineRootPath()) && new File(getOfflineRootPath()).exists();
  }

  private String getOfflineRootPath() {
    return configurationApplication.getOfflineRootPath();
  }

  private List<Metacard> queryCatalog(String metacardId) {
    return offlineCatalog.query(metacardId);
  }
}
