/*
 * SonarQube :: GitHub Plugin
 * Copyright (C) 2015-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.plugins.github;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.CheckForNull;
import org.sonar.api.CoreProperties;
import org.sonar.api.batch.BatchSide;
import org.sonar.api.batch.InstantiationStrategy;
import org.sonar.api.config.Settings;
import org.sonar.api.utils.MessageException;

import static org.apache.commons.lang.StringUtils.isNotBlank;

@BatchSide
@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
public class GitHubPluginConfiguration {

  public static final int MAX_GLOBAL_ISSUES = 10;

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

  public String repository() {
    if (settings.hasKey(GitHubPlugin.GITHUB_REPO)) {
      return repoFromProp();
    }
    if (isNotBlank(settings.getString(CoreProperties.LINKS_SOURCES_DEV)) || isNotBlank(settings.getString(CoreProperties.LINKS_SOURCES))) {
      return repoFromScmProps();
    }
    throw MessageException.of("Unable to determine GitHub repository name for this project. Please provide it using property '" + GitHubPlugin.GITHUB_REPO
      + "' or configure property '" + CoreProperties.LINKS_SOURCES + "'.");
  }

  private String repoFromScmProps() {
    String repo = null;
    if (isNotBlank(settings.getString(CoreProperties.LINKS_SOURCES_DEV))) {
      String url = settings.getString(CoreProperties.LINKS_SOURCES_DEV);
      repo = extractRepoFromGitUrl(url);
    }
    if (repo == null && isNotBlank(settings.getString(CoreProperties.LINKS_SOURCES))) {
      String url = settings.getString(CoreProperties.LINKS_SOURCES);
      repo = extractRepoFromGitUrl(url);
    }
    if (repo == null) {
      throw MessageException.of("Unable to parse GitHub repository name for this project. Please check configuration:\n  * " + CoreProperties.LINKS_SOURCES_DEV
        + ": " + settings.getString(CoreProperties.LINKS_SOURCES_DEV) + "\n  * " + CoreProperties.LINKS_SOURCES + ": " + settings.getString(CoreProperties.LINKS_SOURCES));
    }
    return repo;
  }

  private String repoFromProp() {
    String urlOrRepo = settings.getString(GitHubPlugin.GITHUB_REPO);
    String repo = extractRepoFromGitUrl(urlOrRepo);
    if (repo == null) {
      return urlOrRepo;
    }
    return repo;
  }

  @CheckForNull
  private String extractRepoFromGitUrl(String urlOrRepo) {
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

  public boolean isEnabled() {
    return settings.hasKey(GitHubPlugin.GITHUB_PULL_REQUEST);
  }

  public String endpoint() {
    return settings.getString(GitHubPlugin.GITHUB_ENDPOINT);
  }

  public boolean tryReportIssuesInline() {
    return !settings.getBoolean(GitHubPlugin.GITHUB_DISABLE_INLINE_COMMENTS);
  }

}
