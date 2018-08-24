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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ddf.catalog.CatalogFramework;
import ddf.catalog.content.StorageException;
import ddf.catalog.content.StorageProvider;
import ddf.catalog.content.operation.DeleteStorageRequest;
import ddf.catalog.core.versioning.MetacardVersion;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.Result;
import ddf.catalog.data.impl.AttributeImpl;
import ddf.catalog.data.impl.BinaryContentImpl;
import ddf.catalog.data.impl.MetacardImpl;
import ddf.catalog.data.impl.MetacardTypeImpl;
import ddf.catalog.data.impl.types.CoreAttributes;
import ddf.catalog.data.impl.types.OfflineAttributes;
import ddf.catalog.data.types.Core;
import ddf.catalog.data.types.Offline;
import ddf.catalog.federation.FederationException;
import ddf.catalog.filter.proxy.builder.GeotoolsFilterBuilder;
import ddf.catalog.operation.QueryResponse;
import ddf.catalog.operation.ResourceRequest;
import ddf.catalog.operation.ResourceResponse;
import ddf.catalog.operation.UpdateRequest;
import ddf.catalog.operation.UpdateResponse;
import ddf.catalog.operation.impl.ResourceRequestByProductUri;
import ddf.catalog.resource.Resource;
import ddf.catalog.resource.ResourceNotFoundException;
import ddf.catalog.resource.ResourceNotSupportedException;
import ddf.catalog.source.IngestException;
import ddf.catalog.source.SourceUnavailableException;
import ddf.catalog.source.UnsupportedQueryException;
import ddf.catalog.transform.CatalogTransformerException;
import ddf.catalog.transform.MetacardTransformer;
import ddf.security.SubjectUtils;
import ddf.security.assertion.SecurityAssertion;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.FileHeader;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.codice.ddf.catalog.ui.config.ConfigurationApplication;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.opengis.filter.Filter;
import org.opensaml.core.xml.schema.XSString;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.core.AttributeStatement;

@RunWith(MockitoJUnitRunner.class)
public class OfflineResourcesTest {

  private static final String METACARD_ID = "0123456789";

  private static final URI RESOURCE_URI = URI.create("content://mycontent");

  private static final String LOCATION = "this is the location";

  private static final String WHOAMI = "admin@local";

  private static final String SOURCE_ID = "TheSourceId";

  @Mock private CatalogFramework catalogFramework;

  @Mock private MetacardTransformer xmlTransformer;

  @Mock private ConfigurationApplication configurationApplication;

  @Mock private StorageProvider storageProvider;

  @Mock private Function<String, Filter> filterSupplier;

  @Mock private Supplier<String> whoAmISupplier;

  @Mock private Filter filter;

  @Mock private QueryResponse queryResponse;

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private final List<Result> metacardResults = new LinkedList<>();

  private final List<Result> historyMetacardResults = new LinkedList<>();

  private final List<URI> allResourceUris = new LinkedList<>();

  @Before
  public void setup() {
    metacardResults.clear();
    historyMetacardResults.clear();
    allResourceUris.clear();
  }

  /** Test that a metacard with no history metacards or derived content is offlined. */
  @Test
  public void testBasicCase()
      throws UnsupportedQueryException, SourceUnavailableException, FederationException,
          IngestException, ZipException, CatalogTransformerException, ResourceNotFoundException,
          IOException, ResourceNotSupportedException, StorageException {

    createMetacard(METACARD_ID, metacardResults::add, Collections.emptyList(), RESOURCE_URI);

    mockBasicLibraryCalls();

    mockResource(RESOURCE_URI, "my-content.dat", "data-data-data".getBytes());

    OfflineResources offlineResources = createOfflineResources();

    Map<String, String> results = offlineResources.moveResourceOffline(METACARD_ID, LOCATION);

    assertThatOfflineResults(results);

    assertThatOfflineAttributesAreSet();

    assertThatTheContentsWereDeleted();

    assertThatZipContainsTheCorrectFiles(
        "metacards/012/0123456789/metacard/0123456789.xml",
        "metacards/012/0123456789/content/my-content.dat");
  }

  private OfflineResources createOfflineResources() {
    return new OfflineResources(
        catalogFramework,
        xmlTransformer,
        configurationApplication,
        storageProvider,
        new GeotoolsFilterBuilder());
  }

