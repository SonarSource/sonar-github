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

import org.kohsuke.github.GHCommitState;
import org.sonar.api.batch.bootstrap.ProjectBuilder;

public class PullRequestProjectBuilder extends ProjectBuilder {

  private final GitHubPluginConfiguration gitHubPluginConfiguration;
  private final PullRequestFacade pullRequestFacade;
  private final FileCache fileCache;

  public PullRequestProjectBuilder(GitHubPluginConfiguration gitHubPluginConfiguration, PullRequestFacade pullRequestFacade, FileCache fileCache) {
    this.gitHubPluginConfiguration = gitHubPluginConfiguration;
    this.pullRequestFacade = pullRequestFacade;
    this.fileCache = fileCache;
  }

  @Override
  public void build(Context context) {
    int pullRequestNumber = gitHubPluginConfiguration.pullRequestNumber();
    if (pullRequestNumber == 0) {
      return;
    }
    fileCache.setProjectBaseDir(context.projectReactor().getRoot().getBaseDir());
    pullRequestFacade.init(pullRequestNumber);

    pullRequestFacade.createOrUpdateSonarQubeStatus(GHCommitState.PENDING, "SonarQube analysis in progress");
  }

}
