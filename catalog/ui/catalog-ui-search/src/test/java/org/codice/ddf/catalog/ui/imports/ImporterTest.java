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

import static org.codice.ddf.catalog.ui.imports.EveryElementIs.everyElementIs;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import ddf.catalog.CatalogFramework;
import ddf.catalog.content.StorageException;
import ddf.catalog.content.StorageProvider;
import ddf.catalog.content.data.ContentItem;
import ddf.catalog.content.operation.CreateStorageRequest;
import ddf.catalog.data.Attribute;
import ddf.catalog.data.AttributeRegistry;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.Result;
import ddf.catalog.data.types.Offline;
import ddf.catalog.federation.FederationException;
import ddf.catalog.filter.FilterBuilder;
import ddf.catalog.filter.proxy.builder.GeotoolsFilterBuilder;
import ddf.catalog.operation.CreateRequest;
import ddf.catalog.operation.CreateResponse;
import ddf.catalog.operation.QueryRequest;
import ddf.catalog.operation.UpdateRequest;
import ddf.catalog.operation.UpdateResponse;
import ddf.catalog.operation.impl.QueryResponseImpl;
import ddf.catalog.source.IngestException;
import ddf.catalog.source.SourceUnavailableException;
import ddf.catalog.source.UnsupportedQueryException;
import ddf.catalog.transform.CatalogTransformerException;
import ddf.catalog.transform.InputTransformer;
import ddf.mime.MimeTypeMapper;
import ddf.mime.MimeTypeResolutionException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.ZipParameters;
import org.apache.shiro.subject.ExecutionException;
import org.codice.ddf.catalog.ui.task.TaskMonitor.Task;
import org.codice.ddf.catalog.ui.util.CatalogUtils;
import org.codice.ddf.catalog.ui.util.EndpointUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ImporterTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private InputTransformer inputTransformer;

  @Mock private CatalogFramework catalogFramework;

  @Mock private StorageProvider storageProvider;

  @Mock private Task task;

  @Mock private MimeTypeMapper mimeTypeMapper;

  @Mock private AttributeRegistry attributeRegistry;

  private FilterBuilder filterBuilder = new GeotoolsFilterBuilder();

  private Importer importer;

  @Before
  public void setup() throws MimeTypeResolutionException {
    InputStreamMatcher.clearCache();
    when(mimeTypeMapper.getMimeTypeForFileExtension(eq("dat")))
        .thenReturn("application/octet-stream");
    importer =
        new Importer(
            inputTransformer,
            catalogFramework,
            storageProvider,
            filterBuilder,
            mimeTypeMapper,
            new CatalogUtils(
                new EndpointUtil(
                    Collections.emptyList(),
                    catalogFramework,
                    filterBuilder,
                    Collections.emptyList(),
                    attributeRegistry),
                filterBuilder,
                catalogFramework)) {
          @Override
          <T> T executeAsSystem(Callable<T> callable) {
            try {
              return callable.call();
            } catch (Exception e) {
              throw new ExecutionException(e);
            }
          }
        };
  }

  @Test
  public void testArchiveWithExtraDirectories()
      throws IngestException, IOException, CatalogTransformerException, SourceUnavailableException,
          FederationException, UnsupportedQueryException, ZipException, ImportException {

    Metacard metacard = mockMetacardInCatalog("0123456789");

    File fileToImport =
        createTestZip(
            "metacards/012/0123456789/metacard/0123456789.xml",
            "metacards/012/0123456789/content/a.dat",
            "metacards/012/0123456789/unexpected",
            "metacards/not-three-digit/other-stuff");

    importFile(fileToImport);

    assertMetacardWasUpdatedInCatalogFramework(metacard);
  }

  @Test
  public void testInitialStatus()
      throws ImportException, IOException, CatalogTransformerException, IngestException,
          UnsupportedQueryException, SourceUnavailableException, ZipException, FederationException {

    mockMetacardNotInCatalog("0123456789");
    mockMetacardNotInCatalog("history1");
    mockMetacardNotInCatalog("history2");

    File fileToImport =
        createTestZip(
            "metacards/012/0123456789/metacard/0123456789.xml",
            "metacards/012/0123456789/derived/original/c.dat",
            "metacards/012/0123456789/derived/overview/b.dat",
            "metacards/012/0123456789/content/a.dat",
            "metacards/his/history1/metacard/history1.xml",
            "metacards/his/history1/derived/original/f.dat",
            "metacards/his/history1/derived/overview/e.dat",
            "metacards/his/history1/content/a.dat",
            "metacards/his/history2/metacard/history2.xml",
            "metacards/his/history2/derived/original/i.dat",
            "metacards/his/history2/derived/overview/h.dat",
            "metacards/his/history2/content/a.dat");

    importFile(fileToImport);
  }

  @Test
  public void testOnliningOfASingleMetacardWithoutDerivedContentOrHistoryMetacards()
      throws IOException, ZipException, CatalogTransformerException, ImportException,
          IngestException, UnsupportedQueryException, StorageException, SourceUnavailableException,
          FederationException {

    Metacard metacard = mockMetacardInCatalog("0123456789");

    File fileToImport =
        createTestZip(
            "metacards/012/0123456789/metacard/0123456789.xml",
            "metacards/012/0123456789/content/a.dat");

    importFile(fileToImport);

    assertMetacardWasUpdatedInCatalogFramework(metacard);

    assertContentFilesWereCreatedInStorageProvider(new ExpectedStorageProvider(metacard, "a.dat"));
  }

  @Test
  public void testOnliningOfASingleMetacardWithoutContent()
      throws IOException, ZipException, CatalogTransformerException, ImportException,
          IngestException, UnsupportedQueryException, SourceUnavailableException,
          FederationException {

    Metacard metacard = mockMetacardInCatalog("0123456789");

    File fileToImport = createTestZip("metacards/012/0123456789/metacard/0123456789.xml");

    importFile(fileToImport);

    assertMetacardWasUpdatedInCatalogFramework(metacard);
  }

  @Test
  public void testImportingOfASingleMetacardWithourDerivedContentOrHistoryMetacards()
      throws IngestException, UnsupportedQueryException, CatalogTransformerException, IOException,
          ZipException, StorageException, ImportException, SourceUnavailableException,
          FederationException {
    Metacard metacard = mockMetacardNotInCatalog("0123456789");

    File fileToImport =
        createTestZip(
            "metacards/012/0123456789/metacard/0123456789.xml",
            "metacards/012/0123456789/content/a.dat");

    importFile(fileToImport);

    assertMetacardWasCreatedInCatalogFramework(metacard);

    assertContentFilesWereCreatedInStorageProvider(new ExpectedStorageProvider(metacard, "a.dat"));
  }

  @Test
  public void testOnliningOfASingleMetacardWithDerivedContentAndNoHistoryMetacards()
      throws ZipException, UnsupportedQueryException, IOException, CatalogTransformerException,
          ImportException, StorageException, IngestException, SourceUnavailableException,
          FederationException {

    Metacard metacard = mockMetacardInCatalog("ef302deb60a54d4fa15c7b03c3ed96b5");

    File fileToImport =
        createTestZip(
            "metacards/ef3/ef302deb60a54d4fa15c7b03c3ed96b5/metacard/ef302deb60a54d4fa15c7b03c3ed96b5.xml",
            "metacards/ef3/ef302deb60a54d4fa15c7b03c3ed96b5/content/a.dat",
            "metacards/ef3/ef302deb60a54d4fa15c7b03c3ed96b5/derived/original/b.dat");

    importFile(fileToImport);

    assertMetacardWasUpdatedInCatalogFramework(metacard);

    assertContentFilesWereCreatedInStorageProvider(
        new ExpectedStorageProvider(metacard, "a.dat", "b.dat"));
  }

  @Test
  public void testImportingOfASingleMetacardWithDerivedContentAndNoHistoryMetacards()
      throws ZipException, UnsupportedQueryException, IOException, CatalogTransformerException,
          ImportException, StorageException, IngestException, SourceUnavailableException,
          FederationException {

    Metacard metacard = mockMetacardNotInCatalog("ef302deb60a54d4fa15c7b03c3ed96b5");

    File fileToImport =
        createTestZip(
            "metacards/ef3/ef302deb60a54d4fa15c7b03c3ed96b5/metacard/ef302deb60a54d4fa15c7b03c3ed96b5.xml",
            "metacards/ef3/ef302deb60a54d4fa15c7b03c3ed96b5/content/a.dat",
            "metacards/ef3/ef302deb60a54d4fa15c7b03c3ed96b5/derived/original/b.dat");

    importFile(fileToImport);

    assertMetacardWasCreatedInCatalogFramework(metacard);

    assertContentFilesWereCreatedInStorageProvider(
        new ExpectedStorageProvider(metacard, "a.dat", "b.dat"));
  }

  @Test
  public void testOnliningOfASingleMetacardWithOneHistoryMetacardAndNoDerivedContent()
      throws IngestException, UnsupportedQueryException, CatalogTransformerException, IOException,
          ZipException, StorageException, ImportException, SourceUnavailableException,
          FederationException {

    Metacard metacard = mockMetacardInCatalog("ef302deb60a54d4fa15c7b03c3ed96b5");
    Metacard history = mockMetacardInCatalog("ef302deb60a54d4fa15c7b03c3ed96b6");

    File fileToImport =
        createTestZip(
            "metacards/ef3/ef302deb60a54d4fa15c7b03c3ed96b5/metacard/ef302deb60a54d4fa15c7b03c3ed96b5.xml",
            "metacards/ef3/ef302deb60a54d4fa15c7b03c3ed96b5/content/a.dat",
            "metacards/ef3/ef302deb60a54d4fa15c7b03c3ed96b6/metacard/ef302deb60a54d4fa15c7b03c3ed96b6.xml",
            "metacards/ef3/ef302deb60a54d4fa15c7b03c3ed96b6/content/b.dat");

    importFile(fileToImport);

    assertMetacardWasUpdatedInCatalogFramework(metacard, history);

    assertContentFilesWereCreatedInStorageProvider(
        new ExpectedStorageProvider(metacard, "a.dat"),
        new ExpectedStorageProvider(history, "b.dat"));
  }

  @Test
  public void testImportingOfASingleMetacardWithOneHistoryMetacardAndNoDerivedContent()
      throws IngestException, UnsupportedQueryException, CatalogTransformerException, IOException,
          ZipException, StorageException, ImportException, SourceUnavailableException,
          FederationException {

    Metacard metacard = mockMetacardNotInCatalog("ef302deb60a54d4fa15c7b03c3ed96b5");
    Metacard history = mockMetacardNotInCatalog("ef302deb60a54d4fa15c7b03c3ed96b6");

    File fileToImport =
        createTestZip(
            "metacards/ef3/ef302deb60a54d4fa15c7b03c3ed96b5/metacard/ef302deb60a54d4fa15c7b03c3ed96b5.xml",
            "metacards/ef3/ef302deb60a54d4fa15c7b03c3ed96b5/content/a.dat",
            "metacards/ef3/ef302deb60a54d4fa15c7b03c3ed96b6/metacard/ef302deb60a54d4fa15c7b03c3ed96b6.xml",
            "metacards/ef3/ef302deb60a54d4fa15c7b03c3ed96b6/content/b.dat");

    importFile(fileToImport);

    assertMetacardWasCreatedInCatalogFramework(metacard, history);

    assertContentFilesWereCreatedInStorageProvider(
        new ExpectedStorageProvider(metacard, "a.dat"),
        new ExpectedStorageProvider(history, "b.dat"));
  }

  @Test
  public void testOnlingOfASingleMetacardWithOneHistoryMetacardThatHasDerivedContent()
      throws IngestException, UnsupportedQueryException, CatalogTransformerException, IOException,
          ZipException, StorageException, ImportException, SourceUnavailableException,
          FederationException {

    Metacard metacard = mockMetacardInCatalog("ef302deb60a54d4fa15c7b03c3ed96b5");
    Metacard history = mockMetacardInCatalog("ef302deb60a54d4fa15c7b03c3ed96b6");

    File fileToImport =
        createTestZip(
            "metacards/ef3/ef302deb60a54d4fa15c7b03c3ed96b5/metacard/ef302deb60a54d4fa15c7b03c3ed96b5.xml",
            "metacards/ef3/ef302deb60a54d4fa15c7b03c3ed96b5/content/a.dat",
            "metacards/ef3/ef302deb60a54d4fa15c7b03c3ed96b6/metacard/ef302deb60a54d4fa15c7b03c3ed96b6.xml",
            "metacards/ef3/ef302deb60a54d4fa15c7b03c3ed96b6/content/b.dat",
            "metacards/ef3/ef302deb60a54d4fa15c7b03c3ed96b6/derived/original/c.dat");

    importFile(fileToImport);

    assertMetacardWasUpdatedInCatalogFramework(metacard, history);

    assertContentFilesWereCreatedInStorageProvider(
        new ExpectedStorageProvider(metacard, "a.dat"),
        new ExpectedStorageProvider(history, "b.dat", "c.dat"));
  }

  @Test
  public void testImportingOfASingleMetacardWithOneHistoryMetacardThatHasDerivedContent()
      throws IngestException, UnsupportedQueryException, CatalogTransformerException, IOException,
          ZipException, StorageException, ImportException, SourceUnavailableException,
          FederationException {

    Metacard metacard = mockMetacardNotInCatalog("ef302deb60a54d4fa15c7b03c3ed96b5");
    Metacard history = mockMetacardNotInCatalog("ef302deb60a54d4fa15c7b03c3ed96b6");

    File fileToImport =
        createTestZip(
            "metacards/ef3/ef302deb60a54d4fa15c7b03c3ed96b5/metacard/ef302deb60a54d4fa15c7b03c3ed96b5.xml",
            "metacards/ef3/ef302deb60a54d4fa15c7b03c3ed96b5/content/a.dat",
            "metacards/ef3/ef302deb60a54d4fa15c7b03c3ed96b6/metacard/ef302deb60a54d4fa15c7b03c3ed96b6.xml",
            "metacards/ef3/ef302deb60a54d4fa15c7b03c3ed96b6/content/b.dat",
            "metacards/ef3/ef302deb60a54d4fa15c7b03c3ed96b6/derived/original/c.dat");

    importFile(fileToImport);

    assertMetacardWasCreatedInCatalogFramework(metacard, history);

    assertContentFilesWereCreatedInStorageProvider(
        new ExpectedStorageProvider(metacard, "a.dat"),
        new ExpectedStorageProvider(history, "b.dat", "c.dat"));
  }

  @Test
  public void testOnliningTwoMetacardsWhereTheFirstOneFails()
      throws SourceUnavailableException, IngestException, UnsupportedQueryException,
          CatalogTransformerException, IOException, ZipException, StorageException, ImportException,
          FederationException {

    mockMetacardInCatalogWithError("ef302deb60a54d4fa15c7b03c3ed96b5");
    Metacard metacard2 = mockMetacardInCatalog("ef302deb60a54d4fa15c7b03c3ed96b6");

    File fileToImport =
        createTestZip(
            "metacards/ef3/ef302deb60a54d4fa15c7b03c3ed96b5/metacard/ef302deb60a54d4fa15c7b03c3ed96b5.xml",
            "metacards/ef3/ef302deb60a54d4fa15c7b03c3ed96b5/content/a.dat",
            "metacards/ef3/ef302deb60a54d4fa15c7b03c3ed96b6/metacard/ef302deb60a54d4fa15c7b03c3ed96b6.xml",
            "metacards/ef3/ef302deb60a54d4fa15c7b03c3ed96b6/content/b.dat");

    Set<String> importResults = importFile(fileToImport);

    assertThat(importResults, is(Collections.singleton("ef302deb60a54d4fa15c7b03c3ed96b5")));

    assertMetacardWasUpdatedInCatalogFramework(metacard2);

    assertContentFilesWereCreatedInStorageProvider(new ExpectedStorageProvider(metacard2, "b.dat"));
  }

  private class ExpectedStorageProvider {

    private Metacard metacard;
    private String[] filenames;

    Metacard getMetacard() {
      return metacard;
    }

    String[] getFilenames() {
      return filenames;
    }

    ExpectedStorageProvider(Metacard metacard, String... filenames) {
      this.metacard = metacard;
      this.filenames = filenames;
    }
  }

  private void assertContentFilesWereCreatedInStorageProvider(
      ExpectedStorageProvider... expectedStorageProviders) throws StorageException {

    ArgumentCaptor<CreateStorageRequest> createStorageRequestArgumentCaptor =
        ArgumentCaptor.forClass(CreateStorageRequest.class);

    verify(storageProvider, times(expectedStorageProviders.length))
        .create(createStorageRequestArgumentCaptor.capture());

    List<CreateStorageRequest> storageRequests = createStorageRequestArgumentCaptor.getAllValues();

    assertThat(storageRequests, hasSize(expectedStorageProviders.length));

    for (ExpectedStorageProvider expectedStorageProvider : expectedStorageProviders) {

      List<CreateStorageRequest> storageRequestsThatMatchTheExpectedMetacard =
          storageRequests
              .stream()
              .filter(
                  createStorageRequest ->
                      doAllContentItemsHaveThisMetacard(
                          expectedStorageProvider.getMetacard(), createStorageRequest))
              .collect(Collectors.toList());

      assertThat(storageRequestsThatMatchTheExpectedMetacard, hasSize(1));

      List<ContentItem> contentItems =
          storageRequestsThatMatchTheExpectedMetacard
              .stream()
              .map(CreateStorageRequest::getContentItems)
              .flatMap(List::stream)
              .collect(Collectors.toList());

      assertThat(contentItems, hasSize(expectedStorageProvider.getFilenames().length));

      assertThat(
          contentItems.stream().map(ContentItem::getFilename).collect(Collectors.toList()),
          containsInAnyOrder(expectedStorageProvider.getFilenames()));

      assertThat(
          contentItems.stream().map(ContentItem::getMetacard).collect(Collectors.toList()),
          everyElementIs(expectedStorageProvider.getMetacard()));
    }
  }

  private boolean doAllContentItemsHaveThisMetacard(
      Metacard expectedMetacard, CreateStorageRequest createStorageRequest) {
    return createStorageRequest
        .getContentItems()
        .stream()
        .map(ContentItem::getMetacard)
        .allMatch(metacard -> metacard.equals(expectedMetacard));
  }

  private void assertMetacardWasUpdatedInCatalogFramework(Metacard... metacards)
      throws IngestException, SourceUnavailableException {
    ArgumentCaptor<UpdateRequest> updateRequestArgumentCaptor =
        ArgumentCaptor.forClass(UpdateRequest.class);

    verify(catalogFramework, atLeast(metacards.length))
        .update(updateRequestArgumentCaptor.capture());

    List<Metacard> updatedMetacards =
        updateRequestArgumentCaptor
            .getAllValues()
            .stream()
            .map(UpdateRequest::getUpdates)
            .flatMap(List::stream)
            .map(Map.Entry::getValue)
            .collect(Collectors.toList());

    assertThat(updatedMetacards.size(), greaterThanOrEqualTo(metacards.length));
    assertThat(updatedMetacards, hasItems(metacards));
  }

  private void assertMetacardWasCreatedInCatalogFramework(Metacard... metacards)
      throws IngestException, SourceUnavailableException {
    ArgumentCaptor<CreateRequest> createRequestArgumentCaptor =
        ArgumentCaptor.forClass(CreateRequest.class);

    verify(catalogFramework, times(metacards.length)).create(createRequestArgumentCaptor.capture());

    List<Metacard> createdMetacards = toMetacards(createRequestArgumentCaptor);

    assertThat(createdMetacards, hasSize(metacards.length));
    assertThat(createdMetacards, containsInAnyOrder(metacards));
  }

  private List<Metacard> toMetacards(ArgumentCaptor<CreateRequest> argumentCaptor) {
    return argumentCaptor
        .getAllValues()
        .stream()
        .map(CreateRequest::getMetacards)
        .flatMap(List::stream)
        .collect(Collectors.toList());
  }

  private Metacard mockMetacard(String id) throws IOException, CatalogTransformerException {
    Metacard metacard = mock(Metacard.class);
    when(inputTransformer.transform(InputStreamMatcher.is(id))).thenReturn(metacard);
    when(metacard.getId()).thenReturn(id);
    return metacard;
  }

  @SuppressWarnings("SameParameterValue")
  private Metacard mockMetacardInCatalog(String id)
      throws IOException, CatalogTransformerException, UnsupportedQueryException, IngestException,
          SourceUnavailableException, FederationException {
    Metacard metacard = mockMetacard(id);
    Attribute offlinedByAttribute = mock(Attribute.class);
    when(offlinedByAttribute.getValue()).thenReturn("admin@localhost");
    when(metacard.getAttribute(Offline.OFFLINED_BY)).thenReturn(offlinedByAttribute);
    Result result = mock(Result.class);
    when(result.getMetacard()).thenReturn(metacard);
    when(catalogFramework.query(QueryRequestMatcher.is(id)))
        .then(
            invocationOnMock ->
                new QueryResponseImpl(
                    (QueryRequest) invocationOnMock.getArguments()[0],
                    Collections.singletonList(result),
                    1));
    UpdateResponse updateResponse = mock(UpdateResponse.class);
    when(updateResponse.getProcessingErrors()).thenReturn(Collections.emptySet());
    when(catalogFramework.update(UpdateRequestMatcher.is(id))).thenReturn(updateResponse);
    return metacard;
  }

  @SuppressWarnings("SameParameterValue")
  private void mockMetacardInCatalogWithError(String id)
      throws IOException, CatalogTransformerException, UnsupportedQueryException, IngestException,
          SourceUnavailableException, FederationException {
    Metacard metacard = mockMetacard(id);
    Attribute offlinedByAttribute = mock(Attribute.class);
    when(offlinedByAttribute.getValue()).thenReturn("admin@localhost");
    when(metacard.getAttribute(Offline.OFFLINED_BY)).thenReturn(offlinedByAttribute);
    Result result = mock(Result.class);
    when(result.getMetacard()).thenReturn(metacard);
    when(catalogFramework.query(QueryRequestMatcher.is(id)))
        .then(
            invocationOnMock ->
                new QueryResponseImpl(
                    (QueryRequest) invocationOnMock.getArguments()[0],
                    Collections.singletonList(result),
                    1));
    when(catalogFramework.update(UpdateRequestMatcher.is(id))).thenThrow(new IngestException());
  }

  @SuppressWarnings("UnusedReturnValue")
  private Metacard mockMetacardNotInCatalog(String id)
      throws IOException, CatalogTransformerException, UnsupportedQueryException, IngestException,
          SourceUnavailableException, FederationException {
    Metacard metacard = mockMetacard(id);
    when(catalogFramework.query(QueryRequestMatcher.is(id)))
        .then(
            invocationOnMock ->
                new QueryResponseImpl(
                    (QueryRequest) invocationOnMock.getArguments()[0], Collections.emptyList(), 0));
    CreateResponse createResponse = mock(CreateResponse.class);
    when(createResponse.getProcessingErrors()).thenReturn(Collections.emptySet());
    when(catalogFramework.create(CreateRequestMatcher.is(id))).thenReturn(createResponse);
    return metacard;
  }

  /**
   * Create a test zip with empty files. FYI: {@link File#createTempFile(String, String, File)}
   * creates a zero-byte file, but {@link ZipFile#ZipFile(File)} fails, so we must delete the temp
   * file first.
   */
  private File createTestZip(String... files) throws ZipException, IOException {
    File zip = File.createTempFile("testZip", ".zip", temporaryFolder.getRoot());
    boolean status = zip.delete();
    if (!status) {
      throw new IOException(
          String.format("Unable to delete test file: path=[%s]", zip.getAbsolutePath()));
    }
    ZipFile zipFile = new ZipFile(zip);
    ZipParameters zipParameters = new ZipParameters();

    for (String file : files) {
      zipParameters.setFileNameInZip(file);
      zipParameters.setSourceExternalStream(true);
      zipFile.addStream(new ByteArrayInputStream(file.getBytes()), zipParameters);
    }

    return zip;
  }

  private Set<String> importFile(File fileToImport) throws ImportException {
    return importer.importArchive(fileToImport, task);
  }
}
