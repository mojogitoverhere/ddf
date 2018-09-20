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
package org.codice.ddf.catalog.ui.security;

import com.google.common.collect.ImmutableSet;
import ddf.security.permission.KeyValuePermission;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PermissionUtils {

  private static final Logger LOGGER = LoggerFactory.getLogger(PermissionUtils.class);

  private PermissionUtils() {}

  public static String[] parsePermission(String permission) {
    if (permission == null) {
      return null;
    }

    String[] keyValue = permission.split("=");
    if (keyValue.length != 2 || StringUtils.isAnyBlank(keyValue)) {
      LOGGER.debug(
          "Failed to parse the permission [{}]. It must be in the \"permission key=permission value\" format",
          permission);
      return null;
    }

    return keyValue;
  }

  public static boolean isPermitted(Subject subject, String permission) {
    String[] keyValue = parsePermission(permission);
    if (keyValue == null || subject == null) {
      return true;
    }

    return subject.isPermitted(new KeyValuePermission(keyValue[0], ImmutableSet.of(keyValue[1])));
  }
}
