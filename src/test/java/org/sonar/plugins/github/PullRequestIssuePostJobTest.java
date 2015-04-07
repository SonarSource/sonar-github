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

import org.junit.Before;
import org.junit.Test;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.Settings;
import org.sonar.api.issue.Issue;
import org.sonar.api.issue.ProjectIssues;
import org.sonar.api.resources.Project;

import java.util.Arrays;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class PullRequestIssuePostJobTest {

  private Settings settings;
  private PullRequestIssuePostJob pullRequestIssuePostJob;
  private PullRequestFacade pullRequestFacade;
  private ProjectIssues issues;

  @Before
  public void prepare() {
    settings = new Settings(new PropertyDefinitions(GitHubPlugin.class));
    pullRequestFacade = mock(PullRequestFacade.class);
    issues = mock(ProjectIssues.class);
    when(issues.issues()).thenReturn(Arrays.<Issue>asList());
    pullRequestIssuePostJob = new PullRequestIssuePostJob(issues, new GitHubPluginConfiguration(settings), pullRequestFacade, mock(FileCache.class));

  }

  @Test
  public void noExecutionIfNoPullRequestNumber() {
    pullRequestIssuePostJob.executeOn(new Project("foo"), mock(SensorContext.class));
    verifyNoMoreInteractions(pullRequestFacade);
  }

  @Test
  public void testPullRequestAnalysisNoIssue() {
    settings.setProperty(GitHubPlugin.GITHUB_PULL_REQUEST, "1");
    pullRequestIssuePostJob.executeOn(new Project("foo"), mock(SensorContext.class));
    verify(pullRequestFacade).addGlobalComment("SonarQube analysis reported no new issue.");
  }
}
