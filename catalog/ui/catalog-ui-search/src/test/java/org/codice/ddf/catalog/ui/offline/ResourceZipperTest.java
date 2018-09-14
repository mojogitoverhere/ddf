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

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ddf.catalog.CatalogFramework;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.impl.AttributeImpl;
import ddf.catalog.data.impl.BinaryContentImpl;
import ddf.catalog.data.impl.MetacardImpl;
import ddf.catalog.data.impl.MetacardTypeImpl;
import ddf.catalog.data.impl.types.CoreAttributes;
import ddf.catalog.data.types.Core;
import ddf.catalog.operation.ResourceRequest;
import ddf.catalog.operation.ResourceResponse;
import ddf.catalog.operation.impl.ResourceRequestByProductUri;
import ddf.catalog.resource.Resource;
import ddf.catalog.resource.ResourceNotFoundException;
import ddf.catalog.resource.ResourceNotSupportedException;
import ddf.catalog.transform.CatalogTransformerException;
import ddf.catalog.transform.MetacardTransformer;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.FileHeader;
import org.codice.ddf.catalog.ui.content.exports.ExportType;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ResourceZipperTest {

  private static final String METACARD_ID = "0123456789";

  private static final String METACARD_ID_2 = "abcdefghik";

  private static final URI RESOURCE_URI = URI.create("content://" + METACARD_ID);

  private static final URI OVERVIEW_URI = URI.create("content://mycontent#overview");

  private static final URI ORIGINAL_URI = URI.create("content://mycontent#original");

  private static final URI OTHER_URI = URI.create("content://mycontent-other");

  private ResourceZipper resourceZipper;

  @Mock private CatalogFramework catalogFramework;

  @Mock private MetacardTransformer transformer;

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  @Before
  public void setup() throws IOException, CatalogTransformerException {
    setupTransformer();

    String pathToZip = getPathToZip();
    resourceZipper = new ResourceZipper(catalogFramework, transformer, pathToZip);
  }

  @Test
  public void testAddMetadataOnly()
      throws ZipException, ResourceNotSupportedException, CatalogTransformerException, IOException {
    Metacard metacard = createMetacard(METACARD_ID, RESOURCE_URI, Collections.emptyList());
    resourceZipper.add(metacard, ExportType.METADATA_ONLY);

    assertThatZipContainsTheCorrectFiles("metacards/012/0123456789/metacard/0123456789.xml");
  }

  @Test
  public void testAddContentOnly()
      throws ZipException, ResourceNotSupportedException, CatalogTransformerException, IOException,
          ResourceNotFoundException {
    mockResource(RESOURCE_URI, "my-content.dat", "data-data-data".getBytes());

    Metacard metacard = createMetacard(METACARD_ID, RESOURCE_URI, Collections.emptyList());
    resourceZipper.add(metacard, ExportType.CONTENT_ONLY);

    assertThatZipContainsTheCorrectFiles("metacards/012/0123456789/content/my-content.dat");
  }

  @Test
  public void testAddMetadataAndContent()
      throws ZipException, ResourceNotSupportedException, CatalogTransformerException, IOException,
          ResourceNotFoundException {
    mockResource(RESOURCE_URI, "my-content.dat", "data-data-data".getBytes());
    mockResource(OVERVIEW_URI, "my-overview.dat", "overview-overview-overview".getBytes());
    mockResource(ORIGINAL_URI, "my-original.dat", "original-original-original".getBytes());

    Metacard metacard =
        createMetacard(METACARD_ID, RESOURCE_URI, Arrays.asList(OVERVIEW_URI, ORIGINAL_URI));
    resourceZipper.add(metacard, ExportType.METADATA_AND_CONTENT);

    assertThatZipContainsTheCorrectFiles(
        "metacards/012/0123456789/metacard/0123456789.xml",
        "metacards/012/0123456789/content/my-content.dat",
        "metacards/012/0123456789/derived/overview/my-overview.dat",
        "metacards/012/0123456789/derived/original/my-original.dat");
  }

  @Test
  public void testAddMetadataAndContentWithOtherDerivedContent()
      throws ZipException, ResourceNotSupportedException, CatalogTransformerException, IOException,
          ResourceNotFoundException {
    mockResource(RESOURCE_URI, "my-content.dat", "data-data-data".getBytes());
    mockResource(OTHER_URI, "my-other.dat", "other-other-other".getBytes());

    Metacard metacard =
        createMetacard(METACARD_ID, RESOURCE_URI, Collections.singletonList(OTHER_URI));
    resourceZipper.add(metacard, ExportType.METADATA_AND_CONTENT);

    assertThatZipContainsTheCorrectFiles(
        "metacards/012/0123456789/metacard/0123456789.xml",
        "metacards/012/0123456789/content/my-content.dat",
        "metacards/012/0123456789/derived/other-1/my-other.dat");
  }

  @Test
  public void testAddMultipleMetacards()
      throws ZipException, ResourceNotSupportedException, CatalogTransformerException, IOException,
          ResourceNotFoundException {
    mockResource(RESOURCE_URI, "my-content.dat", "data-data-data".getBytes());

    Metacard metacard1 = createMetacard(METACARD_ID, RESOURCE_URI, Collections.emptyList());
    Metacard metacard2 = createMetacard(METACARD_ID_2, RESOURCE_URI, Collections.emptyList());

    resourceZipper.add(metacard1, ExportType.METADATA_AND_CONTENT);
    resourceZipper.add(metacard2, ExportType.METADATA_AND_CONTENT);

    assertThatZipContainsTheCorrectFiles(
        "metacards/012/0123456789/metacard/0123456789.xml",
        "metacards/012/0123456789/content/my-content.dat",
        "metacards/abc/abcdefghik/metacard/abcdefghik.xml",
        "metacards/abc/abcdefghik/content/my-content.dat");
  }

  private void assertThatZipContainsTheCorrectFiles(String... expectedFileNames)
      throws ZipException {
    ZipFile zipFile = new ZipFile(getPathToZip());

    List<FileHeader> fileHeaders = new LinkedList<>();
    for (Object obj : zipFile.getFileHeaders()) {
      if (obj instanceof FileHeader) {
        fileHeaders.add((FileHeader) obj);
      }
    }

    List<String> fileNames =
        fileHeaders.stream().map(FileHeader::getFileName).collect(Collectors.toList());

    assertThat(fileNames, hasSize(expectedFileNames.length));
    assertThat(fileNames, containsInAnyOrder(expectedFileNames));
  }

  private void setupTransformer() throws CatalogTransformerException {
    when(transformer.transform(any(), any()))
        .thenReturn(new BinaryContentImpl(new ByteArrayInputStream("<xml/>".getBytes())));
  }

  private void mockResource(URI uri, String name, byte[] data)
      throws IOException, ResourceNotFoundException, ResourceNotSupportedException {
    ResourceRequestByProductUri resourceRequestByProductUri = new ResourceRequestByProductUri(uri);
    ResourceResponse resourceResponse = mock(ResourceResponse.class);
    Resource resource = mock(Resource.class);
    when(resourceResponse.getResource()).thenReturn(resource);
    when(resource.getName()).thenReturn(name);
    when(resource.getInputStream()).thenReturn(new ByteArrayInputStream(data));
    when(catalogFramework.getLocalResource(resourceRequestEq(resourceRequestByProductUri)))
        .thenReturn(resourceResponse);
  }

  private static ResourceRequest resourceRequestEq(ResourceRequestByProductUri resourceRequest) {
    return argThat(new RequestMatcher(resourceRequest));
  }

  private String getPathToZip() {
    return folder.getRoot().getAbsolutePath() + File.separator + METACARD_ID + ".zip";
  }

  private Metacard createMetacard(String id, URI resourceUri, List<URI> derivedUris) {
    Metacard metacard =
        new MetacardImpl(
            new MetacardTypeImpl("zipper", Collections.singletonList(new CoreAttributes())));
    metacard.setAttribute(new AttributeImpl(Metacard.ID, id));
    metacard.setAttribute(new AttributeImpl(Core.RESOURCE_URI, resourceUri.toString()));
    metacard.setAttribute(
        new AttributeImpl(
            Core.DERIVED_RESOURCE_URI,
            derivedUris.stream().map(URI::toString).collect(Collectors.toList())));
    return metacard;
  }

  private static class RequestMatcher extends ArgumentMatcher<ResourceRequest> {

    private ResourceRequestByProductUri resourceRequestByProductUri;

    RequestMatcher(ResourceRequestByProductUri resourceRequestByProductUri) {
      this.resourceRequestByProductUri = resourceRequestByProductUri;
    }

    @Override
    public boolean matches(Object o) {
      if (!(o instanceof ResourceRequestByProductUri)) return false;

      ResourceRequestByProductUri resourceRequestByProductUri1 = (ResourceRequestByProductUri) o;

      return resourceRequestByProductUri
          .getAttributeValue()
          .equals(resourceRequestByProductUri1.getAttributeValue());
    }
  }
}
