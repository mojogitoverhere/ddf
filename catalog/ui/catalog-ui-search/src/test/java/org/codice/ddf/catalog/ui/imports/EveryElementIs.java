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

import ddf.catalog.data.Metacard;
import java.util.List;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

class EveryElementIs extends TypeSafeMatcher<List<Metacard>> {

  private Metacard expectedMetacard;

  private EveryElementIs(Metacard metacard) {
    this.expectedMetacard = metacard;
  }

  static EveryElementIs everyElementIs(Metacard metacard) {
    return new EveryElementIs(metacard);
  }

  @Override
  public void describeTo(Description description) {
    description.appendText("every element must be ").appendValue(expectedMetacard);
  }

  @Override
  protected boolean matchesSafely(List<Metacard> metacards) {
    return metacards.stream().allMatch(this::isExpectedMetacard);
  }

  private boolean isExpectedMetacard(Object metacard) {
    return expectedMetacard.equals(metacard);
  }
}
