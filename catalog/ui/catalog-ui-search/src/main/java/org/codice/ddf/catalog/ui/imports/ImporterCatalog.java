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

import com.google.common.io.ByteSource;
import ddf.catalog.CatalogFramework;
import ddf.catalog.content.StorageException;
import ddf.catalog.content.StorageProvider;
import ddf.catalog.content.data.ContentItem;
import ddf.catalog.content.data.impl.ContentItemImpl;
import ddf.catalog.content.operation.CreateStorageRequest;
import ddf.catalog.content.operation.impl.CreateStorageRequestImpl;
import ddf.catalog.core.versioning.DeletedMetacard;
import ddf.catalog.core.versioning.MetacardVersion;
import ddf.catalog.data.Attribute;
import ddf.catalog.data.BinaryContent;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.types.Offline;
import ddf.catalog.federation.FederationException;
import ddf.catalog.filter.FilterBuilder;
import ddf.catalog.operation.CreateResponse;
import ddf.catalog.operation.DeleteResponse;
import ddf.catalog.operation.ProcessingDetails;
import ddf.catalog.operation.QueryResponse;
import ddf.catalog.operation.Request;
import ddf.catalog.operation.Response;
import ddf.catalog.operation.UpdateResponse;
import ddf.catalog.operation.impl.CreateRequestImpl;
import ddf.catalog.operation.impl.DeleteRequestImpl;
import ddf.catalog.operation.impl.QueryImpl;
import ddf.catalog.operation.impl.QueryRequestImpl;
import ddf.catalog.operation.impl.UpdateRequestImpl;
import ddf.catalog.resource.ResourceNotFoundException;
import ddf.catalog.resource.ResourceNotSupportedException;
import ddf.catalog.source.IngestException;
import ddf.catalog.source.SourceUnavailableException;
import ddf.catalog.source.UnsupportedQueryException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.commons.collections.CollectionUtils;
import org.codice.ddf.catalog.ui.util.CatalogUtils;

class ImporterCatalog {

  private final CatalogFramework catalogFramework;

  private final StorageProvider storageProvider;

  private final FilterBuilder filterBuilder;

  private final CatalogUtils catalogUtils;

  ImporterCatalog(
      CatalogFramework catalogFramework,
      StorageProvider storageProvider,
      FilterBuilder filterBuilder,
      CatalogUtils catalogUtils) {
    this.catalogFramework = catalogFramework;
    this.storageProvider = storageProvider;
    this.filterBuilder = filterBuilder;
    this.catalogUtils = catalogUtils;
  }

  /** Returns null if the metacard is not found in the catalog. */
  Metacard findArchiveMetacardInCatalog(Metacard metacardInArchive)
      throws UnsupportedQueryException, ImportException, SourceUnavailableException,
          FederationException {
    String metacardId = getPrimaryMetacardId(metacardInArchive);

    QueryResponse queryResponse = queryForPrimaryOrDeletedMetacard(metacardId);

    checkResponse(metacardId, queryResponse, "Failed to query the catalog provider for a metacard");

    return queryResponse.getResults().isEmpty()
        ? null
        : queryResponse.getResults().get(0).getMetacard();
  }

  void updateMetacard(MetacardImportItem metacardImportItem)
      throws IngestException, ImportException, StorageException, SourceUnavailableException {
    UpdateResponse updateResponse =
        catalogFramework.update(
            new UpdateRequestImpl(
                getPrimaryMetacardId(metacardImportItem.getMetacard()),
                metacardImportItem.getMetacard()));
    checkResponse(
        metacardImportItem.getMetacard().getId(),
        updateResponse,
        "Failed to update the catalog framework");
    createContent(metacardImportItem);
  }

  void createMetacard(MetacardImportItem metacardImportItem)
      throws IngestException, StorageException, ImportException, SourceUnavailableException {
    CreateResponse createResponse =
        catalogFramework.create(new CreateRequestImpl(metacardImportItem.getMetacard()));
    checkResponse(
        metacardImportItem.getMetacard().getId(),
        createResponse,
        "Failed to create a metacard in the catalog framework");
    createContent(metacardImportItem);
  }

  boolean isDeleted(Metacard metacard) {
    return findStringValue(metacard, DeletedMetacard.DELETION_OF_ID).isPresent()
        && findStringValue(metacard, DeletedMetacard.LAST_VERSION_ID).isPresent();
  }

  boolean isActive(Metacard metacard) {
    return !isDeleted(metacard)
        && !findStringValue(metacard, MetacardVersion.VERSION_OF_ID).isPresent();
  }

  boolean isOffline(Metacard metacard) {
    return findStringValue(metacard, Offline.OFFLINED_BY).isPresent();
  }

