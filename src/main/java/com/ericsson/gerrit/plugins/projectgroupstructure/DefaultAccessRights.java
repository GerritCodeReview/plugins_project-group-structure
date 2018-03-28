// Copyright (C) 2018 Ericsson
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

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.common.data.RefConfigSection;
import com.google.gerrit.common.errors.InvalidNameException;
import com.google.gerrit.extensions.annotations.PluginData;
import com.google.gerrit.extensions.events.NewProjectCreatedListener;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.RefPattern;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Set defaults access rights for root projects.
 *
 * <p>Default access rights are read from <review_site>/data/project-group-structure/project.config.
 * The format of that file is the same as regular project.config except that group, in addition to
 * be a group name, can be set to token ${owner} instead which will be replaced by the group owning
 * the project.
 */
@Singleton
public class DefaultAccessRights implements NewProjectCreatedListener {
  private static final Logger log = LoggerFactory.getLogger(DefaultAccessRights.class);
  private static final String OWNER_TOKEN = "${owner}";

  private final GroupCache groupCache;
  private final ProjectCache projectCache;
  private final MetaDataUpdate.User metaDataUpdateFactory;
  private final FileBasedConfig defaultAccessRightsConfig;

  @Inject
  public DefaultAccessRights(
      MetaDataUpdate.User metaDataUpdateFactory,
      ProjectCache projectCache,
      GroupCache groupCache,
      @PluginData Path dataDir) {
    this.groupCache = groupCache;
    this.projectCache = projectCache;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    defaultAccessRightsConfig =
        new FileBasedConfig(dataDir.resolve(ProjectConfig.PROJECT_CONFIG).toFile(), FS.DETECTED);
    try {
      defaultAccessRightsConfig.load();
    } catch (IOException | ConfigInvalidException e) {
      // Swallow the exception to allow the plugin to load, we still want the
      // project structure to be enforced even if defaults access rights will
      // not be set.
      log.error(
          "Failed to load default access rights config {}, no access right will be set on root projects: {}",
          defaultAccessRightsConfig.getFile().getAbsolutePath(),
          e.getMessage(),
          e);
    }
  }

  @Override
  public void onNewProjectCreated(NewProjectCreatedListener.Event event) {
    String projectName = event.getProjectName();
    // only set default access rights for root projects, if configured.
    if (projectName.contains("/") || defaultAccessRightsConfig.getSections().isEmpty()) {
      return;
    }

    ProjectState project = projectCache.get(Project.NameKey.parse(projectName));
    if (project == null) {
      log.error("Could not retrieve projet {} from cache", projectName);
      return;
    }

    try (MetaDataUpdate md = metaDataUpdateFactory.create(project.getProject().getNameKey())) {
      ProjectConfig config = ProjectConfig.read(md);
      setAccessRights(config, project);
      md.setMessage("Set default access rights\n");
      config.commit(md);
    } catch (Exception e) {
      log.error("Failed to set defauts access rights {}", e.getMessage(), e);
    }
  }

  private void setAccessRights(ProjectConfig config, ProjectState project) {
    for (String refName : defaultAccessRightsConfig.getSubsections(ProjectConfig.ACCESS)) {
      if (RefConfigSection.isValid(refName) && isValidRegex(refName)) {
        AccessSection as = config.getAccessSection(refName, true);
        for (String varName :
            defaultAccessRightsConfig.getStringList(
                ProjectConfig.ACCESS, refName, "exclusiveGroupPermissions")) {
          for (String n : varName.split("[, \t]{1,}")) {
            if (Permission.isPermission(n)) {
              as.getPermission(n, true).setExclusiveGroup(true);
            }
          }
        }
        String ownerGroupName = getOwerGroupName(project);
        for (String value : defaultAccessRightsConfig.getNames(ProjectConfig.ACCESS, refName)) {
          if (Permission.isPermission(value)) {
            Permission perm = as.getPermission(value, true);
            setPermissionRules(ownerGroupName, perm, refName, value);
          } else {
            log.error("Invalid permission {}", value);
          }
        }
      }
    }
  }

  private String getOwerGroupName(ProjectState project) {
    Set<AccountGroup.UUID> owners = project.getAllOwners();
    if (!owners.isEmpty()) {
      Optional<InternalGroup> owner = groupCache.get(owners.iterator().next());
      if(owner.isPresent()) {
        return owner.get().getName();
      }
    }
    return String.format("no owners for project %s", project.getProject().getName());
  }

  private boolean isValidRegex(String refPattern) {
    try {
      RefPattern.validateRegExp(refPattern);
    } catch (InvalidNameException e) {
      log.error("Invalid ref name: " + e.getMessage());
      return false;
    }
    return true;
  }

  private void setPermissionRules(
      String ownerGroupName, Permission perm, String refName, String value) {
    for (String ruleString :
        defaultAccessRightsConfig.getStringList(ProjectConfig.ACCESS, refName, value)) {
      PermissionRule rule;
      try {
        rule =
            PermissionRule.fromString(
                ruleString.replaceAll(Pattern.quote(OWNER_TOKEN), ownerGroupName),
                Permission.hasRange(value));

      } catch (IllegalArgumentException notRule) {
        log.error(
            "Invalid rule in {}{}.{}: {}",
            ProjectConfig.ACCESS,
            refName != null ? "." + refName : "",
            value,
            notRule.getMessage());
        continue;
      }

      if (rule.getGroup().getUUID() == null) {
        // this means that group is not already in the groups file, so
        // we need to check if group exist if if it does, get its
        // uuid.
        Optional<InternalGroup> group = groupCache.get(new AccountGroup.NameKey(rule.getGroup().getName()));

        if (!group.isPresent()) {
          log.error("Group {} not found", rule.getGroup().getName());
          continue;
        }
        rule.getGroup().setUUID(group.get().getGroupUUID());
      }
      perm.add(rule);
    }
  }
}
