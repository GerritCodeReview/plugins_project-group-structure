// Copyright (C) 2016 The Android Open Source Project
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

import static com.ericsson.gerrit.plugins.projectgroupstructure.Configuration.SEE_DOCUMENTATION_MSG;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.annotations.PluginCanonicalWebUrl;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.api.groups.Groups;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.CreateProjectArgs;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.validators.ProjectCreationValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ProjectCreationValidator implements ProjectCreationValidationListener {
  private static final Logger log = LoggerFactory.getLogger(ProjectCreationValidator.class);

  private static final String AN_ERROR_OCCURRED_MSG =
      "An error occurred while creating project, please contact Gerrit support";

  private static final String MUST_BE_OWNER_TO_CREATE_PROJECT_MSG =
      "You must be owner of the parent project \"%s\" to create a nested project."
          + SEE_DOCUMENTATION_MSG;

  private static final String PROJECT_CANNOT_CONTAINS_SPACES_MSG =
      "Project name cannot contain spaces." + SEE_DOCUMENTATION_MSG;

  private static final String ROOT_PROJECT_CANNOT_CONTAINS_SLASHES_MSG =
      "Since the \"Rights Inherit From\" field is empty, "
          + "\"%s\" is considered a root project whose parent is \"%s\". "
          + "Root project names cannot contain slashes."
          + SEE_DOCUMENTATION_MSG;

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

  private static final String PROJECT_SHOULD_MATCH_REGEX_MSG =
      "Project name should match the regex: %s." + SEE_DOCUMENTATION_MSG;

  static final String DELEGATE_PROJECT_CREATION_TO = "delegateProjectCreationTo";

  static final String DISABLE_GRANTING_PROJECT_OWNERSHIP = "disableGrantingProjectOwnership";

  private final Groups groups;
  private final String documentationUrl;
  private final AllProjectsNameProvider allProjectsName;
  private final Provider<CurrentUser> self;
  private final PermissionBackend permissionBackend;
  private final PluginConfigFactory cfg;
  private final String pluginName;
  private final Configuration config;

  @Inject
  public ProjectCreationValidator(
      Groups groups,
      @PluginCanonicalWebUrl String url,
      AllProjectsNameProvider allProjectsName,
      Provider<CurrentUser> self,
      PermissionBackend permissionBackend,
      PluginConfigFactory cfg,
      Configuration config,
      @PluginName String pluginName) {
    this.groups = groups;
    this.documentationUrl = url + Configuration.DOCUMENTATION_PATH;
    this.allProjectsName = allProjectsName;
    this.self = self;
    this.permissionBackend = permissionBackend;
    this.cfg = cfg;
    this.pluginName = pluginName;
    this.config = config;
  }

  @Override
  public void validateNewProject(CreateProjectArgs args) throws ValidationException {
    String name = args.getProjectName();
    log.debug("validating creation of {}", name);
    String regex = config.getRegexNameFilter();
    if (!(name.matches(regex))) {
      throw new ValidationException(
          String.format(PROJECT_SHOULD_MATCH_REGEX_MSG, regex, documentationUrl));
    }
    if (name.contains(" ")) {
      throw new ValidationException(
          String.format(PROJECT_CANNOT_CONTAINS_SPACES_MSG, documentationUrl));
    }

    Project.NameKey newParent = args.newParent;

    try {
      permissionBackend.user(self.get()).check(GlobalPermission.ADMINISTRATE_SERVER);

      // Admins can bypass any rules to support creating projects that doesn't
      // comply with the new naming rules. New projects structures have to
      // comply but we need to be able to add new project to an existing non
      // compliant structure.
      log.debug("admin is creating project, bypassing all rules");
      return;
    } catch (AuthException | PermissionBackendException e) {
      // continuing
    }

    if (name.contains(" ")) {
      throw new ValidationException(
          String.format(PROJECT_CANNOT_CONTAINS_SPACES_MSG, documentationUrl));
    }

    if (allProjectsName.get().equals(newParent)) {
      validateRootProject(name, args.permissionsOnly);
    } else {
      validateProject(name, newParent);
    }

    // If we reached that point, it means we allow project creation. Make the
    // user an owner if not already by inheritance.
    if (!isOwner(newParent) && !configDisableGrantingOwnership(newParent)) {
      args.ownerIds.add(createGroup(name + "-admins"));
    }
  }

  private boolean isOwner(Project.NameKey project) {
    try {
      permissionBackend.user(self.get()).project(project).check(ProjectPermission.WRITE_CONFIG);
    } catch (AuthException | PermissionBackendException noWriter) {
      try {
        permissionBackend.user(self.get()).check(GlobalPermission.ADMINISTRATE_SERVER);
      } catch (AuthException | PermissionBackendException noAdmin) {
        return false;
      }
    }
    return true;
  }

  private boolean configDisableGrantingOwnership(Project.NameKey parentCtrl)
      throws ValidationException {
    try {
      return cfg.getFromProjectConfigWithInheritance(parentCtrl, pluginName)
          .getBoolean(DISABLE_GRANTING_PROJECT_OWNERSHIP, false);
    } catch (NoSuchProjectException e) {
      log.error("Failed to check project config for {}: {}", parentCtrl.get(), e.getMessage(), e);
      throw new ValidationException(AN_ERROR_OCCURRED_MSG);
    }
  }

  private AccountGroup.UUID createGroup(String name) throws ValidationException {
    try {
      GroupInfo groupInfo = null;
      try {
        groupInfo = groups.create(name).get();
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
        groupInfo = groups.create(nameWithSha1).get();
      }
      return AccountGroup.UUID.parse(groupInfo.id);
    } catch (RestApiException e) {
      log.error("Failed to create project {}: {}", name, e.getMessage(), e);
      throw new ValidationException(AN_ERROR_OCCURRED_MSG);
    }
  }

  private void validateRootProject(String name, boolean permissionOnly) throws ValidationException {
    log.debug("validating root project name {}", name);
    if (name.contains("/")) {
      log.debug("rejecting creation of {}: name contains slashes", name);
      throw new ValidationException(
          String.format(
              ROOT_PROJECT_CANNOT_CONTAINS_SLASHES_MSG,
              name,
              allProjectsName.get(),
              documentationUrl));
    }
    if (!permissionOnly) {
      log.debug("rejecting creation of {}: missing permissions only option", name);
      throw new ValidationException(
          String.format(REGULAR_PROJECT_NOT_ALLOWED_AS_ROOT_MSG, documentationUrl));
    }
    log.debug("allowing creation of root project {}", name);
  }

  private void validateProject(String name, Project.NameKey parentCtrl) throws ValidationException {
    log.debug("validating name prefix of {}", name);
    String prefix = parentCtrl.get() + "/";
    if (!name.startsWith(prefix)) {
      log.debug("rejecting creation of {}: name is not starting with {}", name, prefix);
      throw new ValidationException(
          String.format(PROJECT_MUST_START_WITH_PARENT_NAME_MSG, prefix + name, documentationUrl));
    }
    if (!isOwner(parentCtrl) && !isInDelegatingGroup(parentCtrl)) {
      log.debug("rejecting creation of {}: user is not owner of {}", name, parentCtrl.get());
      throw new ValidationException(
          String.format(MUST_BE_OWNER_TO_CREATE_PROJECT_MSG, parentCtrl.get(), documentationUrl));
    }
    log.debug("allowing creation of project {}", name);
  }

  private boolean isInDelegatingGroup(Project.NameKey parentCtrl) {
    try {
      Optional<GroupReference> groupReference =
          cfg.getFromProjectConfigWithInheritance(parentCtrl, pluginName)
              .getGroupReference(DELEGATE_PROJECT_CREATION_TO);
      if (groupReference.isPresent()) {
        GroupReference delegateProjectCreationTo = groupReference.get();
        log.debug("delegateProjectCreationTo: {}", delegateProjectCreationTo);
        GroupMembership effectiveGroups = self.get().getEffectiveGroups();
        return effectiveGroups.contains(delegateProjectCreationTo.getUUID());
      }
    } catch (NoSuchProjectException e) {
      log.error("isInDelegatingGroup with error ({}): {}", e.getClass().getName(), e.getMessage());
    }
    return false;
  }
}
