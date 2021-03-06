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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowCapability;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.groups.GroupApi;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

@TestPlugin(
    name = "project-group-structure",
    sysModule = "com.ericsson.gerrit.plugins.projectgroupstructure.Module")
public class ProjectCreationValidatorIT extends LightweightPluginDaemonTest {

  @Inject private ProjectOperations projectOperations;

  private static final String PLUGIN_NAME = "project-group-structure";
  private static final String REGEX_INCLUDING_SLASH = "[a-z_/]+";
  private static final String REGEX_NOT_INCLUDING_SLASH = "[a-z_]+";
  private static final String REGEX_INCLUDING_SPACE = "[a-z_/ ]+";
  private static final String PROJECT_WITH_SPACE = "project with space";

  @Override
  @Before
  public void setUpTestPlugin() throws Exception {
    super.setUpTestPlugin();
    // These access rights are mandatory configuration for this plugin as
    // documented in config.md
    projectOperations
        .project(allProjects)
        .forUpdate()
        .add(allowCapability(GlobalCapability.CREATE_GROUP).group(REGISTERED_USERS))
        .update();
    projectOperations
        .project(allProjects)
        .forUpdate()
        .add(allowCapability(GlobalCapability.CREATE_PROJECT).group(REGISTERED_USERS))
        .update();
  }

  @Test
  public void shouldProjectWithASpaceInTheirName() throws Exception {
    ProjectInput in = new ProjectInput();
    in.permissionsOnly = true;
    RestResponse r = userRestSession.put("/projects/" + Url.encode(PROJECT_WITH_SPACE), in);
    r.assertConflict();
    assertThat(r.getEntityContent()).contains("Project name cannot contain spaces");
  }

  @Test
  @UseLocalDisk
  @GerritConfig(name = "project-group-structure.nameRegex", value = REGEX_INCLUDING_SPACE)
  public void shouldRejectProjectWithSpaceInItsNameEvenWithRegex() throws Exception {
    ProjectInput in = new ProjectInput();
    in.permissionsOnly = true;
    RestResponse r = userRestSession.put("/projects/" + Url.encode(PROJECT_WITH_SPACE), in);
    r.assertConflict();
    assertThat(r.getEntityContent()).contains("Project name cannot contain spaces");
  }

  @Test
  @UseLocalDisk
  @GerritConfig(name = "plugin.project-group-structure.nameRegex", value = REGEX_INCLUDING_SLASH)
  public void shouldMatchProjectNameIfRegexContainsSlash() throws Exception {
    ProjectInput in = new ProjectInput();
    in.permissionsOnly = true;
    RestResponse r = userRestSession.put("/projects/" + Url.encode("project1"), in);
    userRestSession.put("/projects/" + Url.encode("project"), in).assertCreated();
    r.assertConflict();
    assertThat(r.getEntityContent())
        .contains(String.format("Project name should match the regex: %s", REGEX_INCLUDING_SLASH));
  }

  @Test
  @UseLocalDisk
  @GerritConfig(
      name = "plugin.project-group-structure.nameRegex",
      value = REGEX_NOT_INCLUDING_SLASH)
  public void shouldNotMatchProjectNameIfRegexMissesSlash() throws Exception {
    ProjectInput in = new ProjectInput();
    in.permissionsOnly = true;
    userRestSession.put("/projects/" + Url.encode("PROJECT1"), in).assertCreated();
  }

  @Test
  public void shouldAllowAnyUsersToCreateUnderAllProjects() throws Exception {
    ProjectInput in = new ProjectInput();
    in.permissionsOnly = true;
    adminRestSession.put("/projects/" + name("someProject"), in).assertCreated();

    userRestSession.put("/projects/" + name("someOtherProject"), in).assertCreated();
  }

  @Test
  public void shouldBlockRootProjectWithSlashesInTheirName() throws Exception {
    // Root project is OK without slash
    String parent = name("parentProject");
    ProjectInput in = new ProjectInput();
    in.permissionsOnly = true;
    userRestSession.put("/projects/" + parent, in).assertCreated();

    // Creation is rejected when root project name contains slashes
    RestResponse r = userRestSession.put("/projects/" + Url.encode("a/parentProject"), in);
    r.assertConflict();
    assertThat(r.getEntityContent()).contains("Root project names cannot contain slashes");
  }