  @Test
  public void testWithOverviewAndOriginalDerviedContent()
      throws UnsupportedQueryException, SourceUnavailableException, FederationException,
          CatalogTransformerException, ResourceNotFoundException, IOException,
          ResourceNotSupportedException, IngestException, StorageException, ZipException {

    URI overviewUri = URI.create("content://mycontent#overview");
    URI originalUri = URI.create("content://mycontent#original");

    createMetacard(
        METACARD_ID,
        metacardResults::add,
        Collections.emptyList(),
        RESOURCE_URI,
        overviewUri,
        originalUri);

    mockBasicLibraryCalls();

    mockResource(RESOURCE_URI, "my-content.dat", "data-data-data".getBytes());
    mockResource(overviewUri, "my-overview.dat", "overview-overview-overview".getBytes());
    mockResource(originalUri, "my-original.dat", "original-original-original".getBytes());

    OfflineResources offlineResources = createOfflineResources();

    Map<String, String> results = offlineResources.moveResourceOffline(METACARD_ID, LOCATION);

    assertThatOfflineResults(results);

    assertThatOfflineAttributesAreSet();

    assertThatTheContentsWereDeleted();

    assertThatZipContainsTheCorrectFiles(
        "metacards/012/0123456789/metacard/0123456789.xml",
        "metacards/012/0123456789/content/my-content.dat",
        "metacards/012/0123456789/derived/overview/my-overview.dat",
        "metacards/012/0123456789/derived/original/my-original.dat");
  }

  @Test
  public void testWithOtherDerviedContent()
      throws UnsupportedQueryException, SourceUnavailableException, FederationException,
          CatalogTransformerException, ResourceNotFoundException, IOException,
          ResourceNotSupportedException, IngestException, StorageException, ZipException {

    URI otherUri = URI.create("content://mycontent-other");

    createMetacard(
        METACARD_ID, metacardResults::add, Collections.emptyList(), RESOURCE_URI, otherUri);

    mockBasicLibraryCalls();

    mockResource(RESOURCE_URI, "my-content.dat", "data-data-data".getBytes());
    mockResource(otherUri, "my-other.dat", "other-other-other".getBytes());

    OfflineResources offlineResources = createOfflineResources();

    Map<String, String> results = offlineResources.moveResourceOffline(METACARD_ID, LOCATION);

    assertThatOfflineResults(results);

    assertThatOfflineAttributesAreSet();

    assertThatTheContentsWereDeleted();

    assertThatZipContainsTheCorrectFiles(
        "metacards/012/0123456789/metacard/0123456789.xml",
        "metacards/012/0123456789/content/my-content.dat",
        "metacards/012/0123456789/derived/other-1/my-other.dat");
  }

  @Test
  public void testWithHistoryMetacards()
      throws CatalogTransformerException, UnsupportedQueryException, SourceUnavailableException,
          FederationException, ResourceNotFoundException, IOException,
          ResourceNotSupportedException, IngestException, StorageException, ZipException {

    URI overviewUri = URI.create("content:metacard#overview");
    URI originalUri = URI.create("content:metacard#original");

    URI history1ResourceUri = URI.create("content:history1");
    URI overviewHistory1Uri = URI.create("content:history1#overview");
    URI originalHistory1Uri = URI.create("content:history1#original");

    URI history2ResourceUri = URI.create("content:history2");
    URI overviewHistory2Uri = URI.create("content:history2#overview");
    URI originalHistory2Uri = URI.create("content:history2#original");
    URI otherHistory2Uri = URI.create("content:history2#other");

    createMetacard(
        METACARD_ID,
        metacardResults::add,
        Collections.emptyList(),
        RESOURCE_URI,
        overviewUri,
        originalUri);
    createMetacard(
        "history1",
        historyMetacardResults::add,
        Collections.singletonList(MetacardVersion.VERSION_TAG),
        history1ResourceUri,
        overviewHistory1Uri,
        originalHistory1Uri);
    createMetacard(
        "history2",
        historyMetacardResults::add,
        Collections.singletonList(MetacardVersion.VERSION_TAG),
        history2ResourceUri,
        overviewHistory2Uri,
        originalHistory2Uri,
        otherHistory2Uri);

    mockBasicLibraryCalls();

    mockResource(RESOURCE_URI, "a.dat", "a".getBytes());
    mockResource(overviewUri, "b.dat", "b".getBytes());
    mockResource(originalUri, "c.dat", "c".getBytes());

    mockResource(history1ResourceUri, "d.dat", "d".getBytes());
    mockResource(overviewHistory1Uri, "e.dat", "e".getBytes());
    mockResource(originalHistory1Uri, "f.dat", "f".getBytes());

    mockResource(history2ResourceUri, "g.dat", "g".getBytes());
    mockResource(overviewHistory2Uri, "h.dat", "h".getBytes());
    mockResource(originalHistory2Uri, "i.dat", "i".getBytes());
    mockResource(otherHistory2Uri, "j.dat", "j".getBytes());

    OfflineResources offlineResources = createOfflineResources();

    Map<String, String> results = offlineResources.moveResourceOffline(METACARD_ID, LOCATION);

    assertThatOfflineResults(results, "history1", "history2");

    assertThatOfflineAttributesAreSet();

    assertThatTheContentsWereDeleted();

    assertThatZipContainsTheCorrectFiles(
        "metacards/012/0123456789/metacard/0123456789.xml",
        "metacards/012/0123456789/content/a.dat",
        "metacards/012/0123456789/derived/overview/b.dat",
        "metacards/012/0123456789/derived/original/c.dat",
        "metacards/012/0123456789/history/history1/metacard/history1.xml",
        "metacards/012/0123456789/history/history1/content/d.dat",
        "metacards/012/0123456789/history/history1/derived/overview/e.dat",
        "metacards/012/0123456789/history/history1/derived/original/f.dat",
        "metacards/012/0123456789/history/history2/metacard/history2.xml",
        "metacards/012/0123456789/history/history2/content/g.dat",
        "metacards/012/0123456789/history/history2/derived/overview/h.dat",
        "metacards/012/0123456789/history/history2/derived/original/i.dat",
        "metacards/012/0123456789/history/history2/derived/other-1/j.dat");
  }

