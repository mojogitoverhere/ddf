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
package org.codice.ddf.catalog.ui.content.exports;

public enum ExportType {
  METADATA_AND_CONTENT,
  METADATA_ONLY,
  CONTENT_ONLY,
  INVALID;

  public static ExportType fromString(String type) {
    if (type == null) {
      return INVALID;
    }

    if (METADATA_AND_CONTENT.toString().equalsIgnoreCase(type)) {
      return METADATA_AND_CONTENT;
    } else if (METADATA_ONLY.toString().equalsIgnoreCase(type)) {
      return METADATA_ONLY;
    } else if (CONTENT_ONLY.toString().equalsIgnoreCase(type)) {
      return CONTENT_ONLY;
    } else {
      return INVALID;
    }
  }
}
