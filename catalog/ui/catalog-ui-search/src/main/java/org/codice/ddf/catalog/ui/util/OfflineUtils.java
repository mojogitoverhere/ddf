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
package org.codice.ddf.catalog.ui.util;

import ddf.catalog.data.Attribute;
import ddf.catalog.data.AttributeDescriptor;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.types.Offline;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.ImmutablePair;

public class OfflineUtils {

  /** Determine that if at most, only the offline comment has changed. */
  public boolean isOfflineCommentOnlyUpdate(Metacard originalMetacard, Metacard newMetacard) {

    Set<ImmutablePair<AttributeDescriptor, Attribute>> originalAtributes =
        getAttributesExceptOfflineComment(originalMetacard);
    Set<ImmutablePair<AttributeDescriptor, Attribute>> newAttributes =
        getAttributesExceptOfflineComment(newMetacard);

    return originalAtributes.equals(newAttributes);
  }

  private Set<ImmutablePair<AttributeDescriptor, Attribute>> getAttributesExceptOfflineComment(
      Metacard metacard) {
    return metacard
        .getMetacardType()
        .getAttributeDescriptors()
        .stream()
        .map(
            attributeDescriptor ->
                ImmutablePair.of(
                    attributeDescriptor, metacard.getAttribute(attributeDescriptor.getName())))
        .filter(this::isNotOfflineComment)
        .collect(Collectors.toSet());
  }

  private boolean isNotOfflineComment(ImmutablePair<AttributeDescriptor, Attribute> pair) {
    return !Offline.OFFLINE_COMMENT.equals(pair.left.getName());
  }
}