  private void mockBasicLibraryCalls()
      throws UnsupportedQueryException, SourceUnavailableException, FederationException,
          IngestException {
    when(filterSupplier.apply(METACARD_ID)).thenReturn(filter);
    when(whoAmISupplier.get()).thenReturn(WHOAMI);
    when(configurationApplication.getOfflineRootPath())
        .thenReturn(folder.getRoot().getAbsolutePath());
    when(queryResponse.getResults())
        .then(
            (Answer<List<Result>>)
                invocationOnMock ->
                    Stream.concat(metacardResults.stream(), historyMetacardResults.stream())
                        .collect(Collectors.toList()));
    when(catalogFramework.query(any())).thenReturn(queryResponse);
    UpdateResponse updateResponse = mock(UpdateResponse.class);
    when(updateResponse.getProcessingErrors()).thenReturn(Collections.emptySet());
    when(catalogFramework.update(any(UpdateRequest.class))).thenReturn(updateResponse);

    Subject subject = mock(Subject.class);
    PrincipalCollection principalCollection = mock(PrincipalCollection.class);
    when(subject.getPrincipals()).thenReturn(principalCollection);
    SecurityAssertion securityAssertion = mock(SecurityAssertion.class);
    when(principalCollection.oneByType(any())).thenReturn(securityAssertion);
    AttributeStatement attributeStatement = mock(AttributeStatement.class);
    when(securityAssertion.getAttributeStatements())
        .thenReturn(Collections.singletonList(attributeStatement));
    Attribute attribute = mock(Attribute.class);
    when(attributeStatement.getAttributes()).thenReturn(Collections.singletonList(attribute));
    when(attribute.getName()).thenReturn(SubjectUtils.EMAIL_ADDRESS_CLAIM_URI);
    XSString xsString = mock(XSString.class);
    when(xsString.getValue()).thenReturn(WHOAMI);
    when(attribute.getAttributeValues()).thenReturn(Collections.singletonList(xsString));
    ThreadContext.bind(subject);
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
    allResourceUris.add(uri);
  }

  private void createMetacard(
      String metacardId,
      Consumer<Result> resultConsumer,
      List<String> tags,
      URI resourceUri,
      URI... derivedResourceUris)
      throws CatalogTransformerException {
    Metacard metacard =
        new MetacardImpl(
            new MetacardTypeImpl(
                "offline-type", Arrays.asList(new OfflineAttributes(), new CoreAttributes())));
    metacard.setSourceId(SOURCE_ID);
    metacard.setAttribute(new AttributeImpl(Core.RESOURCE_URI, resourceUri.toString()));
    metacard.setAttribute(new AttributeImpl(Core.ID, metacardId));
    metacard.setAttribute(
        new AttributeImpl(
            Core.DERIVED_RESOURCE_URI,
            Arrays.stream(derivedResourceUris).map(URI::toString).collect(Collectors.toList())));
    metacard.setAttribute(new AttributeImpl(Metacard.TAGS, (Serializable) tags));

    Result result = mock(Result.class);
    when(result.getMetacard()).thenReturn(metacard);
    resultConsumer.accept(result);

    when(xmlTransformer.transform(eq(metacard), any()))
        .thenReturn(new BinaryContentImpl(new ByteArrayInputStream("<xml/>".getBytes())));
  }

