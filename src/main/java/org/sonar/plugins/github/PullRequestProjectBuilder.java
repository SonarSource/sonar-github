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

import org.kohsuke.github.GHCommitState;
import org.sonar.api.CoreProperties;
import org.sonar.api.batch.AnalysisMode;
import org.sonar.api.batch.bootstrap.ProjectBuilder;
import org.sonar.api.utils.MessageException;

/**
 * Trigger load of pull request metadata at the very beginning of SQ analysis. Also
 * set "in progress" status on the pull request. 
 *
 */
public class PullRequestProjectBuilder extends ProjectBuilder {

  private final GitHubPluginConfiguration gitHubPluginConfiguration;
  private final PullRequestFacade pullRequestFacade;
  private final AnalysisMode mode;

  public PullRequestProjectBuilder(GitHubPluginConfiguration gitHubPluginConfiguration, PullRequestFacade pullRequestFacade, AnalysisMode mode) {
    this.gitHubPluginConfiguration = gitHubPluginConfiguration;
    this.pullRequestFacade = pullRequestFacade;
    this.mode = mode;
  }

  @Override
  public void build(Context context) {
    if (!gitHubPluginConfiguration.isEnabled()) {
      return;
    }
    checkMode();
    int pullRequestNumber = gitHubPluginConfiguration.pullRequestNumber();
    pullRequestFacade.init(pullRequestNumber, context.projectReactor().getRoot().getBaseDir());

    pullRequestFacade.createOrUpdateSonarQubeStatus(GHCommitState.PENDING, "SonarQube analysis in progress");
  }

  private void checkMode() {
    if (!mode.isIssues()) {
      throw MessageException.of("The GitHub plugin is only intended to be used in preview or issues mode. Please set '" + CoreProperties.ANALYSIS_MODE + "'.");
    }

  }

}
