/*
 * SonarQube :: GitHub Plugin
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

import java.util.Arrays;
import java.util.List;
import org.sonar.api.CoreProperties;
import org.sonar.api.Properties;
import org.sonar.api.Property;
import org.sonar.api.PropertyType;
import org.sonar.api.SonarPlugin;

@Properties({
  @Property(
    key = GitHubPlugin.GITHUB_LOGIN,
    name = "GitHub Login",
    description = "GitHub account used to perform operations like adding comments on pull requests.",
    global = false
  ),
  @Property(
    key = GitHubPlugin.GITHUB_OAUTH,
    name = "GitHub OAuth token",
    description = "Authentication token",
    type = PropertyType.PASSWORD,
    global = false
  ),
  @Property(
    key = GitHubPlugin.GITHUB_REPO,
    name = "GitHub repository",
    description = "GitHub repository for this project. Will be guessed from '" + CoreProperties.LINKS_SOURCES_DEV + "' if present",
    global = false,
    project = false
  ),
  @Property(
    key = GitHubPlugin.GITHUB_PULL_REQUEST,
    name = "GitHub Pull Request",
    description = "Pull request number",
    type = PropertyType.INTEGER,
    global = false,
    project = false,
    module = false
  )
})
public class GitHubPlugin extends SonarPlugin {

  public static final String GITHUB_LOGIN = "sonar.github.login";
  public static final String GITHUB_OAUTH = "sonar.github.oauth";
  public static final String GITHUB_REPO = "sonar.github.repository";
  public static final String GITHUB_PULL_REQUEST = "sonar.github.pullRequest";

  @Override
  public List getExtensions() {
    return Arrays.asList(
      PullRequestIssuePostJob.class,
      GitHubPluginConfiguration.class,
      PullRequestProjectBuilder.class,
      PullRequestFacade.class,
      PullRequestInputFileFilter.class,
      InputFileCacheSensor.class,
      InputFileCache.class);
  }

}
