/*
 * SonarQube :: GitHub Integration
 * Copyright (C) 2015 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.plugins.github;

import org.sonar.api.BatchComponent;
import org.sonar.api.CoreProperties;
import org.sonar.api.batch.InstantiationStrategy;
import org.sonar.api.config.Settings;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
public class GitHubPluginConfiguration implements BatchComponent {

  private Settings settings;
  private Pattern gitPattern;

  public GitHubPluginConfiguration(Settings settings) {
    this.settings = settings;
    this.gitPattern = Pattern.compile(".*@github\\.com:(.*/.*)\\.git");
  }

  public int pullRequestNumber() {
    return settings.getInt(GitHubPlugin.GITHUB_PULL_REQUEST);
  }

  public String repository() {
    if (settings.hasKey(GitHubPlugin.GITHUB_REPO)) {
      return settings.getString(GitHubPlugin.GITHUB_REPO);
    }
    if (settings.hasKey(CoreProperties.LINKS_SOURCES_DEV)) {
      String url = settings.getString(CoreProperties.LINKS_SOURCES_DEV);
      Matcher matcher = gitPattern.matcher(url);
      if (matcher.matches()) {
        return matcher.group(1);
      }
    }
    if (settings.hasKey(CoreProperties.LINKS_SOURCES)) {
      String url = settings.getString(CoreProperties.LINKS_SOURCES);
      Matcher matcher = gitPattern.matcher(url);
      if (matcher.matches()) {
        return matcher.group(1);
      }
    }
    return null;
  }

  public String oauth() {
    return settings.getString(GitHubPlugin.GITHUB_OAUTH);
  }

  public String login() {
    return settings.getString(GitHubPlugin.GITHUB_LOGIN);
  }

}
