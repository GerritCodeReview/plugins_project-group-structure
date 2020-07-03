// Copyright (C) 2018 The Android Open Source Project
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

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import java.nio.file.Files;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

@TestPlugin(
    name = "project-group-structure",
    sysModule = "com.ericsson.gerrit.plugins.projectgroupstructure.Module")
public class DefaultAccessRightsIT extends LightweightPluginDaemonTest {

  @Inject private ProjectOperations projectOperations;

  @Override
  @Before
  public void setUpTestPlugin() throws Exception {
    String defaultAccessRights =
        "[access \"refs/*\"]\n"
            + "  read = group ${owner}\n"
            + "  read = group unexiting group\n"
            + "  push = group Administrators\n"
            + "  exclusiveGroupPermissions = read andInvalidOne\n"
            + "[access \"refs/heads/*\"]\n"
            + "  create = group ${owner}\n"
            + "  push = group ${owner}\n"
            + "  label-Code-Review = -2..+2 group ${owner}\n"
            + "  submit = group ${owner}\n"
            + "  invalidPermission = group ${owner}\n"
            + "  editTopicName = group\n"
            + "[access \"/invalidrefs\"]\n"
            + "[access \"refs/invalidregex/${username(((((\"]\n";
    Files.write(
        tempDataDir.newFile(ProjectConfig.PROJECT_CONFIG).toPath(), defaultAccessRights.getBytes());
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
  public void shouldConfigureForRootProject() throws Exception {
    ProjectInput in = new ProjectInput();
    in.permissionsOnly = true;
    String projectName = name("someProject");
    userRestSession.put("/projects/" + projectName, in).assertCreated();
    projectCache.evict(Project.nameKey(projectName));

    Optional<ProjectState> projectState = projectCache.get(Project.nameKey(projectName));
    AccountGroup.UUID ownerUUID = projectState.get().getOwners().iterator().next();
    ProjectConfig projectConfig = projectState.get().getConfig();

    assertThat(projectConfig.getAccessSections().size()).isEqualTo(2);

    AccessSection refsSection = projectConfig.getAccessSection("refs/*");
    assertThat(refsSection.getPermissions().size()).isEqualTo(3);
    assertThat(refsSection.getPermission(Permission.OWNER).getRules().get(0).getGroup().getUUID())
        .isEqualTo(ownerUUID);
    assertThat(refsSection.getPermission(Permission.READ).getRules().get(0).getGroup().getUUID())
        .isEqualTo(ownerUUID);
    assertThat(refsSection.getPermission(Permission.READ).getExclusiveGroup()).isTrue();
    assertThat(refsSection.getPermission(Permission.PUSH).getRules().get(0).getGroup().getName())
        .isEqualTo("Administrators");

    AccessSection refsHeadsSection = projectConfig.getAccessSection("refs/heads/*");
    assertThat(refsHeadsSection.getPermissions().size()).isEqualTo(4);
    assertThat(
            refsHeadsSection
                .getPermission(Permission.CREATE)
                .getRules()
                .get(0)
                .getGroup()
                .getUUID())
        .isEqualTo(ownerUUID);
    assertThat(
            refsHeadsSection.getPermission(Permission.PUSH).getRules().get(0).getGroup().getUUID())
        .isEqualTo(ownerUUID);
    assertThat(
            refsHeadsSection
                .getPermission(Permission.LABEL + "Code-Review")
                .getRules()
                .get(0)
                .getGroup()
                .getUUID())
        .isEqualTo(ownerUUID);
    assertThat(
            refsHeadsSection
                .getPermission(Permission.SUBMIT)
                .getRules()
                .get(0)
                .getGroup()
                .getUUID())
        .isEqualTo(ownerUUID);
  }

  @Test
  public void shoudNotConfigureForNonRootProject() throws Exception {
    ProjectInput in = new ProjectInput();
    in.permissionsOnly = false;
    String projectName = name("some/project");
    adminRestSession.put("/projects/" + Url.encode(projectName), in).assertCreated();

    assertThat(
            projectCache
                .get(Project.nameKey(projectName))
                .get()
                .getConfig()
                .getAccessSections()
                .size())
        .isEqualTo(0);
  }
}
