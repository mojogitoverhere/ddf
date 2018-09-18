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
package org.codice.ddf.catalog.ui.imports;

import static org.mockito.Matchers.argThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.mockito.ArgumentMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InputStreamMatcher extends ArgumentMatcher<InputStream> {

  private static final Logger LOGGER = LoggerFactory.getLogger(InputStreamMatcher.class);

  private final String metacardId;

  private static final Map<InputStream, String> CACHE = new HashMap<>();

  private InputStreamMatcher(String metacardId) {
    this.metacardId = metacardId;
  }

  @Override
  public boolean matches(Object obj) {
    if (!(obj instanceof InputStream)) {
      return false;
    }

    InputStream inputStream = (InputStream) obj;

    String content =
        CACHE.computeIfAbsent(
            inputStream,
            inputStream1 -> {
              try {
                return IOUtils.toString(inputStream1, "UTF-8");
              } catch (IOException e) {
                LOGGER.info("Unable to read input stream.", e);
                return null;
              }
            });

    return content != null && content.endsWith("/" + metacardId + ".xml");
  }

  static void clearCache() {
    CACHE.clear();
  }

  public static InputStream is(String metacardId) {
    return argThat(new InputStreamMatcher(metacardId));
  }
}
