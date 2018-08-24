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

import ddf.catalog.data.Attribute;
import ddf.catalog.data.AttributeDescriptor;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.impl.AttributeImpl;
import ddf.catalog.data.impl.MetacardImpl;
import ddf.catalog.data.types.Core;
import ddf.catalog.data.types.Offline;
import ddf.security.SubjectUtils;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;

class OfflineAttributeSetter {

  private static final Set<String> ATTRIBUTES_TO_REMOVE =
      new HashSet<>(
          Arrays.asList(
              Core.RESOURCE_URI,
              Core.RESOURCE_DOWNLOAD_URL,
              Core.DERIVED_RESOURCE_URI,
              Core.DERIVED_RESOURCE_DOWNLOAD_URL));

  Metacard setOfflineAttributes(Metacard metacard, String location, String path) {
    metacard.setAttribute(new AttributeImpl(Offline.OFFLINE_COMMENT, location));
    metacard.setAttribute(new AttributeImpl(Offline.OFFLINE_LOCATION_PATH, path));
    metacard.setAttribute(new AttributeImpl(Offline.OFFLINED_BY, getWhoAmI()));
    metacard.setAttribute(new AttributeImpl(Offline.OFFLINE_DATE, new Date()));
    return removeAttributes(metacard);
  }

  private String getWhoAmI() {
    Subject subject = SecurityUtils.getSubject();

    String whoami = SubjectUtils.getEmailAddress(subject);

    if (StringUtils.isEmpty(whoami)) {
      whoami = SubjectUtils.getName(subject);
    }

    return whoami;
  }

  private Metacard removeAttributes(Metacard metacard) {
    List<String> attributeNames =
        metacard
            .getMetacardType()
            .getAttributeDescriptors()
            .stream()
            .map(AttributeDescriptor::getName)
            .collect(Collectors.toList());

    List<Attribute> attributesToSave =
        attributeNames
            .stream()
            .filter(s -> !ATTRIBUTES_TO_REMOVE.contains(s))
            .map(metacard::getAttribute)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    Metacard newMetacard = new MetacardImpl(metacard.getMetacardType());
    newMetacard.setSourceId(metacard.getSourceId());

    attributesToSave.forEach(newMetacard::setAttribute);

    return newMetacard;
  }
}
