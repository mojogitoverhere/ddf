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

import ddf.catalog.content.StorageException;
import ddf.catalog.content.StorageProvider;
import ddf.catalog.content.operation.impl.DeleteStorageRequestImpl;
import ddf.catalog.data.Attribute;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.types.Core;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.codice.ddf.commands.catalog.export.IdAndUriMetacard;

class Content {

  private final StorageProvider storageProvider;

  Content(StorageProvider storageProvider) {
    this.storageProvider = storageProvider;
  }

  void deleteContent(Metacard metacard) throws StorageException {
    URI resourceUri = metacard.getResourceURI();
    if (resourceUri != null) {
      deleteContent(metacard.getId(), resourceUri);
    }
    Attribute derivedResourceUriAttribute = metacard.getAttribute(Core.DERIVED_RESOURCE_URI);
    if (derivedResourceUriAttribute != null) {
      List<URI> uris =
          derivedResourceUriAttribute
              .getValues()
              .stream()
              .filter(String.class::isInstance)
              .map(String.class::cast)
              .map(URI::create)
              .collect(Collectors.toList());
      for (URI uri : uris) {
        deleteContent(metacard.getId(), uri);
      }
    }
  }

  private void deleteContent(String metacardId, URI uri) throws StorageException {
    DeleteStorageRequestImpl deleteRequest =
        new DeleteStorageRequestImpl(
            Collections.singletonList(new IdAndUriMetacard(metacardId, uri)),
            metacardId,
            Collections.emptyMap());
    storageProvider.delete(deleteRequest);
    storageProvider.commit(deleteRequest);
  }
}
