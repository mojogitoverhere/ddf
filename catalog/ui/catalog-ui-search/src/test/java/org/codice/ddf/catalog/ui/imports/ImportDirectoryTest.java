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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Before;
import org.junit.Test;

public class ImportDirectoryTest {

  private ImportDirectory importDirectory;

  @Before
  public void setup() {
    importDirectory = new ImportDirectory();
  }

  @Test
  public void testSetRootDirectory() {
    importDirectory.setRootDirectory("/path/to/imports/");
    assertThat(importDirectory.getRootDirectory(), is("/path/to/imports/"));
  }

  @Test
  public void testSetRootDirectoryAddsTrailingSlash() {
    importDirectory.setRootDirectory("/path/to/imports");
    assertThat(importDirectory.getRootDirectory(), is("/path/to/imports/"));
  }
}
