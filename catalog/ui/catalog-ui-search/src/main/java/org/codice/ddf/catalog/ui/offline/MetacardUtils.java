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

import ddf.catalog.core.versioning.MetacardVersion;
import ddf.catalog.data.Attribute;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.types.Offline;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.collections.CollectionUtils;

public class MetacardUtils {

  public static boolean isRevision(Metacard metacard) {
    Set<String> tags = metacard.getTags();
    return CollectionUtils.isNotEmpty(tags) && tags.contains(MetacardVersion.VERSION_TAG);
  }

  public static boolean isOfflined(Metacard metacard) {
    return Optional.ofNullable(metacard.getAttribute(Offline.OFFLINE_DATE))
        .map(Attribute::getValue)
        .isPresent();
  }

  private MetacardUtils() {}
}
