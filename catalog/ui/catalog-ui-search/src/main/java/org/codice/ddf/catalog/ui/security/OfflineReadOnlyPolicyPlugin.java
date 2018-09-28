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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import ddf.catalog.Constants;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.Result;
import ddf.catalog.data.types.Offline;
import ddf.catalog.operation.Query;
import ddf.catalog.operation.ResourceRequest;
import ddf.catalog.operation.ResourceResponse;
import ddf.catalog.plugin.PolicyPlugin;
import ddf.catalog.plugin.PolicyResponse;
import ddf.catalog.plugin.impl.PolicyResponseImpl;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.subject.Subject;
import org.codice.ddf.catalog.ui.util.OfflineUtils;

public class OfflineReadOnlyPolicyPlugin implements PolicyPlugin {

  private String importUserAttribute;

  private String importUserAttributeValue;

  @SuppressWarnings("WeakerAccess" /* needed for metatype */)
  public void setImportUserAttribute(String importUserAttribute) {
    this.importUserAttribute = importUserAttribute;
  }

  @SuppressWarnings("WeakerAccess" /* needed for metatype */)
  public void setImportUserAttributeValue(String importUserAttributeValue) {
    this.importUserAttributeValue = importUserAttributeValue;
  }

  private final OfflineUtils offlineUtils = new OfflineUtils();

  private final UserApplication userApplication;

  @SuppressWarnings("WeakerAccess" /* needed for blueprint */)
  public OfflineReadOnlyPolicyPlugin(UserApplication userApplication) {
    this.userApplication = userApplication;
  }

  boolean isAllowedToEdit(Subject subject) {
    return PermissionUtils.isPermitted(subject, importUserAttribute, importUserAttributeValue);
  }

  @Override
  public PolicyResponse processPreCreate(Metacard input, Map<String, Serializable> properties) {
    return noOpPolicyResponse();
  }

  @Override
  public PolicyResponse processPreUpdate(Metacard metacard, Map<String, Serializable> properties) {
    Metacard originalMetacard = getUpdateMap(properties).get(metacard.getId());

    return Optional.ofNullable(originalMetacard)
        .filter(metacard1 -> StringUtils.isNotEmpty(importUserAttribute))
        .filter(metacard1 -> StringUtils.isNotEmpty(importUserAttributeValue))
        .filter(this::isOffline)
        .filter(metacard1 -> isEditRestricted(metacard1, metacard))
        .map(metacard1 -> createPolicy())
        .orElseGet(this::noOpPolicyResponse);
  }

  private boolean isEditRestricted(Metacard originalMetacard, Metacard newMetacard) {
    if (userApplication.isOfflineAllowed()) {
      return !offlineUtils.isOfflineCommentOnlyUpdate(originalMetacard, newMetacard);
    }
    return true;
  }

  @Override
  public PolicyResponse processPreDelete(
      List<Metacard> metacards, Map<String, Serializable> properties) {
    return noOpPolicyResponse();
  }

  @Override
  public PolicyResponse processPostDelete(Metacard input, Map<String, Serializable> properties) {
    return noOpPolicyResponse();
  }

  @Override
  public PolicyResponse processPreQuery(Query query, Map<String, Serializable> properties) {
    return noOpPolicyResponse();
  }

  @Override
  public PolicyResponse processPostQuery(Result input, Map<String, Serializable> properties) {
    return noOpPolicyResponse();
  }

  @Override
  public PolicyResponse processPreResource(ResourceRequest resourceRequest) {
    return noOpPolicyResponse();
  }

  @Override
  public PolicyResponse processPostResource(ResourceResponse resourceResponse, Metacard metacard) {
    return noOpPolicyResponse();
  }

  private PolicyResponse noOpPolicyResponse() {
    return new PolicyResponseImpl();
  }

  private PolicyResponse createPolicy() {
    return new PolicyResponseImpl(
        Collections.emptyMap(),
        ImmutableMap.of(importUserAttribute, ImmutableSet.of(importUserAttributeValue)));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Metacard> getUpdateMap(Map<String, Serializable> properties) {
    return (Map<String, Metacard>)
        Optional.of(properties)
            .map(p -> p.get(Constants.ATTRIBUTE_UPDATE_MAP_KEY))
            .filter(Map.class::isInstance)
            .map(Map.class::cast)
            .orElseGet(HashMap::new);
  }

  private boolean isOffline(Metacard metacard) {
    return metacard.getAttribute(Offline.OFFLINE_DATE) != null;
  }
}
