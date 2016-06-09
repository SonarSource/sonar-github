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

import java.io.File;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.batch.AnalysisMode;
import org.sonar.api.batch.bootstrap.ProjectBuilder;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.Settings;
import org.sonar.api.utils.MessageException;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class PullRequestProjectBuilderTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private PullRequestProjectBuilder pullRequestProjectBuilder;
  private PullRequestFacade facade;
  private Settings settings;
  private AnalysisMode mode;

  @Before
  public void prepare() {
    settings = new Settings(new PropertyDefinitions(GitHubPlugin.class));
    facade = mock(PullRequestFacade.class);
    mode = mock(AnalysisMode.class);
    pullRequestProjectBuilder = new PullRequestProjectBuilder(new GitHubPluginConfiguration(settings), facade, mode);

  }

  @Test
  public void shouldDoNothing() {
    pullRequestProjectBuilder.build(null);
    verifyZeroInteractions(facade);
  }

  @Test
  public void shouldFailIfNotPreview() {
    settings.setProperty(GitHubPlugin.GITHUB_PULL_REQUEST, "1");

    thrown.expect(MessageException.class);
    thrown.expectMessage("The GitHub plugin is only intended to be used in preview or issues mode. Please set 'sonar.analysis.mode'.");

    pullRequestProjectBuilder.build(null);
  }

  @Test
  public void shouldNotFailIfIssues() {
    settings.setProperty(GitHubPlugin.GITHUB_PULL_REQUEST, "1");
    when(mode.isIssues()).thenReturn(true);

    pullRequestProjectBuilder.build(mock(ProjectBuilder.Context.class, withSettings().defaultAnswer(RETURNS_DEEP_STUBS)));

    verify(facade).init(eq(1), any(File.class));
  }
}
