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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.Result;
import ddf.catalog.data.impl.MetacardImpl;
import ddf.catalog.operation.Query;
import ddf.catalog.operation.ResourceRequest;
import ddf.catalog.operation.ResourceResponse;
import ddf.catalog.plugin.PolicyResponse;
import ddf.catalog.plugin.StopProcessingException;
import ddf.security.permission.KeyValuePermission;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.shiro.subject.Subject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;

public class DeleteMetacardPolicyPluginTest {

  private static final String RESOURCE = "resource";

  private static final String PERMISSION_KEY =
      "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/role";

  private static final String PERMISSION_VALUE = "admin";

  private Subject subject;

  private DeleteMetacardPolicyPlugin plugin;

  @Before
  public void setup() {
    plugin = new DeleteMetacardPolicyPlugin();
    plugin.setPermission(PERMISSION_KEY + "=" + PERMISSION_VALUE);
    subject = mock(Subject.class);
  }

  @Test
  public void testIsAllowedToDeleteIsTrue() {
    when(subject.isPermitted(Matchers.any(KeyValuePermission.class))).thenReturn(true);

    boolean canQuery = plugin.isAllowedToDelete(subject);
    assertThat(canQuery, is(true));
  }

  @Test
  public void testIsAllowedToDeleteIsTrueWithInvalidPermission() {
    plugin.setPermission("some=invalid=permission");
    boolean canQuery = plugin.isAllowedToDelete(subject);
    assertThat(canQuery, is(true));
  }

  @Test
  public void testIsAllowedToDeleteIsFalse() {
    when(subject.isPermitted(Matchers.any(KeyValuePermission.class))).thenReturn(false);

    plugin.setPermission(PERMISSION_KEY + "=" + PERMISSION_VALUE);
    boolean canQuery = plugin.isAllowedToDelete(subject);
    assertThat(canQuery, is(false));
  }

  @Test
  public void testProcessPreDelete() throws StopProcessingException {
    List<Metacard> metacards = Collections.singletonList(getMetacard(RESOURCE));
    PolicyResponse response = plugin.processPreDelete(metacards, null);
    assertPolicyResponse(response);
  }

  @Test
  public void testProcessPreDeleteWithoutPermissionSet() throws StopProcessingException {
    plugin.setPermission(null);
    List<Metacard> metacards = Collections.singletonList(getMetacard(RESOURCE));
    PolicyResponse response = plugin.processPreDelete(metacards, null);
    assertEmptyPolicyResponse(response);
  }

  @Test
  public void testProcessPreDeleteNonResourceMetacard() throws StopProcessingException {
    List<Metacard> metacards = Collections.singletonList(getMetacard("other"));
    PolicyResponse response = plugin.processPreDelete(metacards, null);
    assertEmptyPolicyResponse(response);
  }

  @Test
  public void testProcessPreDeleteWithNoEqualsSignInPermission() throws StopProcessingException {
    plugin.setPermission("does-not-contain-an-equals-sign");
    List<Metacard> metacards = Collections.singletonList(getMetacard(RESOURCE));
    PolicyResponse response = plugin.processPreDelete(metacards, null);
    assertEmptyPolicyResponse(response);
  }

  @Test
  public void testProcessPreDeleteWithTooManyEqualsSignsInPermission()
      throws StopProcessingException {
    plugin.setPermission("has=too=many=equals=signs");
    List<Metacard> metacards = Collections.singletonList(getMetacard(RESOURCE));
    PolicyResponse response = plugin.processPreDelete(metacards, null);
    assertEmptyPolicyResponse(response);
  }

  @Test
  public void testProcessPreDeleteWithNoPermissionKey() throws StopProcessingException {
    plugin.setPermission("=some-role");
    List<Metacard> metacards = Collections.singletonList(getMetacard(RESOURCE));
    PolicyResponse response = plugin.processPreDelete(metacards, null);
    assertEmptyPolicyResponse(response);
  }

  @Test
  public void testProcessPreDeleteWithNoPermissionValue() throws StopProcessingException {
    plugin.setPermission("some-uri=");
    List<Metacard> metacards = Collections.singletonList(getMetacard(RESOURCE));
    PolicyResponse response = plugin.processPreDelete(metacards, null);
    assertEmptyPolicyResponse(response);
  }

  @Test
  public void testProcessPreDeleteAssortedMetacards() throws StopProcessingException {
    List<Metacard> metacards = Arrays.asList(getMetacard("other"), getMetacard(RESOURCE));
    PolicyResponse response = plugin.processPreDelete(metacards, null);
    assertPolicyResponse(response);
  }

  @Test
  public void testProcessPostDelete() throws StopProcessingException {
    PolicyResponse response = plugin.processPostDelete(getMetacard(RESOURCE), null);
    assertEmptyPolicyResponse(response);
  }

  @Test
  public void testProcessPreUpdate() throws StopProcessingException {
    PolicyResponse response = plugin.processPreUpdate(getMetacard(RESOURCE), null);
    assertEmptyPolicyResponse(response);
  }

  @Test
  public void testProcessPreQuery() throws StopProcessingException {
    PolicyResponse response = plugin.processPreQuery(mock(Query.class), null);
    assertEmptyPolicyResponse(response);
  }

  @Test
  public void testProcessPostQuery() throws StopProcessingException {
    PolicyResponse response = plugin.processPostQuery(mock(Result.class), null);
    assertEmptyPolicyResponse(response);
  }

  @Test
  public void testProcessPreResource() throws StopProcessingException {
    PolicyResponse response = plugin.processPreResource(mock(ResourceRequest.class));
    assertEmptyPolicyResponse(response);
  }

  @Test
  public void testProcessPostResource() throws StopProcessingException {
    PolicyResponse response =
        plugin.processPostResource(mock(ResourceResponse.class), mock(Metacard.class));
    assertEmptyPolicyResponse(response);
  }

  @Test
  public void testProcessPreCreate() throws StopProcessingException {
    PolicyResponse response = plugin.processPreCreate(getMetacard(RESOURCE), null);
    assertEmptyPolicyResponse(response);
  }

  private void assertEmptyPolicyResponse(PolicyResponse response) {
    assertThat(response.itemPolicy().keySet(), is(empty()));
    assertThat(response.operationPolicy().keySet(), is(empty()));
  }

  private void assertPolicyResponse(PolicyResponse response) {
    Map<String, Set<String>> operationPolicy = response.operationPolicy();
    assertThat(operationPolicy.keySet(), hasSize(1));
    Set<String> policies = operationPolicy.get(PERMISSION_KEY);
    assertThat(policies, hasSize(1));
    assertThat(policies, contains(PERMISSION_VALUE));

    assertThat(response.itemPolicy().keySet(), is(empty()));
  }

  private Metacard getMetacard(String tag) {
    MetacardImpl metacard = new MetacardImpl();
    metacard.setTags(ImmutableSet.of(tag));
    return metacard;
  }
}