  void restore(Metacard metacard)
      throws IngestException, ResourceNotSupportedException, IOException,
          SourceUnavailableException, FederationException, UnsupportedQueryException,
          ResourceNotFoundException, ImportException {

    Optional<String> metacardDeletedIdOptional =
        findStringValue(metacard, DeletedMetacard.DELETION_OF_ID);
    Optional<String> metacardDeletedVersion =
        findStringValue(metacard, DeletedMetacard.LAST_VERSION_ID);

    if (metacardDeletedIdOptional.isPresent() && metacardDeletedVersion.isPresent()) {
      catalogUtils.revert(metacardDeletedIdOptional.get(), metacardDeletedVersion.get());
    } else {
      throw new ImportException(
          String.format(
              "Only deleted metacards should be restored: metacardId=[%s]", metacard.getId()));
    }
  }

  void delete(Metacard metacard)
      throws SourceUnavailableException, IngestException, ImportException {
    DeleteResponse deleteResponse =
        catalogFramework.delete(
            new DeleteRequestImpl(Collections.singletonList(metacard.getId()), Metacard.ID, null));
    checkResponse(metacard.getId(), deleteResponse, "Failed to delete a metacard");
  }

  private Optional<String> findStringValue(Metacard metacard, String attribute) {
    return Optional.ofNullable(metacard.getAttribute(attribute))
        .map(Attribute::getValue)
        .filter(String.class::isInstance)
        .map(String.class::cast);
  }

  private QueryResponse queryForPrimaryOrDeletedMetacard(String metacardId)
      throws UnsupportedQueryException, SourceUnavailableException, FederationException {
    return catalogFramework.query(
        new QueryRequestImpl(
            new QueryImpl(
                filterBuilder.allOf(
                    filterBuilder.anyOf(
                        filterBuilder.attribute(Metacard.ID).equalTo().text(metacardId),
                        filterBuilder
                            .attribute(DeletedMetacard.DELETION_OF_ID)
                            .equalTo()
                            .text(metacardId)),
                    filterBuilder.attribute(Metacard.TAGS).like().text("*")))));
  }

  private String getPrimaryMetacardId(Metacard metacard) throws ImportException {
    return isDeleted(metacard)
        ? findStringValue(metacard, DeletedMetacard.DELETION_OF_ID)
            .orElseThrow(
                () ->
                    new ImportException(
                        String.format(
                            "Could not find the '%s' attribute on a deleted metacard: metacardId=[%s]",
                            DeletedMetacard.DELETION_OF_ID, metacard.getId())))
        : metacard.getId();
  }

  private void createContent(MetacardImportItem metacardImportItem) throws StorageException {

    List<ContentItem> contentItems = new LinkedList<>();

    Metacard metacard = metacardImportItem.getMetacard();

    addContent(metacard, null, metacardImportItem.getContent(), contentItems);

    for (Map.Entry<String, MetacardImportItem.ContentImportItem> derivedContentEntry :
        metacardImportItem.getDerivedContent().entrySet()) {
      addContent(
          metacard, derivedContentEntry.getKey(), derivedContentEntry.getValue(), contentItems);
    }

    CreateStorageRequest createStorageRequest =
        new CreateStorageRequestImpl(contentItems, new HashMap<>());
    storageProvider.create(createStorageRequest);
    storageProvider.commit(createStorageRequest);
  }

  private void addContent(
      Metacard metacard,
      @Nullable String type,
      MetacardImportItem.ContentImportItem contentAndFilename,
      List<ContentItem> contentItems) {

    if (contentAndFilename == null) {
      return;
    }

    BinaryContent binaryContent = contentAndFilename.getBinaryContent();

    contentItems.add(
        new ContentItemImpl(
            metacard.getId(),
            type,
            new WrappedByteSource(binaryContent),
            binaryContent.getMimeTypeValue(),
            contentAndFilename.getFileName(),
            0,
            metacard));
  }

  private <T extends Request> void checkResponse(
      String metacardId, Response<T> response, String message) throws ImportException {
    if (CollectionUtils.isNotEmpty(response.getProcessingErrors())) {
      throw new ImportException(
          String.format(
              "%s: metacardId=[%s] details=[%s]",
              message, metacardId, stringifyProcessingErrors(response.getProcessingErrors())));
    }
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

  private static class WrappedByteSource extends ByteSource {
    private BinaryContent binaryContent;

    private WrappedByteSource(BinaryContent binaryContent) {
      this.binaryContent = binaryContent;
    }

    @Override
    public InputStream openStream() {
      return binaryContent.getInputStream();
    }
  }
}
