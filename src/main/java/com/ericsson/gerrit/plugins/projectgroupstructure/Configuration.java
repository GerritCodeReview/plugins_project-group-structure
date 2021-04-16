// Copyright (C) 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License"),
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

import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
class Configuration {

  private static final String REGEX = "regex";
  private static final String DEFAULT_REGEX_VALUE =
      "The value of the regex is not set or it is invalid because it doesn't accept /."
          + "Using default empty regex.";

  private String regexNameFilter;
  private static final Logger log = LoggerFactory.getLogger(Configuration.class);

  @Inject
  Configuration(PluginConfigFactory pluginConfigFactory, @PluginName String pluginName) {
    Config config = pluginConfigFactory.getGlobalPluginConfig(pluginName);
    regexNameFilter = config.getString(pluginName, null, REGEX);
    if (regexNameFilter == null || (regexNameFilter != null && !"/".matches(regexNameFilter))) {
      regexNameFilter = "";
      log.warn(DEFAULT_REGEX_VALUE);
    }
  }

  public String RegexNameFilter() {
    return regexNameFilter;
  }
}