  private void assertThatOfflineResults(Map<String, String> results, String... historyMetacardIds) {

    Map<String, String> expected = new HashMap<>();
    expected.put(METACARD_ID, "");
    Arrays.stream(historyMetacardIds).forEach(s -> expected.put(s, ""));

    assertThat(results, is(expected));
  }

  private void assertThatZipContainsTheCorrectFiles(String... expectedFileNames)
      throws ZipException {
    ZipFile zipFile =
        new ZipFile(folder.getRoot().getAbsolutePath() + File.separator + METACARD_ID + ".zip");

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

  private void assertDeleteStorageRequest(List<DeleteStorageRequest> requests, List<URI> uris) {
    List<Metacard> deletedMetacards =
        requests
            .stream()
            .map(DeleteStorageRequest::getMetacards)
            .flatMap(List::stream)
            .collect(Collectors.toList());
    assertThat(deletedMetacards, hasSize(uris.size()));

    List<String> ids =
        Stream.concat(metacardResults.stream(), historyMetacardResults.stream())
            .map(Result::getMetacard)
            .map(Metacard::getId)
            .collect(Collectors.toList());

    assertThat(
        deletedMetacards.stream().map(Metacard::getId).distinct().collect(Collectors.toList()),
        containsInAnyOrder(ids.toArray(new String[] {})));
    assertThat(
        deletedMetacards.stream().map(Metacard::getResourceURI).collect(Collectors.toList()),
        containsInAnyOrder(uris.toArray(new URI[] {})));
  }

  private void assertThatTheContentsWereDeleted() throws StorageException {
    ArgumentCaptor<DeleteStorageRequest> deleteStorageRequestDeleteArgumentCaptor =
        ArgumentCaptor.forClass(DeleteStorageRequest.class);
    ArgumentCaptor<DeleteStorageRequest> deleteStorageRequestCommitArgumentCaptor =
        ArgumentCaptor.forClass(DeleteStorageRequest.class);

    verify(storageProvider, times(allResourceUris.size()))
        .delete(deleteStorageRequestDeleteArgumentCaptor.capture());
    verify(storageProvider, times(allResourceUris.size()))
        .commit(deleteStorageRequestCommitArgumentCaptor.capture());

    assertDeleteStorageRequest(
        deleteStorageRequestDeleteArgumentCaptor.getAllValues(), allResourceUris);
    assertDeleteStorageRequest(
        deleteStorageRequestCommitArgumentCaptor.getAllValues(), allResourceUris);
  }

  private void assertThatOfflineAttributesAreSet()
      throws IngestException, SourceUnavailableException {
    ArgumentCaptor<UpdateRequest> updateRequestArgumentCaptor =
        ArgumentCaptor.forClass(UpdateRequest.class);
    verify(catalogFramework, times(metacardResults.size()))
        .update(updateRequestArgumentCaptor.capture());

    List<Metacard> updatedMetacards =
        updateRequestArgumentCaptor
            .getAllValues()
            .stream()
            .map(UpdateRequest::getUpdates)
            .flatMap(List::stream)
            .map(Map.Entry::getValue)
            .collect(Collectors.toList());

    assertThat(updatedMetacards, hasSize(metacardResults.size()));

    for (Metacard metacard : updatedMetacards) {
      assertThat(metacard.getAttribute(Offline.OFFLINE_COMMENT).getValue(), is(LOCATION));
      assertThat(
          metacard.getAttribute(Offline.OFFLINE_LOCATION_PATH).getValue(),
          is(METACARD_ID + ".zip"));
      assertThat(metacard.getAttribute(Offline.OFFLINE_DATE).getValue(), notNullValue());
      assertThat(metacard.getAttribute(Offline.OFFLINED_BY).getValue(), is(WHOAMI));
      assertThat(metacard.getSourceId(), is(SOURCE_ID));
    }
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

  private static ResourceRequest resourceRequestEq(ResourceRequestByProductUri resourceRequest) {
    return argThat(new RequestMatcher(resourceRequest));
  }
}
