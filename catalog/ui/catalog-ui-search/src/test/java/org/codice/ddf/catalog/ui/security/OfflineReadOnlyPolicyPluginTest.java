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

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ddf.catalog.Constants;
import ddf.catalog.data.Attribute;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.Result;
import ddf.catalog.data.impl.types.OfflineAttributes;
import ddf.catalog.data.types.Offline;
import ddf.catalog.operation.Query;
import ddf.catalog.operation.ResourceRequest;
import ddf.catalog.operation.ResourceResponse;
import ddf.catalog.plugin.PolicyResponse;
import ddf.security.Subject;
import ddf.security.permission.KeyValuePermission;
import java.io.Serializable;
import java.util.Collections;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.authz.Permission;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class OfflineReadOnlyPolicyPluginTest {

  private static final String ROLE_URI =
      "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/role";

  private static final String ROLE = "system-import";

  @Mock private Metacard metacard;

  @Mock private Query query;

  @Mock private Result result;

  @Mock private ResourceRequest resourceRequest;

  @Mock private ResourceResponse resourceResponse;

  @Mock private UserApplication userApplication;

  private OfflineReadOnlyPolicyPlugin offlineReadOnlyPolicyPlugin;

  @Before
  public void setup() {
    offlineReadOnlyPolicyPlugin = new OfflineReadOnlyPolicyPlugin(userApplication);
    offlineReadOnlyPolicyPlugin.setImportUserAttribute(ROLE_URI);
    offlineReadOnlyPolicyPlugin.setImportUserAttributeValue(ROLE);
    when(userApplication.isOfflineAllowed()).thenReturn(true);
  }

  @Test
  public void testProcessPreCreate() {
    PolicyResponse policyResponse =
        offlineReadOnlyPolicyPlugin.processPreCreate(metacard, Collections.emptyMap());
    assertEmptyPolicyResponse(policyResponse);
  }

  @Test
  public void testProcessPostDelete() {
    PolicyResponse policyResponse =
        offlineReadOnlyPolicyPlugin.processPostDelete(metacard, Collections.emptyMap());
    assertEmptyPolicyResponse(policyResponse);
  }

  @Test
  public void testProcessPreQuery() {
    PolicyResponse policyResponse =
        offlineReadOnlyPolicyPlugin.processPreQuery(query, Collections.emptyMap());
    assertEmptyPolicyResponse(policyResponse);
  }

  @Test
  public void testProcessPreDelete() {
    PolicyResponse policyResponse =
        offlineReadOnlyPolicyPlugin.processPreDelete(
            Collections.singletonList(metacard), Collections.emptyMap());
    assertEmptyPolicyResponse(policyResponse);
  }

  @Test
  public void testProcessPostQuery() {
    PolicyResponse policyResponse =
        offlineReadOnlyPolicyPlugin.processPostQuery(result, Collections.emptyMap());
    assertEmptyPolicyResponse(policyResponse);
  }

  @Test
  public void testProcessPreResource() {
    PolicyResponse policyResponse = offlineReadOnlyPolicyPlugin.processPreResource(resourceRequest);
    assertEmptyPolicyResponse(policyResponse);
  }

  @Test
  public void testProcessPostResource() {
    PolicyResponse policyResponse =
        offlineReadOnlyPolicyPlugin.processPostResource(resourceResponse, metacard);
    assertEmptyPolicyResponse(policyResponse);
  }

  @Test
  public void testIsAllowedToEdit() {

    Subject subject = mock(Subject.class);

    when(subject.isPermitted(any(Permission.class))).thenReturn(true);

    ArgumentCaptor<Permission> permissionArgumentCaptor = ArgumentCaptor.forClass(Permission.class);

    boolean isAllowed = offlineReadOnlyPolicyPlugin.isAllowedToEdit(subject);

    verify(subject).isPermitted(permissionArgumentCaptor.capture());

    assertThat(permissionArgumentCaptor.getValue(), instanceOf(KeyValuePermission.class));

    KeyValuePermission keyValuePermission =
        (KeyValuePermission) permissionArgumentCaptor.getValue();

    assertThat(keyValuePermission.getKey(), is(ROLE_URI));

    assertThat(keyValuePermission.getValues(), is(Collections.singleton(ROLE)));

    assertThat(isAllowed, is(true));
  }

  @Test
  public void testProcessPreUpdateForOfflinedMetacard() {

    String metacardId = "1";

    Metacard originalMetacard = mockOfflineMetacard(metacardId);

    PolicyResponse policyResponse = processPreUpdate(metacardId, originalMetacard);

    assertThat(policyResponse.operationPolicy(), is(Collections.emptyMap()));
    assertThat(
        policyResponse.itemPolicy(),
        is(Collections.singletonMap(ROLE_URI, Collections.singleton(ROLE))));
  }

  @Test
  public void testProcessPreUpdateForOfflinedMetacardWhileMissingUpdateMap() {

    mockOfflineMetacard("1");

    PolicyResponse policyResponse =
        offlineReadOnlyPolicyPlugin.processPreUpdate(metacard, Collections.emptyMap());

    assertEmptyPolicyResponse(policyResponse);
  }

  @Test
  public void testProcessPreUpdateForOnlinedMetacard() {

    String metacardId = "1";
    Metacard originalMetacard = mock(Metacard.class);
    when(metacard.getId()).thenReturn(metacardId);
    mockMetacardType(originalMetacard);
    mockMetacardType(metacard);

    PolicyResponse policyResponse = processPreUpdate(metacardId, originalMetacard);

    assertEmptyPolicyResponse(policyResponse);
  }

  @Test
  public void testProcessPreCreateWhenSecurityAttributeIsNull() {
    offlineReadOnlyPolicyPlugin.setImportUserAttribute(null);

    String metacardId = "1";

    Metacard originalMetacard = mockOfflineMetacard(metacardId);

    PolicyResponse policyResponse = processPreUpdate(metacardId, originalMetacard);

    assertEmptyPolicyResponse(policyResponse);
  }

  @Test
  public void testProcessPreCreateWhenSecurityAttributeIsEmpty() {
    offlineReadOnlyPolicyPlugin.setImportUserAttribute(StringUtils.EMPTY);

    String metacardId = "1";

    Metacard originalMetacard = mockOfflineMetacard(metacardId);

    PolicyResponse policyResponse = processPreUpdate(metacardId, originalMetacard);

    assertEmptyPolicyResponse(policyResponse);
  }

  @Test
  public void testProcessPreCreateWhenSecurityAttributeValueIsNull() {
    offlineReadOnlyPolicyPlugin.setImportUserAttributeValue(null);

    String metacardId = "1";

    Metacard originalMetacard = mockOfflineMetacard(metacardId);

    PolicyResponse policyResponse = processPreUpdate(metacardId, originalMetacard);

    assertEmptyPolicyResponse(policyResponse);
  }

  @Test
  public void testProcessPreCreateWhenSecurityAttributeValueIsEmpty() {
    offlineReadOnlyPolicyPlugin.setImportUserAttributeValue(StringUtils.EMPTY);

    String metacardId = "1";

    Metacard originalMetacard = mockOfflineMetacard(metacardId);

    PolicyResponse policyResponse = processPreUpdate(metacardId, originalMetacard);

    assertEmptyPolicyResponse(policyResponse);
  }

  private PolicyResponse processPreUpdate(String metacardId, Metacard originalMetacard) {
    return offlineReadOnlyPolicyPlugin.processPreUpdate(
        metacard,
        Collections.singletonMap(
            Constants.ATTRIBUTE_UPDATE_MAP_KEY,
            (Serializable) Collections.singletonMap(metacardId, originalMetacard)));
  }

  private Metacard mockOfflineMetacard(String metacardId) {
    Metacard originalMetacard = mock(Metacard.class);
    when(metacard.getId()).thenReturn(metacardId);
    when(originalMetacard.getAttribute(eq(Offline.OFFLINE_DATE))).thenReturn(mock(Attribute.class));
    mockMetacardType(originalMetacard);
    mockMetacardType(metacard);
    return originalMetacard;
  }

  private void mockMetacardType(Metacard metacard) {
    when(metacard.getMetacardType()).thenReturn(new OfflineAttributes());
  }

  private void assertEmptyPolicyResponse(PolicyResponse policyResponse) {
    assertThat(policyResponse.itemPolicy(), is(Collections.emptyMap()));
    assertThat(policyResponse.operationPolicy(), is(Collections.emptyMap()));
  }
}
