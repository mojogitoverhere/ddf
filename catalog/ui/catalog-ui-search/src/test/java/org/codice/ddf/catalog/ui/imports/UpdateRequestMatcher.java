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

import ddf.catalog.data.Metacard;
import ddf.catalog.operation.UpdateRequest;
import java.util.Map;
import org.mockito.ArgumentMatcher;

public class UpdateRequestMatcher extends ArgumentMatcher<UpdateRequest> {

  private String metacardId;

  private UpdateRequestMatcher(String metacardId) {
    this.metacardId = metacardId;
  }

  @Override
  public boolean matches(Object obj) {
    if (!(obj instanceof UpdateRequest)) {
      return false;
    }

    UpdateRequest updateRequest = (UpdateRequest) obj;

    return updateRequest
        .getUpdates()
        .stream()
        .map(Map.Entry::getValue)
        .map(Metacard::getId)
        .anyMatch(this::isTargetMetacardId);
  }

  private boolean isTargetMetacardId(String id) {
    return id.equals(metacardId);
  }

  public static UpdateRequest is(String metacardId) {
    return argThat(new UpdateRequestMatcher(metacardId));
  }
}
