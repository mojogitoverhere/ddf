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
package org.codice.ddf.catalog.ui.datausage;

import static spark.Spark.get;

import ddf.security.Subject;
import ddf.security.SubjectUtils;
import org.apache.shiro.SecurityUtils;
import org.boon.json.JsonFactory;
import org.boon.json.JsonParserFactory;
import org.boon.json.JsonSerializerFactory;
import org.boon.json.ObjectMapper;
import org.codice.ddf.persistence.PersistenceException;
import org.codice.ddf.persistence.attributes.AttributesStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.servlet.SparkApplication;

public class DataUsageApplication implements SparkApplication {

  private static final Logger LOGGER = LoggerFactory.getLogger(DataUsageApplication.class);

  private static final ObjectMapper JSON_MAPPER =
      JsonFactory.create(
          new JsonParserFactory(), new JsonSerializerFactory().includeNulls().includeEmpty());

  /**
   * If the user's data uage or data limit cannot be determined, then return this value as the data
   * usage remaining.
   */
  private static final long DEFAULT_USAGE_REMAINING = Long.MAX_VALUE;

  private static final long UNLIMITED_USAGE_REMAINING = Long.MAX_VALUE;

  private final AttributesStore attributesStore;

  public DataUsageApplication(AttributesStore attributesStore) {
    this.attributesStore = attributesStore;
  }

  @Override
  public void init() {
    get(
        "/datausage/remaining",
        (req, res) -> {
          String username = getUsername();

          try {
            long dataLimit = attributesStore.getDataLimitByUser(username);

            if (dataLimit < 0) {
              return UNLIMITED_USAGE_REMAINING;
            }

            long currentDataUsage = attributesStore.getCurrentDataUsageByUser(username);

            return dataLimit - currentDataUsage;
          } catch (PersistenceException e) {
            LOGGER.debug(
                "Unable to retrieve the data usage and data limit for user '{}'.", username, e);
          }
          return DEFAULT_USAGE_REMAINING;
        },
        JSON_MAPPER::toJson);
  }

  private String getUsername() {
    Subject subject = (Subject) SecurityUtils.getSubject();
    return SubjectUtils.getName(subject, null, true);
  }
}
