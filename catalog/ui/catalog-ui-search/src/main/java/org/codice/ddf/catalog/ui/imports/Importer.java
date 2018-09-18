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

import ddf.catalog.CatalogFramework;
import ddf.catalog.Constants;
import ddf.catalog.content.StorageException;
import ddf.catalog.content.StorageProvider;
import ddf.catalog.core.versioning.MetacardVersion;
import ddf.catalog.data.Metacard;
import ddf.catalog.federation.FederationException;
import ddf.catalog.filter.FilterBuilder;
import ddf.catalog.resource.ResourceNotFoundException;
import ddf.catalog.resource.ResourceNotSupportedException;
import ddf.catalog.source.IngestException;
import ddf.catalog.source.SourceUnavailableException;
import ddf.catalog.source.UnsupportedQueryException;
import ddf.catalog.transform.CatalogTransformerException;
import ddf.catalog.transform.InputTransformer;
import ddf.mime.MimeTypeMapper;
import ddf.mime.MimeTypeResolutionException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.activation.MimeTypeParseException;
import javax.annotation.Nullable;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.Validate;
import org.codice.ddf.catalog.ui.util.CatalogUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Importer {

  private static final Logger LOGGER = LoggerFactory.getLogger(Importer.class);

  private static final Logger INGEST_LOGGER = LoggerFactory.getLogger(Constants.INGEST_LOGGER_NAME);

  private final InputTransformer inputTransformer;

  private final MimeTypeMapper mimeTypeMapper;

  private final Unzipper unzipper = new Unzipper();

  private final ImporterCatalog importerCatalog;

  /** This is a Null Object Pattern implementation of Status. */
  private static final Status NULL_STATUS =
      (completed, total) -> {
        // do nothing
      };

  @SuppressWarnings("WeakerAccess" /* access needed for blueprint */)
  public Importer(
      InputTransformer inputTransformer,
      CatalogFramework catalogFramework,
      StorageProvider storageProvider,
      FilterBuilder filterBuilder,
      MimeTypeMapper mimeTypeMapper,
      CatalogUtils catalogUtils) {
    this.inputTransformer = inputTransformer;
    this.mimeTypeMapper = mimeTypeMapper;
    this.importerCatalog =
        new ImporterCatalog(catalogFramework, storageProvider, filterBuilder, catalogUtils);
  }

  /**
   * @param fileToImport the zip file to import
   * @param unzipStatus the status of the unzipping process
   * @param importStatus the status of the importing process
   * @return a set of metacard identifiers that failed to import
   * @throws ImportException the import failed, a partial import was not possible
   */
  Set<String> importArchive(
      File fileToImport, @Nullable final Status unzipStatus, @Nullable final Status importStatus)
      throws ImportException {

    Validate.notNull(fileToImport, "The file to import must be non-null");

    Status localUnzipStatus = Optional.ofNullable(unzipStatus).orElse(NULL_STATUS);
    Status localImportStatus = Optional.ofNullable(importStatus).orElse(NULL_STATUS);

    ZipFile zipFile = getZipFile(fileToImport);

    Path temporaryDirectory = unzipper.unzip(zipFile, localUnzipStatus);

    try {

      ExtractedArchive extractedArchive = new ExtractedArchive(temporaryDirectory);

      long totalWork = extractedArchive.getMetacardCount();

      localImportStatus.update(0, totalWork);

      List<MetacardImportItem> metacardImportItems = extractImportDetails(extractedArchive);

      Set<String> metacardsThatFailedToImport = new HashSet<>();

      long completed = 0;
      for (MetacardImportItem metacardImportItem : metacardImportItems) {
        try {
          processMetacardToImport(metacardImportItem);
        } catch (UnsupportedQueryException
            | IngestException
            | StorageException
            | IOException
            | SourceUnavailableException
            | FederationException
            | ImportException
            | ResourceNotFoundException
            | ResourceNotSupportedException e) {
          LOGGER.debug(
              "Failed to import a metacard: metacardId=[{}]",
              metacardImportItem.getMetacard().getId(),
              e);
          // TODO (PR) what is the correct log level?
          INGEST_LOGGER.info(
              "Failed to import a metacard: metacardId=[{}]",
              metacardImportItem.getMetacard().getId(),
              e);
          metacardsThatFailedToImport.add(metacardImportItem.getMetacard().getId());
        } finally {
          localImportStatus.update(++completed, totalWork);
        }
      }

      return metacardsThatFailedToImport;
    } finally {
      boolean deleteStatus = FileUtils.deleteQuietly(temporaryDirectory.toFile());
      if (!deleteStatus) {
        LOGGER.debug("Unable to delete temporary directory: dir=[{}]", temporaryDirectory);
      }
    }
  }

  private ZipFile getZipFile(File fileToImport) throws ImportException {
    try {
      return new ZipFile(fileToImport);
    } catch (ZipException e) {
      throw new ImportException("Failed to create ZipFile object.", e);
    }
  }

  private List<MetacardImportItem> extractImportDetails(ExtractedArchive extractedArchive)
      throws ImportException {
    try {
      return extractedArchive
          .acceptVisitor(new ImportFileVisitor(inputTransformer, mimeTypeMapper))
          .flush()
          .getMetacardImportItems();
    } catch (IOException
        | CatalogTransformerException
        | MimeTypeResolutionException
        | MimeTypeParseException e) {
      throw new ImportException("Failed to extract the import details from the import archive.", e);
    }
  }

  private void processMetacardToImport(MetacardImportItem metacardImportItem)
      throws UnsupportedQueryException, IngestException, IOException, StorageException,
          ImportException, SourceUnavailableException, FederationException,
          ResourceNotFoundException, ResourceNotSupportedException {

    handlerFactory(metacardImportItem).handle();

    for (MetacardImportItem historyMetacardToImport : metacardImportItem.getHistory()) {
      processMetacardToImport(historyMetacardToImport);
    }
  }

  private interface Handler {
    void handle()
        throws UnsupportedQueryException, IngestException, IOException, StorageException,
            ImportException, SourceUnavailableException, FederationException,
            ResourceNotFoundException, ResourceNotSupportedException;
  }

  /**
   * Return a handler based on the state of the metacard being imported and the primary metacard (if
   * it exists). Throw an ImportException if handler cannot be determined.
   */
  private Handler handlerFactory(final MetacardImportItem metacardImportItem)
      throws SourceUnavailableException, FederationException, UnsupportedQueryException,
          ImportException {

    final Metacard metacardInArchive = metacardImportItem.getMetacard();

    Metacard metacardInCatalog = importerCatalog.findArchiveMetacardInCatalog(metacardInArchive);

    if (metacardInCatalog != null) {

      if (isRevision(metacardInCatalog)) {
        return this::handleImportingOntoARevisionMetacard;
      }

      if (isDeleted(metacardInCatalog) && isActive(metacardInArchive)) {
        return () ->
            handleImportingAnActiveMetacardOntoADeletedMetacard(
                metacardImportItem, metacardInCatalog);
      }

      if (isActive(metacardInCatalog) && isDeleted(metacardInArchive)) {
        return () -> handleImportingADeletedMetacardOntoAnActiveMetacard(metacardInCatalog);
      }

      if (isActive(metacardInCatalog) && isActive(metacardInArchive)) {
        if (isOffline(metacardInCatalog)) {
          return () ->
              handleImportingAnActiveMetacardOntoAnOfflineActiveMetacard(metacardImportItem);
        } else {
          return this::handleImportingAnActiveMetacardOntoAnOnlineActiveMetacard;
        }
      }

      if (isDeleted(metacardInCatalog) && isDeleted(metacardInArchive)) {
        return this::handleImportingADeletedMetacardOntoADeletedMetacard;
      }
    }

    if (metacardInCatalog == null) {
      return () -> handleImportingOntoANonexistentMetacard(metacardImportItem);
    }

    throw new ImportException(
        String.format(
            "Unable to determine how to handle the incoming metacard: metacardId=[%s]",
            metacardImportItem.getMetacard().getId()));
  }

  private void handleImportingOntoARevisionMetacard() {
    // do nothing
  }

  private void handleImportingADeletedMetacardOntoADeletedMetacard() {
    // do nothing
  }

  private void handleImportingAnActiveMetacardOntoAnOfflineActiveMetacard(
      MetacardImportItem metacardImportItem)
      throws SourceUnavailableException, StorageException, ImportException, IngestException {
    importerCatalog.updateMetacard(metacardImportItem);
  }

  private void handleImportingAnActiveMetacardOntoAnOnlineActiveMetacard() {
    // do nothing - exposes a bug in the storage provider
  }

  private void handleImportingAnActiveMetacardOntoADeletedMetacard(
      MetacardImportItem metacardImportItem, Metacard metacardInCatalog)
      throws ImportException, IngestException, ResourceNotFoundException,
          ResourceNotSupportedException, SourceUnavailableException, FederationException,
          UnsupportedQueryException, IOException, StorageException {
    importerCatalog.restore(metacardInCatalog);
    importerCatalog.updateMetacard(metacardImportItem);
  }

  private void handleImportingOntoANonexistentMetacard(MetacardImportItem metacardImportItem)
      throws SourceUnavailableException, StorageException, ImportException, IngestException {
    importerCatalog.createMetacard(metacardImportItem);
  }

  private void handleImportingADeletedMetacardOntoAnActiveMetacard(Metacard metacardInCatalog)
      throws ImportException, SourceUnavailableException, IngestException {
    importerCatalog.delete(metacardInCatalog);
  }

  private boolean isOffline(Metacard metacard) {
    return importerCatalog.isOffline(metacard);
  }

  private boolean isRevision(Metacard metacard) {
    return Optional.ofNullable(metacard.getTags())
        .filter(tags -> tags.contains(MetacardVersion.VERSION_TAG))
        .isPresent();
  }

  private boolean isDeleted(Metacard metacard) {
    return importerCatalog.isDeleted(metacard);
  }

  private boolean isActive(Metacard metacard) {
    return importerCatalog.isActive(metacard);
  }
}
