// Copyright (C) 2016 Ericsson
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.ericsson.gerrit.plugins.projectgroupstructure;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.hash.Hashing;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.extensions.annotations.PluginCanonicalWebUrl;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.group.CreateGroup;
import com.google.gerrit.server.project.CreateProjectArgs;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.validators.ProjectCreationValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Singleton
public class ProjectCreationValidator
    implements ProjectCreationValidationListener {
  private static final Logger log =
      LoggerFactory.getLogger(ProjectCreationValidator.class);

  private static final String AN_ERROR_OCCURRED_MSG =
      "An error occurred while creating project, please contact Gerrit support";

  private static final String MUST_BE_OWNER_TO_CREATE_PROJECT_MSG =
      "You must be owner of the parent project \"%s\" to create a nested project."
          + "\n\nSee documentation for more info: %s";

  private static final String ROOT_PROJECT_CANNOT_CONTAINS_SLASHES_MSG =
      "Root project name cannot contains slashes."
          + "\n\nSee documentation for more info: %s";

  private static final String REGULAR_PROJECT_NOT_ALLOWED_AS_ROOT_MSG =
      "Regular projects are not allowed as root.\n\n"
          + "Please create a root parent project (project with option "
          + "\"Only serve as parent for other projects\") that will hold "
          + "all your access rights and then create your regular project that "
          + "inherits rights from your root project.\n\n" + "Example:\n"
          + "\"someOrganization\"->parent project\n"
          + "\"someOrganization/someProject\"->regular project."
          + "\n\nSee documentation for more info: %s";

  private static final String PROJECT_MUST_START_WITH_PARENT_NAME_MSG =
      "Project name must start with parent project name, e.g. %s."
          + "\n\nSee documentation for more info: %s";

  /* package */ static final String DELEGATE_PROJECT_CREATION_TO =
      "delegateProjectCreationTo";

  private final CreateGroup.Factory createGroupFactory;
  private final String documentationUrl;
  private final AllProjectsNameProvider allProjectsName;
  private final PluginConfigFactory cfg;
  private final String pluginName;
  private final GerritApi gerritApi;

  @Inject
  public ProjectCreationValidator(CreateGroup.Factory createGroupFactory,
      @PluginCanonicalWebUrl String url,
      AllProjectsNameProvider allProjectsName,
      PluginConfigFactory cfg,
      @PluginName String pluginName,
      GerritApi gerritApi) {
    this.createGroupFactory = createGroupFactory;
    this.documentationUrl = url + "Documentation/index.html";
    this.allProjectsName = allProjectsName;
    this.cfg = cfg;
    this.pluginName = pluginName;
    this.gerritApi = gerritApi;
  }

  @Override
  public void validateNewProject(CreateProjectArgs args)
      throws ValidationException {
    String name = args.getProjectName();
    log.debug("validating creation of {}", name);
    ProjectControl parentCtrl = args.newParent;
    if (parentCtrl.getUser().getCapabilities().canAdministrateServer()) {
      // Admins can bypass any rules to support creating projects that doesn't
      // comply with the new naming rules. New projects structures have to
      // comply but we need to be able to add new project to an existing non
      // compliant structure.
      log.debug("admin is creating project, bypassing all rules");
      return;
    }

    Project parent = parentCtrl.getProject();

    if (allProjectsName.get().equals(parent.getNameKey())) {
      if (name.contains("/")) {
        log.debug("rejecting creation of {}: name contains slashes", name);
        throw new ValidationException(String.format(
            ROOT_PROJECT_CANNOT_CONTAINS_SLASHES_MSG, documentationUrl));
      }
      if (!args.permissionsOnly) {
        log.debug("rejecting creation of {}: missing permissions only option",
            name);
        throw new ValidationException(String
            .format(REGULAR_PROJECT_NOT_ALLOWED_AS_ROOT_MSG, documentationUrl));
      }
      args.ownerIds.add(createGroup(name + "-admins"));
      log.debug("allowing creation of root project {}", name);
      return;
    }

    validateProjectNamePrefix(name, parent);

    if (!parentCtrl.isOwner() && !isInDelegatingGroup(parentCtrl)) {
      log.debug("rejecting creation of {}: user is not owner of {}", name,
          parent.getName());
      throw new ValidationException(
          String.format(MUST_BE_OWNER_TO_CREATE_PROJECT_MSG, parent.getName(),
              documentationUrl));
    }
    log.debug("allowing creation of project {}", name);
  }

  private boolean isInDelegatingGroup(ProjectControl parentCtrl) {
    try {
      String delegateProjectCreationTo = cfg
          .getFromProjectConfigWithInheritance(
              parentCtrl.getProject().getNameKey(), pluginName)
          .getString(DELEGATE_PROJECT_CREATION_TO, null);
      log.debug("delegateProjectCreationTo: {}", delegateProjectCreationTo);
      if (Strings.isNullOrEmpty(delegateProjectCreationTo)) {
        return false;
      }
      if (delegateProjectCreationTo.startsWith("Group[")
          && delegateProjectCreationTo.contains("/")) {
        return isMember(parentCtrl.getUser().getUserName(),
            GroupReference.fromString(delegateProjectCreationTo).getUUID());
      }
      log.info(
          "malformed group reference (delegateProjectCreationTo): {} in project {}",
          delegateProjectCreationTo,
          parentCtrl.getProject().getNameKey().get());
      return false;
    } catch (NoSuchProjectException | RestApiException e) {
      log.error("isOwner with error ({}): {}", e.getClass().getName(),
          e.getMessage());
      return false;
    }
  }

  private boolean isMember(String userName, AccountGroup.UUID groupUuid)
      throws RestApiException {
    List<AccountInfo> accounts =
        gerritApi.groups().id(groupUuid.get()).members(true);
    for (AccountInfo account : accounts) {
      if (account.username.equals(userName)) {
        return true;
      }
    }
    return false;
  }

  private AccountGroup.UUID createGroup(String name)
      throws ValidationException {
    try {
      GroupInfo groupInfo = null;
      try {
        groupInfo = createGroupFactory.create(name)
            .apply(TopLevelResource.INSTANCE, new GroupInput());
      } catch (ResourceConflictException e) {
        // name already exists, make sure it is unique by adding a abbreviated
        // sha1
        String nameWithSha1 = name + "-" + Hashing.sha1()
            .hashString(name, Charsets.UTF_8).toString().substring(0, 7);
        log.info(
            "Failed to create group name {} because of a conflict: {}, trying to create {} instead",
            name, e.getMessage(), nameWithSha1);
        groupInfo = createGroupFactory.create(nameWithSha1)
            .apply(TopLevelResource.INSTANCE, new GroupInput());
      }
      return AccountGroup.UUID.parse(groupInfo.id);
    } catch (RestApiException | OrmException e) {
      log.error("Failed to create project " + name + ": " + e.getMessage(), e);
      throw new ValidationException(AN_ERROR_OCCURRED_MSG);
    }
  }

  private void validateProjectNamePrefix(String name, Project parent)
      throws ValidationException {
    log.debug("validating name prefix of {}", name);
    String prefix = parent.getName() + "/";
    if (!name.startsWith(prefix)) {
      log.debug("rejecting creation of {}: name is not starting with {}", name,
          prefix);
      throw new ValidationException(
          String.format(PROJECT_MUST_START_WITH_PARENT_NAME_MSG, prefix + name,
              documentationUrl));
    }
  }
}