  @Test
  public void shouldBlockProjectWithParentNotPartOfProjectName() throws Exception {
    // Root project is OK without parent part of the name
    ProjectInput in = new ProjectInput();
    in.permissionsOnly = true;
    String parent = name("parentProject");
    userRestSession.put("/projects/" + parent, in).assertCreated();

    // Creation is rejected when project name does not start with parent
    in = new ProjectInput();
    in.parent = parent;
    RestResponse r = userRestSession.put("/projects/childProject", in);
    r.assertConflict();
    assertThat(r.getEntityContent()).contains("Project name must start with parent project name");

    // Creation is OK when project name starts with parent
    userRestSession.put("/projects/" + Url.encode(parent + "/childProject"), in).assertCreated();

    // Creation is rejected when project name does not start with nested parent
    String nestedParent = parent + "/childProject";
    in.parent = nestedParent;
    r = userRestSession.put("/projects/grandchild", in);
    r.assertConflict();
    assertThat(r.getEntityContent()).contains("Project name must start with parent project name");

    // Creation is OK when project name starts with nested parent
    userRestSession
        .put("/projects/" + Url.encode(nestedParent + "/grandchild"), in)
        .assertCreated();
  }

  @Test
  public void shouldBlockCreationToNonOwnersOfParentProject() throws Exception {
    String ownerGroup = name("groupA");
    GroupApi g = gApi.groups().create(ownerGroup);

    String parent = name("parentProject");
    ProjectInput in = new ProjectInput();
    in.permissionsOnly = true;
    in.owners = Lists.newArrayList(ownerGroup);
    adminRestSession.put("/projects/" + parent, in).assertCreated();

    // Creation is rejected when user is not owner of parent
    in = new ProjectInput();
    in.parent = parent;
    RestResponse r = userRestSession.put("/projects/" + Url.encode(parent + "/childProject"), in);
    r.assertConflict();
    assertThat(r.getEntityContent()).contains("You must be owner of the parent project");

    // Creation is OK when user is owner of parent
    g.addMembers(user.username());
    userRestSession.put("/projects/" + Url.encode(parent + "/childProject"), in).assertCreated();
  }

  @Test
  public void shouldAllowAdminsToByPassAnyRule() throws Exception {
    // Root project with a slash
    String parent = name("orgA/parentProject");
    ProjectInput in = new ProjectInput();
    in.permissionsOnly = true;
    adminRestSession.put("/projects/" + Url.encode(parent), in).assertCreated();

    // Child project without name of parent as prefix
    in = new ProjectInput();
    in.parent = parent;
    adminRestSession.put("/projects/" + Url.encode("orgA/childProject"), in).assertCreated();
  }

  @Test
  public void shouldMakeUserOwnerOnRootProjectCreation() throws Exception {
    // normal case, when <project-name>-admins group does not exist
    String rootProject = name("rootProject");
    ProjectInput in = new ProjectInput();
    in.permissionsOnly = true;
    userRestSession.put("/projects/" + rootProject, in).assertCreated();
    Optional<ProjectState> projectState = projectCache.get(Project.nameKey(rootProject));
    assertThat(projectState.get().getOwners().size()).isEqualTo(1);
    assertThat(projectState.get().getOwners())
        .contains(
            groupCache.get(AccountGroup.nameKey(rootProject + "-admins")).get().getGroupUUID());

    // case when <project-name>-admins group already exists
    rootProject = name("rootProject2");
    String existingGroupName = rootProject + "-admins";
    gApi.groups().create(existingGroupName);
    userRestSession.put("/projects/" + rootProject, in).assertCreated();
    projectState = projectCache.get(Project.nameKey(rootProject));
    assertThat(projectState.get().getOwners().size()).isEqualTo(1);
    String expectedOwnerGroup =
        existingGroupName
            + "-"
            + Hashing.sha256()
                .hashString(existingGroupName, Charsets.UTF_8)
                .toString()
                .substring(0, 7);
    assertThat(projectState.get().getOwners())
        .contains(groupCache.get(AccountGroup.nameKey(expectedOwnerGroup)).get().getGroupUUID());
  }

  @Test
  public void shouldBlockRootCodeProject() throws Exception {
    RestResponse r = userRestSession.put("/projects/" + Url.encode("project1"));
    r.assertConflict();
    assertThat(r.getEntityContent()).contains("Regular projects are not allowed as root");
  }

