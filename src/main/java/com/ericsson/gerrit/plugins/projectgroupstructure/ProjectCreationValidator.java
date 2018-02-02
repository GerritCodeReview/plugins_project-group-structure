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
import com.google.common.hash.Hashing;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.extensions.annotations.PluginCanonicalWebUrl;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.group.CreateGroup;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.CreateProjectArgs;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.validators.ProjectCreationValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ProjectCreationValidator implements ProjectCreationValidationListener {
  private static final Logger log = LoggerFactory.getLogger(ProjectCreationValidator.class);

  private static final String AN_ERROR_OCCURRED_MSG =
      "An error occurred while creating project, please contact Gerrit support";

  private static final String SEE_DOCUMENTATION_MSG = "\n\nSee documentation for more info: %s";

  private static final String MUST_BE_OWNER_TO_CREATE_PROJECT_MSG =
      "You must be owner of the parent project \"%s\" to create a nested project."
          + SEE_DOCUMENTATION_MSG;

  private static final String PROJECT_CANNOT_CONTAINS_SPACES_MSG =
      "Project name cannot contains spaces." + SEE_DOCUMENTATION_MSG;

  private static final String ROOT_PROJECT_CANNOT_CONTAINS_SLASHES_MSG =
      "Root project name cannot contains slashes." + SEE_DOCUMENTATION_MSG;

  private static final String REGULAR_PROJECT_NOT_ALLOWED_AS_ROOT_MSG =
      "Regular projects are not allowed as root.\n\n"
          + "Please create a root parent project (project with option "
          + "\"Only serve as parent for other projects\") that will hold "
          + "all your access rights and then create your regular project that "
          + "inherits rights from your root project.\n\n"
          + "Example:\n"
          + "\"someOrganization\"->parent project\n"
          + "\"someOrganization/someProject\"->regular project."
          + SEE_DOCUMENTATION_MSG;

  private static final String PROJECT_MUST_START_WITH_PARENT_NAME_MSG =
      "Project name must start with parent project name, e.g. %s." + SEE_DOCUMENTATION_MSG;

  static final String DELEGATE_PROJECT_CREATION_TO = "delegateProjectCreationTo";

  static final String DISABLE_GRANTING_PROJECT_OWNERSHIP = "disableGrantingProjectOwnership";

  private final CreateGroup.Factory createGroupFactory;
  private final String documentationUrl;
  private final AllProjectsNameProvider allProjectsName;
  private final Provider<CurrentUser> self;
  private final PermissionBackend permissionBackend;
  private final PluginConfigFactory cfg;
  private final String pluginName;
  private final ProjectControl.GenericFactory projectControlFactory;

  @Inject
  public ProjectCreationValidator(
      CreateGroup.Factory createGroupFactory,
      @PluginCanonicalWebUrl String url,
      AllProjectsNameProvider allProjectsName,
      Provider<CurrentUser> self,
      PermissionBackend permissionBackend,
      PluginConfigFactory cfg,
      @PluginName String pluginName,
      ProjectControl.GenericFactory projectControlFactory) {
    this.createGroupFactory = createGroupFactory;
    this.documentationUrl = url + "Documentation/index.html";
    this.allProjectsName = allProjectsName;
    this.self = self;
    this.permissionBackend = permissionBackend;
    this.cfg = cfg;
    this.pluginName = pluginName;
    this.projectControlFactory = projectControlFactory;
  }

  @Override
  public void validateNewProject(CreateProjectArgs args) throws ValidationException {
    String name = args.getProjectName();
    log.debug("validating creation of {}", name);
    if (name.contains(" ")) {
      throw new ValidationException(
          String.format(PROJECT_CANNOT_CONTAINS_SPACES_MSG, documentationUrl));
    }

    ProjectControl parentCtrl;
    try {
      parentCtrl = projectControlFactory.controlFor(args.newParent, self.get());
    } catch (NoSuchProjectException | IOException e) {
      log.error(
          "Failed to create project "
              + name
              + "; Cannot retrieve info about parent project "
              + args.newParent.get()
              + ": "
              + e.getMessage(),
          e);
      throw new ValidationException(AN_ERROR_OCCURRED_MSG);
    }

    try {
      permissionBackend.user(self).check(GlobalPermission.ADMINISTRATE_SERVER);

      // Admins can bypass any rules to support creating projects that doesn't
      // comply with the new naming rules. New projects structures have to
      // comply but we need to be able to add new project to an existing non
      // compliant structure.
      log.debug("admin is creating project, bypassing all rules");
      return;
    } catch (AuthException | PermissionBackendException e) {
      // continuing
    }

    if (allProjectsName.get().equals(parentCtrl.getProject().getNameKey())) {
      validateRootProject(name, args.permissionsOnly);
    } else {
      validateProject(name, parentCtrl);
    }

    // If we reached that point, it means we allow project creation. Make the
    // user an owner if not already by inheritance.
    if (!parentCtrl.isOwner() && !configDisableGrantingOwnership(parentCtrl)) {
      args.ownerIds.add(createGroup(name + "-admins"));
    }
  }

  private boolean configDisableGrantingOwnership(ProjectControl parentCtrl)
      throws ValidationException {
    try {
      return cfg.getFromProjectConfigWithInheritance(
              parentCtrl.getProject().getNameKey(), pluginName)
          .getBoolean(DISABLE_GRANTING_PROJECT_OWNERSHIP, false);
    } catch (NoSuchProjectException e) {
      log.error(
          "Failed to check project config for "
              + parentCtrl.getProject().getName()
              + ": "
              + e.getMessage(),
          e);
      throw new ValidationException(AN_ERROR_OCCURRED_MSG);
    }
  }

  private AccountGroup.UUID createGroup(String name) throws ValidationException {
    try {
      GroupInfo groupInfo = null;
      try {
        groupInfo =
            createGroupFactory.create(name).apply(TopLevelResource.INSTANCE, new GroupInput());
      } catch (ResourceConflictException e) {
        // name already exists, make sure it is unique by adding a abbreviated
        // sha1
        String nameWithSha1 =
            name
                + "-"
                + Hashing.sha256().hashString(name, Charsets.UTF_8).toString().substring(0, 7);
        log.info(
            "Failed to create group name {} because of a conflict: {}, trying to create {} instead",
            name,
            e.getMessage(),
            nameWithSha1);
        groupInfo =
            createGroupFactory
                .create(nameWithSha1)
                .apply(TopLevelResource.INSTANCE, new GroupInput());
      }
      return AccountGroup.UUID.parse(groupInfo.id);
    } catch (RestApiException | OrmException | IOException | ConfigInvalidException e) {
      log.error("Failed to create project " + name + ": " + e.getMessage(), e);
      throw new ValidationException(AN_ERROR_OCCURRED_MSG);
    }
  }

  private void validateRootProject(String name, boolean permissionOnly) throws ValidationException {
    log.debug("validating root project name {}", name);
    if (name.contains("/")) {
      log.debug("rejecting creation of {}: name contains slashes", name);
      throw new ValidationException(
          String.format(ROOT_PROJECT_CANNOT_CONTAINS_SLASHES_MSG, documentationUrl));
    }
    if (!permissionOnly) {
      log.debug("rejecting creation of {}: missing permissions only option", name);
      throw new ValidationException(
          String.format(REGULAR_PROJECT_NOT_ALLOWED_AS_ROOT_MSG, documentationUrl));
    }
    log.debug("allowing creation of root project {}", name);
  }

  private void validateProject(String name, ProjectControl parentCtrl) throws ValidationException {
    log.debug("validating name prefix of {}", name);
    Project parent = parentCtrl.getProject();
    String prefix = parent.getName() + "/";
    if (!name.startsWith(prefix)) {
      log.debug("rejecting creation of {}: name is not starting with {}", name, prefix);
      throw new ValidationException(
          String.format(PROJECT_MUST_START_WITH_PARENT_NAME_MSG, prefix + name, documentationUrl));
    }
    if (!parentCtrl.isOwner() && !isInDelegatingGroup(parentCtrl)) {
      log.debug("rejecting creation of {}: user is not owner of {}", name, parent.getName());
      throw new ValidationException(
          String.format(MUST_BE_OWNER_TO_CREATE_PROJECT_MSG, parent.getName(), documentationUrl));
    }
    log.debug("allowing creation of project {}", name);
  }

  private boolean isInDelegatingGroup(ProjectControl parentCtrl) {
    try {
      GroupReference delegateProjectCreationTo =
          cfg.getFromProjectConfigWithInheritance(parentCtrl.getProject().getNameKey(), pluginName)
              .getGroupReference(DELEGATE_PROJECT_CREATION_TO);
      if (delegateProjectCreationTo == null) {
        return false;
      }
      log.debug("delegateProjectCreationTo: {}", delegateProjectCreationTo);
      GroupMembership effectiveGroups = parentCtrl.getUser().getEffectiveGroups();
      return effectiveGroups.contains(delegateProjectCreationTo.getUUID());
    } catch (NoSuchProjectException e) {
      log.error("isInDelegatingGroup with error ({}): {}", e.getClass().getName(), e.getMessage());
      return false;
    }
  }
}
