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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ddf.catalog.content.StorageException;
import ddf.catalog.content.StorageProvider;
import ddf.catalog.content.operation.DeleteStorageRequest;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.impl.AttributeImpl;
import ddf.catalog.data.impl.MetacardImpl;
import ddf.catalog.data.impl.MetacardTypeImpl;
import ddf.catalog.data.impl.types.CoreAttributes;
import ddf.catalog.data.impl.types.OfflineAttributes;
import ddf.catalog.data.types.Core;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.codice.ddf.commands.catalog.export.IdAndUriMetacard;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class OfflineContentProcessorTest {

  private static final String ID = "1";

  private static final String SOURCE_ID = "TheSourceId";

  private static final URI RESOURCE_URI = URI.create("content://123");

  private static final URI DERIVED_URI = URI.create("content://abc");

  private static final URI DERIVED_URI_2 = URI.create("content://xyz");

  private OfflineContentProcessor processor;

  @Mock private StorageProvider storageProvider;

  @Before
  public void setup() {
    processor = new OfflineContentProcessor(storageProvider);
  }

  @Test
  public void testProcessWithNoUrisOrUrls() throws StorageException {
    Metacard metacard = createMetacard(ID, null, Collections.emptyList());

    ArgumentCaptor<DeleteStorageRequest> deleteStorageRequestForDelete =
        ArgumentCaptor.forClass(DeleteStorageRequest.class);
    ArgumentCaptor<DeleteStorageRequest> deleteStorageRequestForCommit =
        ArgumentCaptor.forClass(DeleteStorageRequest.class);

    processor.process(metacard);

    verify(storageProvider, times(0)).delete(deleteStorageRequestForDelete.capture());
    verify(storageProvider, times(0)).commit(deleteStorageRequestForCommit.capture());
  }

  @Test
  public void testProcessResourceUriOnly() throws StorageException {
    Metacard metacard = createMetacard(ID, RESOURCE_URI, Collections.emptyList());

    ArgumentCaptor<DeleteStorageRequest> deleteStorageRequestForDelete =
        ArgumentCaptor.forClass(DeleteStorageRequest.class);
    ArgumentCaptor<DeleteStorageRequest> deleteStorageRequestForCommit =
        ArgumentCaptor.forClass(DeleteStorageRequest.class);

    processor.process(metacard);

    verify(storageProvider, times(1)).delete(deleteStorageRequestForDelete.capture());
    verify(storageProvider, times(1)).commit(deleteStorageRequestForCommit.capture());

    List<URI> uri = Collections.singletonList(RESOURCE_URI);
    Set<URI> urisToDelete = new HashSet<>(uri);
    for (DeleteStorageRequest deleteStorageRequest : deleteStorageRequestForDelete.getAllValues()) {
      assertDeleteStorageRequest(deleteStorageRequest, ID, urisToDelete);
    }
    assertThat(urisToDelete.isEmpty(), is(true));

    Set<URI> urisToCommit = new HashSet<>(uri);
    for (DeleteStorageRequest deleteStorageRequest : deleteStorageRequestForCommit.getAllValues()) {
      assertDeleteStorageRequest(deleteStorageRequest, ID, urisToCommit);
    }
    assertThat(urisToCommit.isEmpty(), is(true));
  }

  @Test
  public void testProcessDerivedResourceUriOnly() throws StorageException {
    List<URI> derivedUris = Collections.singletonList(DERIVED_URI);
    Metacard metacard = createMetacard(ID, null, derivedUris);

    ArgumentCaptor<DeleteStorageRequest> deleteStorageRequestForDelete =
        ArgumentCaptor.forClass(DeleteStorageRequest.class);
    ArgumentCaptor<DeleteStorageRequest> deleteStorageRequestForCommit =
        ArgumentCaptor.forClass(DeleteStorageRequest.class);

    processor.process(metacard);

    verify(storageProvider, times(1)).delete(deleteStorageRequestForDelete.capture());
    verify(storageProvider, times(1)).commit(deleteStorageRequestForCommit.capture());

    Set<URI> urisToDelete = new HashSet<>(derivedUris);
    for (DeleteStorageRequest deleteStorageRequest : deleteStorageRequestForDelete.getAllValues()) {
      assertDeleteStorageRequest(deleteStorageRequest, ID, urisToDelete);
    }
    assertThat(urisToDelete.isEmpty(), is(true));

    Set<URI> urisToCommit = new HashSet<>(derivedUris);
    for (DeleteStorageRequest deleteStorageRequest : deleteStorageRequestForCommit.getAllValues()) {
      assertDeleteStorageRequest(deleteStorageRequest, ID, urisToCommit);
    }
    assertThat(urisToCommit.isEmpty(), is(true));
  }

  @Test
  public void testProcessMultipleDerivedResourceUriOnly() throws StorageException {
    List<URI> derivedUris = Arrays.asList(DERIVED_URI, DERIVED_URI_2);
    Metacard metacard = createMetacard(ID, null, Arrays.asList(DERIVED_URI, DERIVED_URI_2));

    ArgumentCaptor<DeleteStorageRequest> deleteStorageRequestForDelete =
        ArgumentCaptor.forClass(DeleteStorageRequest.class);
    ArgumentCaptor<DeleteStorageRequest> deleteStorageRequestForCommit =
        ArgumentCaptor.forClass(DeleteStorageRequest.class);

    processor.process(metacard);

    verify(storageProvider, times(2)).delete(deleteStorageRequestForDelete.capture());
    verify(storageProvider, times(2)).commit(deleteStorageRequestForCommit.capture());

    Set<URI> urisToDelete = new HashSet<>(derivedUris);
    for (DeleteStorageRequest deleteStorageRequest : deleteStorageRequestForDelete.getAllValues()) {
      assertDeleteStorageRequest(deleteStorageRequest, ID, urisToDelete);
    }
    assertThat(urisToDelete.isEmpty(), is(true));

    Set<URI> urisToCommit = new HashSet<>(derivedUris);
    for (DeleteStorageRequest deleteStorageRequest : deleteStorageRequestForCommit.getAllValues()) {
      assertDeleteStorageRequest(deleteStorageRequest, ID, urisToCommit);
    }
    assertThat(urisToCommit.isEmpty(), is(true));
  }

  @Test
  public void testProcessMultipleDerivedResourceUriOnlyAndResourceUri() throws StorageException {
    List<URI> allUris = Arrays.asList(RESOURCE_URI, DERIVED_URI, DERIVED_URI_2);
    Metacard metacard = createMetacard(ID, RESOURCE_URI, Arrays.asList(DERIVED_URI, DERIVED_URI_2));

    ArgumentCaptor<DeleteStorageRequest> deleteStorageRequestForDelete =
        ArgumentCaptor.forClass(DeleteStorageRequest.class);
    ArgumentCaptor<DeleteStorageRequest> deleteStorageRequestForCommit =
        ArgumentCaptor.forClass(DeleteStorageRequest.class);

    processor.process(metacard);

    verify(storageProvider, times(3)).delete(deleteStorageRequestForDelete.capture());
    verify(storageProvider, times(3)).commit(deleteStorageRequestForCommit.capture());

    Set<URI> urisToDelete = new HashSet<>(allUris);
    for (DeleteStorageRequest deleteStorageRequest : deleteStorageRequestForDelete.getAllValues()) {
      assertDeleteStorageRequest(deleteStorageRequest, ID, urisToDelete);
    }
    assertThat(urisToDelete.isEmpty(), is(true));

    Set<URI> urisToCommit = new HashSet<>(allUris);
    for (DeleteStorageRequest deleteStorageRequest : deleteStorageRequestForCommit.getAllValues()) {
      assertDeleteStorageRequest(deleteStorageRequest, ID, urisToCommit);
    }
    assertThat(urisToCommit.isEmpty(), is(true));
  }

  private void assertDeleteStorageRequest(DeleteStorageRequest request, String id, Set<URI> uris) {
    assertThat(request.getMetacards(), hasSize(1));
    assertThat(request.getMetacards().get(0), instanceOf(IdAndUriMetacard.class));

    IdAndUriMetacard actual = (IdAndUriMetacard) request.getMetacards().get(0);

    assertThat(actual.getId(), is(id));
    URI uri = actual.getResourceURI();
    assertThat(uri, notNullValue());
    assertThat(uris.contains(uri), is(true));
    uris.remove(uri);
  }

  private Metacard createMetacard(
      String metacardId, URI resourceUri, List<URI> derivedResourceUris) {
    Metacard metacard =
        new MetacardImpl(
            new MetacardTypeImpl(
                "offline-type", Arrays.asList(new OfflineAttributes(), new CoreAttributes())));
    metacard.setAttribute(new AttributeImpl(Core.ID, metacardId));
    metacard.setSourceId(SOURCE_ID);
    if (resourceUri != null) {
      metacard.setAttribute(new AttributeImpl(Core.RESOURCE_URI, resourceUri.toString()));
    }
    if (derivedResourceUris != null) {
      metacard.setAttribute(
          new AttributeImpl(
              Core.DERIVED_RESOURCE_URI,
              derivedResourceUris.stream().map(URI::toString).collect(Collectors.toList())));
    }
    return metacard;
  }
}
