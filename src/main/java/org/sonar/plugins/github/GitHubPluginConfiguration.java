/*
 * SonarQube :: GitHub Plugin
 * Copyright (C) 2015 SonarSource
 * sonarqube@googlegroups.com
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.CheckForNull;
import org.sonar.api.BatchComponent;
import org.sonar.api.CoreProperties;
import org.sonar.api.batch.InstantiationStrategy;
import org.sonar.api.config.Settings;

@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
public class GitHubPluginConfiguration implements BatchComponent {

  private Settings settings;
  private Pattern gitSshPattern;
  private Pattern gitHttpPattern;

  public GitHubPluginConfiguration(Settings settings) {
    this.settings = settings;
    this.gitSshPattern = Pattern.compile(".*@github\\.com:(.*/.*)\\.git");
    this.gitHttpPattern = Pattern.compile("https?://github\\.com/(.*/.*)\\.git");
  }

  public int pullRequestNumber() {
    return settings.getInt(GitHubPlugin.GITHUB_PULL_REQUEST);
  }

  @CheckForNull
  public String repository() {
    String repo = null;
    if (settings.hasKey(GitHubPlugin.GITHUB_REPO)) {
      String urlOrRepo = settings.getString(GitHubPlugin.GITHUB_REPO);
      repo = parseGitUrl(urlOrRepo);
      if (repo == null) {
        repo = urlOrRepo;
      }
    }
    if (repo == null && settings.hasKey(CoreProperties.LINKS_SOURCES_DEV)) {
      String url = settings.getString(CoreProperties.LINKS_SOURCES_DEV);
      repo = parseGitUrl(url);
    }
    if (repo == null && settings.hasKey(CoreProperties.LINKS_SOURCES)) {
      String url = settings.getString(CoreProperties.LINKS_SOURCES);
      repo = parseGitUrl(url);
    }
    return repo;
  }

  @CheckForNull
  private String parseGitUrl(String urlOrRepo) {
    Matcher matcher = gitSshPattern.matcher(urlOrRepo);
    if (matcher.matches()) {
      return matcher.group(1);
    }
    matcher = gitHttpPattern.matcher(urlOrRepo);
    if (matcher.matches()) {
      return matcher.group(1);
    }
    return null;
  }

  @CheckForNull
  public String oauth() {
    return settings.getString(GitHubPlugin.GITHUB_OAUTH);
  }

  @CheckForNull
  public String login() {
    return settings.getString(GitHubPlugin.GITHUB_LOGIN);
  }

  public boolean isEnabled() {
    return settings.hasKey(GitHubPlugin.GITHUB_PULL_REQUEST);
  }

  public String endpoint() {
    return settings.getString(GitHubPlugin.GITHUB_ENDPOINT);
  }

}
