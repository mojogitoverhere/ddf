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
package ddf.catalog.data.types;

/**
 * <b> This code is experimental. While this interface is functional and tested, it may change or be
 * removed in a future version of the library. </b>
 */
public interface Offline {

  /**
   * The name of the storage location that was entered by the user. This is a descriptive location.
   * For example: "Third shelf from the right."
   */
  String OFFLINE_COMMENT = "ext.offline-comment";

  /** The relative file system path of the generated zip file in the secondary storage. */
  String OFFLINE_LOCATION_PATH = "ext.offline-location-path";

  /** The user who offlined the content. */
  String OFFLINED_BY = "ext.offlined-by";

  /** The date the content was offlined. */
  String OFFLINE_DATE = "ext.offline-date";
}
