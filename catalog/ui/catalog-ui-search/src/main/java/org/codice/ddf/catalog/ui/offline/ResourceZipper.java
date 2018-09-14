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

import ddf.catalog.CatalogFramework;
import ddf.catalog.content.data.ContentItem;
import ddf.catalog.data.Attribute;
import ddf.catalog.data.BinaryContent;
import ddf.catalog.data.Metacard;
import ddf.catalog.operation.ResourceResponse;
import ddf.catalog.operation.impl.ResourceRequestByProductUri;
import ddf.catalog.resource.Resource;
import ddf.catalog.resource.ResourceNotFoundException;
import ddf.catalog.resource.ResourceNotSupportedException;
import ddf.catalog.transform.CatalogTransformerException;
import ddf.catalog.transform.MetacardTransformer;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.ZipParameters;
import org.codice.ddf.catalog.ui.content.exports.ExportType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResourceZipper {

  private static final Logger LOGGER = LoggerFactory.getLogger(ResourceZipper.class);

  private final CatalogFramework catalogFramework;

  private final MetacardTransformer metacardTransformer;

  private final ZipFile zipFile;

  public ResourceZipper(
      CatalogFramework catalogFramework,
      MetacardTransformer metacardTransformer,
      String absolutePathToZip)
      throws IOException {
    this.zipFile = createInitialZipFile(absolutePathToZip);
    this.catalogFramework = catalogFramework;
    this.metacardTransformer = metacardTransformer;
  }

  public void add(Metacard metacard, ExportType exportType)
      throws IOException, CatalogTransformerException, ResourceNotSupportedException {
    addMetacard(metacard, exportType);
  }

  public void addErrorsFile(InputStream errors) throws IOException {
    writeToZip("errors.json", errors);
  }

  private void addMetacard(Metacard metacard, ExportType exportType)
      throws CatalogTransformerException, IOException, ResourceNotSupportedException {

    if (exportType == ExportType.METADATA_ONLY || exportType == ExportType.METADATA_AND_CONTENT) {
      writePrimaryMetacardToZip(metacard);
    }

    String id = metacard.getId();

    if (exportType == ExportType.CONTENT_ONLY || exportType == ExportType.METADATA_AND_CONTENT) {
      write(getContentUri(metacard), this::writePrimaryContentToZip, id);
    }

    if (exportType == ExportType.METADATA_AND_CONTENT) {
      write(getOriginalContentUri(metacard), this::writeDerivedOriginalContentToZip, id);

      write(getOverviewContentUri(metacard), this::writeDerivedOverviewContentToZip, id);

      writePrimaryOtherDerivedContent(metacard, id);
    }
  }

  private void writeOtherDerivedContent(
      Metacard metacard, String id, BiFunction<Integer, String, String> pather)
      throws IOException, ResourceNotSupportedException {
    List<URI> otherUris = getOtherContentUri(metacard).collect(Collectors.toList());
    int index = 1;
    for (URI otherUri : otherUris) {
      final int finalIndex = index;
      write(
          Optional.of(otherUri),
          (metacardId, uri) -> writer(metacardId, uri, s -> pather.apply(finalIndex, s)),
          id);
      index++;
    }
  }

  private void writePrimaryOtherDerivedContent(Metacard metacard, String id)
      throws IOException, ResourceNotSupportedException {
    writeOtherDerivedContent(
        metacard, id, (index, name) -> ZipPathGenerator.derivedOtherPath(id, index, name));
  }

  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  private void write(Optional<URI> optionalUri, ResourceZipper.Writer writer, String metacardId)
      throws IOException, ResourceNotSupportedException {
    if (optionalUri.isPresent()) {
      writer.write(metacardId, optionalUri.get());
    }
  }

  private static ZipFile createInitialZipFile(String absolutePath) throws IOException {
    try {
      return new ZipFile(absolutePath);
    } catch (ZipException e) {
      throw new IOException(
          String.format("Unable to create initial ZipFile object for path [%s]", absolutePath));
    }
  }

  private void writePrimaryContentToZip(String id, URI uri)
      throws IOException, ResourceNotSupportedException {
    writer(id, uri, name -> ZipPathGenerator.contentPath(id, name));
  }

  private void writeDerivedOverviewContentToZip(String id, URI uri)
      throws IOException, ResourceNotSupportedException {
    writer(id, uri, name -> ZipPathGenerator.derivedOverviewPath(id, name));
  }

  private void writeDerivedOriginalContentToZip(String id, URI uri)
      throws IOException, ResourceNotSupportedException {
    writer(id, uri, name -> ZipPathGenerator.derivedOriginalPath(id, name));
  }

  private void writer(String id, URI uri, Function<String, String> pather)
      throws IOException, ResourceNotSupportedException {
    try {
      ResourceResponse localResource =
          catalogFramework.getLocalResource(new ResourceRequestByProductUri(uri));
      Resource resource = localResource.getResource();
      writeToZip(pather.apply(resource.getName()), resource.getInputStream());
    } catch (ResourceNotFoundException e) {
      LOGGER.debug("Did not find the local resource: metacardId=[{}] uri=[{}]", id, uri, e);
    }
  }

  private Optional<URI> getContentUri(Metacard metacard) {
    return Optional.of(metacard)
        .filter(m -> m.getResourceURI() != null)
        .filter(m -> m.getResourceURI().getScheme() != null)
        .filter(m -> m.getResourceURI().getScheme().startsWith(ContentItem.CONTENT_SCHEME))
        .filter(m -> !m.getTags().contains("deleted"))
        .map(Metacard::getResourceURI);
  }

  private Optional<URI> getOriginalContentUri(Metacard metacard) {
    return getDerivedContentUri(metacard, this::isOriginalUri);
  }

  private Optional<URI> getOverviewContentUri(Metacard metacard) {
    return getDerivedContentUri(metacard, this::isOverviewUri);
  }

  private Stream<URI> getOtherContentUri(Metacard metacard) {
    return getDerivedContentUris(metacard, uri -> !isOriginalUri(uri) && !isOverviewUri(uri));
  }

  private Optional<URI> getDerivedContentUri(Metacard metacard, Predicate<String> uriTypeFilter) {
    return getDerivedContentUris(metacard, uriTypeFilter).findFirst();
  }

  private Stream<URI> getDerivedContentUris(Metacard metacard, Predicate<String> uriTypeFilter) {
    Attribute attribute = metacard.getAttribute(Metacard.DERIVED_RESOURCE_URI);
    if (attribute != null) {
      return Stream.of(attribute.getValues())
          .filter(Objects::nonNull)
          .flatMap(Collection::stream)
          .filter(String.class::isInstance)
          .map(String.class::cast)
          .filter(uriTypeFilter)
          .map(URI::create);
    }
    return Stream.empty();
  }

  private boolean isOverviewUri(String uri) {
    return uri.endsWith("#overview");
  }

  private boolean isOriginalUri(String uri) {
    return uri.endsWith("#original");
  }

  private void writePrimaryMetacardToZip(Metacard metacard)
      throws CatalogTransformerException, IOException {
    BinaryContent binaryMetacard = getBinaryContent(metacard);
    writeToZip(ZipPathGenerator.metacardPath(metacard.getId()), binaryMetacard.getInputStream());
  }

  private BinaryContent getBinaryContent(Metacard metacard) throws CatalogTransformerException {
    return metacardTransformer.transform(metacard, Collections.emptyMap());
  }

  private void writeToZip(String fileName, InputStream inputStream) throws IOException {
    ZipParameters parameters = new ZipParameters();
    parameters.setSourceExternalStream(true);
    parameters.setFileNameInZip(fileName);

    try {
      zipFile.addStream(inputStream, parameters);
    } catch (ZipException e) {
      throw new IOException(String.format("Failed to add file [%s] to zip", fileName), e);
    }
  }

  private interface Writer {
    void write(String metacardId, URI uri) throws IOException, ResourceNotSupportedException;
  }
}
