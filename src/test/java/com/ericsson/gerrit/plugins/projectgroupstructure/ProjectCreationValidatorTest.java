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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import com.google.gerrit.acceptance.PluginDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.api.groups.GroupApi;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.ProjectState;

import org.junit.Before;
import org.junit.Test;

public class ProjectCreationValidatorTest extends PluginDaemonTest {

  @Before
  public void setUp() throws Exception {
    // These access rights are mandatory configuration for this plugin as
    // documented in config.md
    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.CREATE_GROUP);
    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.CREATE_PROJECT);
  }

  @Test
  public void shouldAllowAnyUsersToCreateUnderAllProjects() throws Exception {
    ProjectInput in = new ProjectInput();
    in.permissionsOnly = true;
    adminRestSession.put("/projects/" + name("someProject"), in)
        .assertCreated();

    userRestSession.put("/projects/" + name("someOtherProject"), in)
        .assertCreated();
  }

  @Test
  public void shouldBlockRootProjectWithSlashesInTheirName() throws Exception {
    // Root project is OK without slash
    String parent = name("parentProject");
    ProjectInput in = new ProjectInput();
    in.permissionsOnly = true;
    userRestSession.put("/projects/" + parent, in).assertCreated();

    // Creation is rejected when root project name contains slashes
    RestResponse r =
        userRestSession.put("/projects/" + Url.encode("a/parentProject"), in);
    r.assertConflict();
    assertThat(r.getEntityContent())
        .contains("Root project name cannot contains slashes");
  }

  @Test
  public void shouldBlockProjectWithParentNotPartOfProjectName()
      throws Exception {
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
    assertThat(r.getEntityContent())
        .contains("Project name must start with parent project name");

    // Creation is OK when project name starts with parent
    userRestSession.put("/projects/" + Url.encode(parent + "/childProject"), in)
        .assertCreated();

    // Creation is rejected when project name does not start with nested parent
    String nestedParent = parent + "/childProject";
    in.parent = nestedParent;
    r = userRestSession.put("/projects/grandchild", in);
    r.assertConflict();
    assertThat(r.getEntityContent())
        .contains("Project name must start with parent project name");

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
    RestResponse r = userRestSession
        .put("/projects/" + Url.encode(parent + "/childProject"), in);
    r.assertConflict();
    assertThat(r.getEntityContent())
        .contains("You must be owner of the parent project");

    // Creation is OK when user is owner of parent
    g.addMembers(user.username);
    userRestSession.put("/projects/" + Url.encode(parent + "/childProject"), in)
        .assertCreated();
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
    adminRestSession.put("/projects/" + Url.encode("orgA/childProject"), in)
        .assertCreated();
  }

  @Test
  public void shouldMakeUserOwnerOnRootProjectCreation() throws Exception {
    // normal case, when <project-name>-admins group does not exist
    String rootProject = name("rootProject");
    ProjectInput in = new ProjectInput();
    in.permissionsOnly = true;
    userRestSession.put("/projects/" + rootProject, in).assertCreated();
    ProjectState projectState =
        projectCache.get(new Project.NameKey(rootProject));
    assertThat(projectState.getOwners().size()).isEqualTo(1);
    assertThat(projectState.getOwners()).contains(groupCache
        .get(new AccountGroup.NameKey(rootProject + "-admins")).getGroupUUID());

    // case when <project-name>-admins group already exists
    rootProject = name("rootProject2");
    String existingGroupName = rootProject + "-admins";
    gApi.groups().create(existingGroupName);
    userRestSession.put("/projects/" + rootProject, in).assertCreated();
    projectState = projectCache.get(new Project.NameKey(rootProject));
    assertThat(projectState.getOwners().size()).isEqualTo(1);
    String expectedOwnerGroup = existingGroupName + "-"
        + Hashing.sha1().hashString(existingGroupName, Charsets.UTF_8)
            .toString().substring(0, 7);
    assertThat(projectState.getOwners()).contains(groupCache
        .get(new AccountGroup.NameKey(expectedOwnerGroup)).getGroupUUID());
  }

  @Test
  public void shouldBlockRootCodeProject() throws Exception {
    RestResponse r = userRestSession.put("/projects/" + Url.encode("project1"));
    r.assertConflict();
    assertThat(r.getEntityContent())
        .contains("Regular projects are not allowed as root");
  }
}
