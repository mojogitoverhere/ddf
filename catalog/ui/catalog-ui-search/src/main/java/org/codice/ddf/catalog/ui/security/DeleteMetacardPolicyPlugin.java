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
import ddf.catalog.data.Metacard;
import ddf.catalog.data.Result;
import ddf.catalog.operation.Query;
import ddf.catalog.operation.ResourceRequest;
import ddf.catalog.operation.ResourceResponse;
import ddf.catalog.plugin.PolicyPlugin;
import ddf.catalog.plugin.PolicyResponse;
import ddf.catalog.plugin.StopProcessingException;
import ddf.catalog.plugin.impl.PolicyResponseImpl;
import ddf.security.permission.KeyValuePermission;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteMetacardPolicyPlugin implements PolicyPlugin {

  private static final Logger LOGGER = LoggerFactory.getLogger(DeleteMetacardPolicyPlugin.class);

  private String deletePermission;

  private String searchDeletedPermission;

  public boolean isAllowedToSearchDeleted(Subject subject) {
    return isPermitted(subject, searchDeletedPermission);
  }

  public boolean isAllowedToDelete(Subject subject) {
    return isPermitted(subject, deletePermission);
  }

  @Override
  public PolicyResponse processPreCreate(Metacard input, Map<String, Serializable> properties)
      throws StopProcessingException {
    return noOpPolicyResponse();
  }

  @Override
  public PolicyResponse processPreUpdate(Metacard newMetacard, Map<String, Serializable> properties)
      throws StopProcessingException {
    return noOpPolicyResponse();
  }

  @Override
  public PolicyResponse processPreDelete(
      List<Metacard> metacards, Map<String, Serializable> properties)
      throws StopProcessingException {
    return new PolicyResponseImpl(getPolicy(metacards), Collections.emptyMap());
  }

  @Override
  public PolicyResponse processPostDelete(Metacard input, Map<String, Serializable> properties)
      throws StopProcessingException {
    return noOpPolicyResponse();
  }

  @Override
  public PolicyResponse processPreQuery(Query query, Map<String, Serializable> properties)
      throws StopProcessingException {
    return noOpPolicyResponse();
  }

  @Override
  public PolicyResponse processPostQuery(Result input, Map<String, Serializable> properties)
      throws StopProcessingException {
    return noOpPolicyResponse();
  }

  @Override
  public PolicyResponse processPreResource(ResourceRequest resourceRequest)
      throws StopProcessingException {
    return noOpPolicyResponse();
  }

  @Override
  public PolicyResponse processPostResource(ResourceResponse resourceResponse, Metacard metacard)
      throws StopProcessingException {
    return noOpPolicyResponse();
  }

  private boolean isPermitted(Subject subject, String permission) {
    String[] keyValue = parsePermission(permission);
    if (keyValue == null || subject == null) {
      return true;
    }

    return subject.isPermitted(new KeyValuePermission(keyValue[0], ImmutableSet.of(keyValue[1])));
  }

  private Map<String, Set<String>> getPolicy(List<Metacard> metacards) {
    boolean includesResourceMetacard = metacards.stream().anyMatch(this::isResource);
    String[] keyValue = parsePermission(deletePermission);
    if (includesResourceMetacard && keyValue != null) {
      return ImmutableMap.of(keyValue[0], ImmutableSet.of(keyValue[1]));
    } else {
      return Collections.emptyMap();
    }
  }

  private boolean isResource(Metacard metacard) {
    return metacard.getTags().contains("resource");
  }

  private PolicyResponse noOpPolicyResponse() {
    return new PolicyResponseImpl();
  }

  private String[] parsePermission(String permission) {
    if (permission == null) {
      return null;
    }

    String[] keyValue = permission.split("=");
    if (keyValue.length != 2 || StringUtils.isAnyBlank(keyValue)) {
      LOGGER.debug(
          "Failed to parse the permission [{}]. It must be in the \"permission key=permission value\" format",
          permission);
      return null;
    }

    return keyValue;
  }

  public void setDeletePermission(String deletePermission) {
    this.deletePermission = deletePermission;
  }

  public void setSearchDeletedPermission(String searchDeletedPermission) {
    this.searchDeletedPermission = searchDeletedPermission;
  }
}