  @Test
  public void shouldAllowCreationIfUserIsInDelegatingGroup() throws Exception {
    String ownerGroup = name("groupA");
    gApi.groups().create(ownerGroup);

    String parent = name("parentProject");
    ProjectInput in = new ProjectInput();
    in.permissionsOnly = true;
    in.owners = Lists.newArrayList(ownerGroup);
    adminRestSession.put("/projects/" + parent, in).assertCreated();

    in = new ProjectInput();
    in.parent = parent;
    RestResponse r = userRestSession.put("/projects/" + Url.encode(parent + "/childProject"), in);
    r.assertConflict();
    assertThat(r.getEntityContent()).contains("You must be owner of the parent project");

    // the user is in the delegating group
    String delegatingGroup = name("groupB");
    GroupApi dGroup = gApi.groups().create(delegatingGroup);
    dGroup.addMembers(user.username());
    // the group is in the project.config
    Project.NameKey parentNameKey = Project.nameKey(parent);
    try (ProjectConfigUpdate cfgUpdate = updateProject(parentNameKey)) {
      ProjectConfig cfg = cfgUpdate.getConfig();
      String gId = gApi.groups().id(delegatingGroup).get().id;
      cfg.updatePluginConfig(
          PLUGIN_NAME,
          pluginCfg ->
              pluginCfg.setGroupReference(
                  ProjectCreationValidator.DELEGATE_PROJECT_CREATION_TO,
                  GroupReference.create(AccountGroup.UUID.parse(gId), delegatingGroup)));
      cfgUpdate.save();
    }
    userRestSession.put("/projects/" + Url.encode(parent + "/childProject"), in).assertCreated();
  }

  @Test
  public void shouldMakeUserOwnerIfNotAlreadyOwnerByInheritance() throws Exception {
    String parent = name("parentProject");
    ProjectInput in = new ProjectInput();
    in.permissionsOnly = true;
    adminRestSession.put("/projects/" + parent, in).assertCreated();

    String delegatingGroup = name("someGroup");
    GroupApi dGroup = gApi.groups().create(delegatingGroup);
    dGroup.addMembers(user.username());
    Project.NameKey parentNameKey = Project.nameKey(parent);
    try (ProjectConfigUpdate cfgUpdate = updateProject(parentNameKey)) {
      ProjectConfig cfg = cfgUpdate.getConfig();
      String gId = gApi.groups().id(delegatingGroup).get().id;
      cfg.updatePluginConfig(
          PLUGIN_NAME,
          pluginCfg ->
              pluginCfg.setGroupReference(
                  ProjectCreationValidator.DELEGATE_PROJECT_CREATION_TO,
                  GroupReference.create(AccountGroup.UUID.parse(gId), delegatingGroup)));
      cfgUpdate.save();
    }

    // normal case, when <project-name>-admins group does not exist
    in = new ProjectInput();
    in.parent = parent;
    String childProject = parent + "/childProject";
    userRestSession.put("/projects/" + Url.encode(childProject), in).assertCreated();
    Optional<ProjectState> projectState = projectCache.get(Project.nameKey(childProject));
    assertThat(projectState.get().getOwners().size()).isEqualTo(1);
    assertThat(projectState.get().getOwners())
        .contains(
            groupCache.get(AccountGroup.nameKey(childProject + "-admins")).get().getGroupUUID());

    // case when <project-name>-admins group already exists
    String childProject2 = parent + "/childProject2";
    String existingGroupName = childProject2 + "-admins";
    gApi.groups().create(existingGroupName);
    userRestSession.put("/projects/" + Url.encode(childProject2), in).assertCreated();
    projectState = projectCache.get(Project.nameKey(childProject2));
    assertThat(projectState.get().getOwners().size()).isEqualTo(1);
    String expectedOwnerGroup =
        existingGroupName
            + "-"
            + Hashing.sha256()
                .hashString(existingGroupName, Charsets.UTF_8)
                .toString()
                .substring(0, 7);
    assertThat(projectState.get().getOwners())
        .contains(groupCache.get(AccountGroup.nameKey(expectedOwnerGroup)).get().getGroupUUID());
  }

  @Test
  public void shouldNotMakeUserOwnerIfNotAlreadyOwnerByInheritanceAndGrantingIsDisabled()
      throws Exception {
    String parent = name("parentProject");
    ProjectInput in = new ProjectInput();
    in.permissionsOnly = true;
    adminRestSession.put("/projects/" + parent, in).assertCreated();

    String delegatingGroup = name("someGroup");
    GroupApi dGroup = gApi.groups().create(delegatingGroup);
    dGroup.addMembers(user.username());
    Project.NameKey parentNameKey = Project.nameKey(parent);
    try (ProjectConfigUpdate cfgUpdate = updateProject(parentNameKey)) {
      ProjectConfig cfg = cfgUpdate.getConfig();
      String gId = gApi.groups().id(delegatingGroup).get().id;
      cfg.updatePluginConfig(
          PLUGIN_NAME,
          pluginCfg ->
              pluginCfg.setGroupReference(
                  ProjectCreationValidator.DELEGATE_PROJECT_CREATION_TO,
                  GroupReference.create(AccountGroup.UUID.parse(gId), delegatingGroup)));
      cfg.updatePluginConfig(
          PLUGIN_NAME,
          pluginCfg ->
              pluginCfg.setBoolean(
                  ProjectCreationValidator.DISABLE_GRANTING_PROJECT_OWNERSHIP, true));
      cfgUpdate.save();
    }

    in = new ProjectInput();
    in.parent = parent;
    String childProject = parent + "/childProject";
    userRestSession.put("/projects/" + Url.encode(childProject), in).assertCreated();
    Optional<ProjectState> projectState = projectCache.get(Project.nameKey(childProject));
    assertThat(projectState.get().getOwners().size()).isEqualTo(0);
  }

