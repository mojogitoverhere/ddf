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
package org.codice.ddf.catalog.ui.util;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.google.common.collect.ImmutableMap;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.impl.AttributeImpl;
import ddf.catalog.data.impl.MetacardImpl;
import ddf.catalog.data.impl.MetacardTypeImpl;
import ddf.catalog.data.impl.types.CoreAttributes;
import ddf.catalog.data.impl.types.OfflineAttributes;
import ddf.catalog.data.types.Core;
import ddf.catalog.data.types.Offline;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class OfflineUtilsTest {

  private static final String LOCATION_ONE = "location1";

  private static final String LOCATION_TWO = "location2";

  private static final String OFFLINE_COMMENT_ONE = "comment1";

  private static final String OFFLINE_COMMENT_TWO = "comment2";

  private static final byte[] BINARY_ONE = new byte[] {0};

  private static final byte[] BINARY_TWO = new byte[] {1};

  private OfflineUtils offlineUtils;

  @Before
  public void setup() {
    offlineUtils = new OfflineUtils();
  }

  @Test
  public void testUnchanged() {
    Metacard originalMetacard = createMetacard();
    Metacard newMetacard = createMetacard();

    assertThat(offlineUtils.isOfflineCommentOnlyUpdate(originalMetacard, newMetacard), is(true));
  }

  @Test
  public void testOriginalMetacardHasOneNonOfflineCommentMoreThanNewMetacard() {
    Metacard originalMetacard = createMetacard(Core.LOCATION, LOCATION_ONE);
    Metacard newMetacard = createMetacard();

    assertThat(offlineUtils.isOfflineCommentOnlyUpdate(originalMetacard, newMetacard), is(false));
  }

  @Test
  public void testNewMetacardHasOneNonOfflineCommentMoreThanOriginalMetacard() {
    Metacard originalMetacard = createMetacard();
    Metacard newMetacard = createMetacard(Core.LOCATION, LOCATION_ONE);

    assertThat(offlineUtils.isOfflineCommentOnlyUpdate(originalMetacard, newMetacard), is(false));
  }

  @Test
  public void testNewMetacardHasDifferentNonOfflineValuesThanOriginalMetacard() {
    Metacard originalMetacard = createMetacard(Core.LOCATION, LOCATION_ONE);
    Metacard newMetacard = createMetacard(Core.LOCATION, LOCATION_TWO);

    assertThat(offlineUtils.isOfflineCommentOnlyUpdate(originalMetacard, newMetacard), is(false));
  }

  @Test
  public void testNewMetacardHasDifferentOfflineValuesThanOriginalMetacard() {
    Metacard originalMetacard = createMetacard(Offline.OFFLINE_COMMENT, OFFLINE_COMMENT_ONE);
    Metacard newMetacard = createMetacard(Offline.OFFLINE_COMMENT, OFFLINE_COMMENT_TWO);

    assertThat(offlineUtils.isOfflineCommentOnlyUpdate(originalMetacard, newMetacard), is(true));
  }

  @Test
  public void testNewMetacardHasMultipleChanges() {
    Metacard originalMetacard =
        createMetacard(
            ImmutableMap.of(
                Offline.OFFLINE_COMMENT,
                Collections.singletonList(OFFLINE_COMMENT_ONE),
                Core.LOCATION,
                Collections.singletonList(LOCATION_ONE)));
    Metacard newMetacard =
        createMetacard(
            ImmutableMap.of(
                Offline.OFFLINE_COMMENT,
                Collections.singletonList(OFFLINE_COMMENT_TWO),
                Core.LOCATION,
                Collections.singletonList(LOCATION_TWO)));

    assertThat(offlineUtils.isOfflineCommentOnlyUpdate(originalMetacard, newMetacard), is(false));
  }

  @Test
  public void testWhenTheOnlyDifferenceIsAByteArray() {
    Metacard originalMetacard = createMetacard(Core.THUMBNAIL, BINARY_ONE);
    Metacard newMetacard = createMetacard(Core.THUMBNAIL, BINARY_TWO);

    assertThat(offlineUtils.isOfflineCommentOnlyUpdate(originalMetacard, newMetacard), is(false));
  }

  @Test
  public void testWhenTheOnlyDifferenceIsOfflineCommentAndMetacardsHaveArray() {
    Metacard originalMetacard =
        createMetacard(
            ImmutableMap.of(
                Offline.OFFLINE_COMMENT,
                Collections.singletonList(OFFLINE_COMMENT_ONE),
                Core.THUMBNAIL,
                Collections.singletonList(BINARY_ONE)));
    Metacard newMetacard =
        createMetacard(
            ImmutableMap.of(
                Offline.OFFLINE_COMMENT,
                Collections.singletonList(OFFLINE_COMMENT_TWO),
                Core.THUMBNAIL,
                Collections.singletonList(BINARY_ONE)));

    assertThat(offlineUtils.isOfflineCommentOnlyUpdate(originalMetacard, newMetacard), is(true));
  }

  private Metacard createMetacard() {
    return createMetacard(Collections.emptyMap());
  }

  private Metacard createMetacard(String attributeName, Serializable value) {
    return createMetacard(
        Collections.singletonMap(attributeName, Collections.singletonList(value)));
  }

  private Metacard createMetacard(Map<String, List<Serializable>> values) {

    Metacard metacard =
        new MetacardImpl(
            new MetacardTypeImpl(
                "test-metacard", Arrays.asList(new CoreAttributes(), new OfflineAttributes())));

    values
        .entrySet()
        .stream()
        .map(
            stringListEntry ->
                new AttributeImpl(stringListEntry.getKey(), stringListEntry.getValue()))
        .forEach(metacard::setAttribute);

    return metacard;
  }
}
