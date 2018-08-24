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
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ddf.catalog.content.StorageException;
import ddf.catalog.content.StorageProvider;
import ddf.catalog.content.operation.DeleteStorageRequest;
import ddf.catalog.data.Metacard;
import java.net.URI;
import org.codice.ddf.commands.catalog.export.IdAndUriMetacard;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class ContentTest {

  @Test
  public void testDeleteContent() throws StorageException {
    StorageProvider storageProvider = mock(StorageProvider.class);
    Content content = new Content(storageProvider);
    Metacard metacard = mock(Metacard.class);
    URI uri = URI.create("content://123");
    String id = "1";
    when(metacard.getResourceURI()).thenReturn(uri);
    when(metacard.getId()).thenReturn(id);

    ArgumentCaptor<DeleteStorageRequest> deleteStorageRequestArgumentCaptor1 =
        ArgumentCaptor.forClass(DeleteStorageRequest.class);
    ArgumentCaptor<DeleteStorageRequest> deleteStorageRequestArgumentCaptor2 =
        ArgumentCaptor.forClass(DeleteStorageRequest.class);

    content.deleteContent(metacard);

    verify(storageProvider).delete(deleteStorageRequestArgumentCaptor1.capture());
    verify(storageProvider).commit(deleteStorageRequestArgumentCaptor2.capture());

    assertThat(deleteStorageRequestArgumentCaptor1.getValue().getMetacards(), hasSize(1));
    assertThat(
        deleteStorageRequestArgumentCaptor1.getValue().getMetacards().get(0),
        instanceOf(IdAndUriMetacard.class));
    IdAndUriMetacard actual =
        (IdAndUriMetacard) deleteStorageRequestArgumentCaptor1.getValue().getMetacards().get(0);
    assertThat(actual.getId(), is(id));
    assertThat(actual.getResourceURI(), is(uri));
  }
}