  @Test
  public void shouldBlockCreationIfGroupRefIsNotUsed() throws Exception {
    String ownerGroup = name("groupA");
    gApi.groups().create(ownerGroup);

    String parent = name("parentProject");
    ProjectInput in = new ProjectInput();
    in.permissionsOnly = true;
    in.owners = Lists.newArrayList(ownerGroup);
    adminRestSession.put("/projects/" + parent, in).assertCreated();

    in = new ProjectInput();
    in.parent = parent;
    RestResponse r = userRestSession.put("/projects/" + Url.encode(parent + "/childProject"), in);
    r.assertConflict();
    assertThat(r.getEntityContent()).contains("You must be owner of the parent project");

    // the user is in the delegating group
    String delegatingGroup = name("groupB");
    GroupApi dGroup = gApi.groups().create(delegatingGroup);
    dGroup.addMembers(user.username());
    // the group is in the project.config
    Project.NameKey parentNameKey = Project.nameKey(parent);
    try (ProjectConfigUpdate cfgUpdate = updateProject(parentNameKey)) {
      ProjectConfig cfg = cfgUpdate.getConfig();
      cfg.updatePluginConfig(
          PLUGIN_NAME,
          pluginCfg ->
              pluginCfg.setString(
                  ProjectCreationValidator.DELEGATE_PROJECT_CREATION_TO, delegatingGroup));
      cfgUpdate.save();
    }
    userRestSession.put("/projects/" + Url.encode(parent + "/childProject"), in).assertConflict();
  }

  @Test
  public void shouldAllowCreationIfUserIsInDelegatingGroupNested() throws Exception {
    String ownerGroup = name("groupA");
    gApi.groups().create(ownerGroup);

    String parent = name("parentProject");
    ProjectInput in = new ProjectInput();
    in.permissionsOnly = true;
    in.owners = Lists.newArrayList(ownerGroup);
    adminRestSession.put("/projects/" + parent, in).assertCreated();

    in = new ProjectInput();
    in.parent = parent;
    RestResponse r = userRestSession.put("/projects/" + Url.encode(parent + "/childProject"), in);
    r.assertConflict();
    assertThat(r.getEntityContent()).contains("You must be owner of the parent project");

    // the user is in the nested delegating group
    String delegatingGroup = name("groupB");
    GroupApi dGroup = gApi.groups().create(delegatingGroup);

    String nestedGroup = name("groupC");
    GroupApi nGroup = gApi.groups().create(nestedGroup);
    nGroup.addMembers(user.username());

    dGroup.addGroups(nestedGroup);
    // the group is in the project.config
    Project.NameKey parentNameKey = Project.nameKey(parent);
    try (ProjectConfigUpdate cfgUpdate = updateProject(parentNameKey)) {
      ProjectConfig cfg = cfgUpdate.getConfig();
      String gId = gApi.groups().id(delegatingGroup).get().id;
      cfg.updatePluginConfig(
          PLUGIN_NAME,
          pluginCfg ->
              pluginCfg.setGroupReference(
                  ProjectCreationValidator.DELEGATE_PROJECT_CREATION_TO,
                  GroupReference.create(AccountGroup.UUID.parse(gId), delegatingGroup)));
      cfgUpdate.save();
    }
    userRestSession.put("/projects/" + Url.encode(parent + "/childProject"), in).assertCreated();
  }

