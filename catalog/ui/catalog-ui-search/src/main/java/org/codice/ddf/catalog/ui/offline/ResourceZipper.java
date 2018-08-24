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
import java.io.File;
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
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ResourceZipper {

  private static final Logger LOGGER = LoggerFactory.getLogger(ResourceZipper.class);

  private final CatalogFramework catalogFramework;

  private final MetacardTransformer xmlTransformer;

  private final ZipFile zipFile;

  private final ZipPathGenerator zipPathGenerator;

  private final String relativePath;

  ResourceZipper(
      CatalogFramework catalogFramework,
      MetacardTransformer xmlTransformer,
      String rootPath,
      String relativeBasePath,
      String metacardId)
      throws IOException {
    Pair<String, ZipFile> createResults =
        createInitialZipFile(rootPath, relativeBasePath, metacardId);
    this.zipFile = createResults.getRight();
    this.relativePath = createResults.getLeft();
    this.catalogFramework = catalogFramework;
    this.xmlTransformer = xmlTransformer;
    this.zipPathGenerator = new ZipPathGenerator(metacardId);
  }

  String getRelativePath() {
    return relativePath;
  }

  void add(Metacard metacard)
      throws IOException, CatalogTransformerException, ResourceNotSupportedException, ZipException {
    if (MetacardUtils.isRevision(metacard)) {
      addHistoryMetacard(metacard);
    } else {
      addPrimaryMetacard(metacard);
    }
  }

  private void addPrimaryMetacard(Metacard metacard)
      throws CatalogTransformerException, IOException, ResourceNotSupportedException, ZipException {
    writePrimaryMetacardToZip(metacard);

    String id = metacard.getId();

    write(getContentUri(metacard), this::writePrimaryContentToZip, id);

    write(getOriginalContentUri(metacard), this::writeDerivedOriginalContentToZip, id);

    write(getOverviewContentUri(metacard), this::writeDerivedOverviewContentToZip, id);

    writePrimaryOtherDerivedContent(metacard, id);
  }

  private void writeOtherDerivedContent(
      Metacard metacard, String id, BiFunction<Integer, String, String> pather)
      throws IOException, ResourceNotSupportedException, ZipException {
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
      throws ZipException, IOException, ResourceNotSupportedException {
    writeOtherDerivedContent(metacard, id, zipPathGenerator::derivedOtherContentPath);
  }

  private void writeHistoryOtherDerivedContent(Metacard metacard, String id)
      throws IOException, ResourceNotSupportedException, ZipException {
    writeOtherDerivedContent(
        metacard,
        id,
        (index, name) -> zipPathGenerator.historyDerivedOtherContentPath(id, index, name));
  }

  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  private void write(Optional<URI> optionalUri, Writer writer, String metacardId)
      throws IOException, ResourceNotSupportedException, ZipException {
    if (optionalUri.isPresent()) {
      writer.write(metacardId, optionalUri.get());
    }
  }

  private void addHistoryMetacard(Metacard metacard)
      throws IOException, ResourceNotSupportedException, CatalogTransformerException, ZipException {
    writeHistoryMetacardToZip(metacard);

    String id = metacard.getId();

    write(getContentUri(metacard), this::writeHistoryContentToZip, id);

    write(getOriginalContentUri(metacard), this::writeHistoryDerivedOriginalContentToZip, id);

    write(getOverviewContentUri(metacard), this::writeHistoryDerivedOverviewContentToZip, id);

    writeHistoryOtherDerivedContent(metacard, id);
  }

  private static Pair<String, ZipFile> createInitialZipFile(
      String rootPath, String relativeBasePath, String metacardId) throws IOException {
    String relativePath = relativeBasePath + ".zip";
    String absolutePath = rootPath + File.separator + relativePath;
    try {

      return new ImmutablePair<>(relativePath, new ZipFile(absolutePath));
    } catch (ZipException e) {
      throw new IOException(
          String.format(
              "Unable to create initial ZipFile object while offlining a resource: outputPath=[%s] metacardId=[%s]",
              absolutePath, metacardId));
    }
  }

  private void writePrimaryContentToZip(String id, URI uri)
      throws IOException, ResourceNotSupportedException, ZipException {
    writer(id, uri, name -> zipPathGenerator.primaryContentPath(id, name));
  }

  private void writeDerivedOverviewContentToZip(String id, URI uri)
      throws IOException, ResourceNotSupportedException, ZipException {
    writer(id, uri, zipPathGenerator::derivedOverviewContentPath);
  }

  private void writeHistoryDerivedOverviewContentToZip(String id, URI uri)
      throws ZipException, IOException, ResourceNotSupportedException {
    writer(id, uri, name -> zipPathGenerator.historyDerivedOverviewContentPath(id, name));
  }

  private void writeDerivedOriginalContentToZip(String id, URI uri)
      throws IOException, ResourceNotSupportedException, ZipException {
    writer(id, uri, zipPathGenerator::derivedOriginalContentPath);
  }

  private void writeHistoryDerivedOriginalContentToZip(String id, URI uri)
      throws ZipException, IOException, ResourceNotSupportedException {
    writer(id, uri, name -> zipPathGenerator.historyDerivedOriginalContentPath(id, name));
  }

  private void writeHistoryContentToZip(String id, URI uri)
      throws IOException, ResourceNotSupportedException, ZipException {
    writer(id, uri, name -> zipPathGenerator.historyContentPath(id, name));
  }

  private void writer(String id, URI uri, Function<String, String> pather)
      throws IOException, ResourceNotSupportedException, ZipException {
    try {
      ResourceResponse localResource =
          catalogFramework.getLocalResource(new ResourceRequestByProductUri(uri));
      Resource resource = localResource.getResource();
      writeToZip(pather.apply(resource.getName()), resource);
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
      throws CatalogTransformerException, ZipException {
    BinaryContent binaryMetacard = getBinaryContent(metacard);
    writeToZip(zipPathGenerator.primaryMetacardPath(), binaryMetacard);
  }

  private void writeHistoryMetacardToZip(Metacard metacard)
      throws CatalogTransformerException, ZipException {
    BinaryContent binaryMetacard = getBinaryContent(metacard);
    writeToZip(zipPathGenerator.historyMetacardPath(metacard), binaryMetacard);
  }

  private void writeToZip(String fileName, BinaryContent binaryContent) throws ZipException {
    writeToZip(fileName, binaryContent.getInputStream());
  }

  private BinaryContent getBinaryContent(Metacard metacard) throws CatalogTransformerException {
    return xmlTransformer.transform(metacard, Collections.emptyMap());
  }

  private void writeToZip(String fileName, Resource resource) throws ZipException {
    writeToZip(fileName, resource.getInputStream());
  }

  private void writeToZip(String fileName, InputStream inputStream) throws ZipException {
    ZipParameters parameters = new ZipParameters();
    parameters.setSourceExternalStream(true);
    parameters.setFileNameInZip(fileName);

    zipFile.addStream(inputStream, parameters);
  }

  private interface Writer {
    void write(String metacardId, URI uri)
        throws IOException, ResourceNotSupportedException, ZipException;
  }
}
