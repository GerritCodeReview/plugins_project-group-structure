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
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
class Configuration {
  private static final Logger log = LoggerFactory.getLogger(Configuration.class);

  private static final String NAME_REGEX = "nameRegex";
  private static final String DEFAULT_NAME_REGEX_VALUE = ".+";
  private static final String DEFAULT_NAME_REGEX_MESSAGE =
      "The value of the regex is invalid because it doesn't accept / or it accepts spaces."
          + " Using default "
          + DEFAULT_NAME_REGEX_VALUE
          + " regex.";

  private String regexNameFilter;

  @Inject
  Configuration(PluginConfigFactory pluginConfigFactory, @PluginName String pluginName) {
    PluginConfig config = pluginConfigFactory.getFromGerritConfig(pluginName);
    regexNameFilter = config.getString(NAME_REGEX, DEFAULT_NAME_REGEX_VALUE);
    if (!"/".matches(regexNameFilter) || " ".matches(regexNameFilter)) {
      log.warn(DEFAULT_NAME_REGEX_MESSAGE);
      regexNameFilter = DEFAULT_NAME_REGEX_VALUE;
    }
  }

  public String getRegexNameFilter() {
    return regexNameFilter;
  }
}