  @Test
  public void shouldBlockCreationIfUserIsNotInDelegatingGroup() throws Exception {
    String ownerGroup = name("groupA");
    gApi.groups().create(ownerGroup);

    String parent = name("parentProject");
    ProjectInput in = new ProjectInput();
    in.permissionsOnly = true;
    in.owners = Lists.newArrayList(ownerGroup);
    adminRestSession.put("/projects/" + parent, in).assertCreated();

    in = new ProjectInput();
    in.parent = parent;
    RestResponse r = userRestSession.put("/projects/" + Url.encode(parent + "/childProject"), in);
    r.assertConflict();
    assertThat(r.getEntityContent()).contains("You must be owner of the parent project");

    // the user is in the delegating group
    String delegatingGroup = name("groupB");
    gApi.groups().create(delegatingGroup);
    // The user is not added to the delegated group
    // the group is in the project.config
    Project.NameKey parentNameKey = Project.nameKey(parent);
    try (ProjectConfigUpdate cfgUpdate = updateProject(parentNameKey)) {
      ProjectConfig cfg = cfgUpdate.getConfig();
      String gId = gApi.groups().id(delegatingGroup).get().id;
      cfg.updatePluginConfig(
          PLUGIN_NAME,
          pluginCfg ->
              pluginCfg.setGroupReference(
                  ProjectCreationValidator.DELEGATE_PROJECT_CREATION_TO,
                  GroupReference.create(AccountGroup.UUID.parse(gId), delegatingGroup)));
      cfgUpdate.save();
    }
    userRestSession.put("/projects/" + Url.encode(parent + "/childProject"), in).assertConflict();
  }

  @Test
  public void shouldBlockCreationIfDelegatingGroupDoesNotExist() throws Exception {
    String ownerGroup = name("groupA");
    gApi.groups().create(ownerGroup);

    String parent = name("parentProject");
    ProjectInput in = new ProjectInput();
    in.permissionsOnly = true;
    in.owners = Lists.newArrayList(ownerGroup);
    adminRestSession.put("/projects/" + parent, in).assertCreated();

    in = new ProjectInput();
    in.parent = parent;
    RestResponse r = userRestSession.put("/projects/" + Url.encode(parent + "/childProject"), in);
    r.assertConflict();
    assertThat(r.getEntityContent()).contains("You must be owner of the parent project");

    // The delegating group is not created
    String delegatingGroup = name("groupB");
    // the group is in the project.config
    Project.NameKey parentNameKey = Project.nameKey(parent);
    try (ProjectConfigUpdate cfgUpdate = updateProject(parentNameKey)) {
      ProjectConfig cfg = cfgUpdate.getConfig();
      String gId = "fake-gId";
      cfg.updatePluginConfig(
          PLUGIN_NAME,
          pluginCfg ->
              pluginCfg.setGroupReference(
                  ProjectCreationValidator.DELEGATE_PROJECT_CREATION_TO,
                  GroupReference.create(AccountGroup.UUID.parse(gId), delegatingGroup)));
      cfgUpdate.save();
    }
    userRestSession.put("/projects/" + Url.encode(parent + "/childProject"), in).assertConflict();
  }

  @Test
  public void shouldNotBlockCreationIfDelegatingGroupIsRenamed() throws Exception {
    String ownerGroup = name("groupA");
    gApi.groups().create(ownerGroup);

    String parent = name("parentProject");
    ProjectInput in = new ProjectInput();
    in.permissionsOnly = true;
    in.owners = Lists.newArrayList(ownerGroup);
    adminRestSession.put("/projects/" + parent, in).assertCreated();

    in = new ProjectInput();
    in.parent = parent;
    RestResponse r = userRestSession.put("/projects/" + Url.encode(parent + "/childProject"), in);
    r.assertConflict();
    assertThat(r.getEntityContent()).contains("You must be owner of the parent project");

    // the user is in the delegating group
    String delegatingGroup = name("groupB");
    GroupApi dGroup = gApi.groups().create(delegatingGroup);
    dGroup.addMembers(user.username());
    // the group is in the project.config
    Project.NameKey parentNameKey = Project.nameKey(parent);
    projectCache.evict(parentNameKey);

    try (ProjectConfigUpdate cfgUpdate = updateProject(parentNameKey)) {
      ProjectConfig cfg = cfgUpdate.getConfig();

      String gId = gApi.groups().id(delegatingGroup).get().id;
      cfg.updatePluginConfig(
          PLUGIN_NAME,
          pluginCfg ->
              pluginCfg.setGroupReference(
                  ProjectCreationValidator.DELEGATE_PROJECT_CREATION_TO,
                  GroupReference.create(AccountGroup.UUID.parse(gId), delegatingGroup)));
      cfgUpdate.save();

      String newDelegatingGroup = name("groupC");
      cfg.renameGroup(cfg.getGroup(delegatingGroup).getUUID(), newDelegatingGroup);
    }

    userRestSession.put("/projects/" + Url.encode(parent + "/childProject"), in).assertCreated();
  }
}
