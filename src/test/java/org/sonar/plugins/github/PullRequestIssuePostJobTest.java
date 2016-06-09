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

import java.util.Arrays;
import javax.annotation.CheckForNull;
import org.junit.Before;
import org.junit.Test;
import org.kohsuke.github.GHCommitState;
import org.mockito.ArgumentCaptor;
import org.sonar.api.CoreProperties;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.postjob.PostJobContext;
import org.sonar.api.batch.postjob.issue.PostJobIssue;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.Settings;
import org.sonar.api.rule.RuleKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PullRequestIssuePostJobTest {

  private PullRequestIssuePostJob pullRequestIssuePostJob;
  private PullRequestFacade pullRequestFacade;
  private PostJobContext context;

  @Before
  public void prepare() throws Exception {
    pullRequestFacade = mock(PullRequestFacade.class);
    Settings settings = new Settings(new PropertyDefinitions(PropertyDefinition.builder(CoreProperties.SERVER_BASE_URL)
      .name("Server base URL")
      .description("HTTP URL of this SonarQube server, such as <i>http://yourhost.yourdomain/sonar</i>. This value is used i.e. to create links in emails.")
      .category(CoreProperties.CATEGORY_GENERAL)
      .defaultValue(CoreProperties.SERVER_BASE_URL_DEFAULT_VALUE)
      .build()));
    GitHubPluginConfiguration config = new GitHubPluginConfiguration(settings);
    context = mock(PostJobContext.class);

    settings.setProperty("sonar.host.url", "http://192.168.0.1");
    settings.setProperty(CoreProperties.SERVER_BASE_URL, "http://myserver");
    pullRequestIssuePostJob = new PullRequestIssuePostJob(config, pullRequestFacade, new MarkDownUtils(settings));
  }

  private PostJobIssue newMockedIssue(String componentKey, @CheckForNull DefaultInputFile inputFile, @CheckForNull Integer line, Severity severity,
    boolean isNew, String message) {
    PostJobIssue issue = mock(PostJobIssue.class);
    when(issue.inputComponent()).thenReturn(inputFile);
    when(issue.componentKey()).thenReturn(componentKey);
    if (line != null) {
      when(issue.line()).thenReturn(line);
    }
    when(issue.ruleKey()).thenReturn(RuleKey.of("repo", "rule"));
    when(issue.severity()).thenReturn(severity);
    when(issue.isNew()).thenReturn(isNew);
    when(issue.message()).thenReturn(message);

    return issue;
  }

  private PostJobIssue newMockedIssue(String componentKey, Severity severity, boolean isNew, String message) {
    return newMockedIssue(componentKey, null, null, severity, isNew, message);
  }

  @Test
  public void testPullRequestAnalysisNoIssue() {
    when(context.issues()).thenReturn(Arrays.<PostJobIssue>asList());
    pullRequestIssuePostJob.execute(context);
    verify(pullRequestFacade).createOrUpdateGlobalComments(null);
    verify(pullRequestFacade).createOrUpdateSonarQubeStatus(GHCommitState.SUCCESS, "SonarQube reported no issues");
  }

  @Test
  public void testPullRequestAnalysisWithNewIssues() {
    DefaultInputFile inputFile1 = new DefaultInputFile("foo", "src/Foo.php");
    PostJobIssue newIssue = newMockedIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, true, "msg1");
    when(pullRequestFacade.getGithubUrl(inputFile1, 1)).thenReturn("http://github/blob/abc123/src/Foo.php#L1");

    PostJobIssue lineNotVisible = newMockedIssue("foo:src/Foo.php", inputFile1, 2, Severity.BLOCKER, true, "msg2");
    when(pullRequestFacade.getGithubUrl(inputFile1, 2)).thenReturn("http://github/blob/abc123/src/Foo.php#L2");

    DefaultInputFile inputFile2 = new DefaultInputFile("foo", "src/Foo2.php");
    PostJobIssue fileNotInPR = newMockedIssue("foo:src/Foo2.php", inputFile2, 1, Severity.BLOCKER, true, "msg3");

    PostJobIssue notNewIssue = newMockedIssue("foo:src/Foo.php", inputFile1, 1, Severity.BLOCKER, false, "msg");

    PostJobIssue issueOnDir = newMockedIssue("foo:src", Severity.BLOCKER, true, "msg4");

    PostJobIssue issueOnProject = newMockedIssue("foo", Severity.BLOCKER, true, "msg");

    PostJobIssue globalIssue = newMockedIssue("foo:src/Foo.php", inputFile1, null, Severity.BLOCKER, true, "msg5");

    when(context.issues()).thenReturn(Arrays.<PostJobIssue>asList(newIssue, globalIssue, issueOnProject, issueOnDir, fileNotInPR, lineNotVisible, notNewIssue));
    when(pullRequestFacade.hasFile(inputFile1)).thenReturn(true);
    when(pullRequestFacade.hasFileLine(inputFile1, 1)).thenReturn(true);

    pullRequestIssuePostJob.execute(context);
    verify(pullRequestFacade).createOrUpdateGlobalComments(contains("SonarQube analysis reported 5 issues"));
    verify(pullRequestFacade)
      .createOrUpdateGlobalComments(contains("* ![BLOCKER](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-blocker.png) 5 blocker"));
    verify(pullRequestFacade)
      .createOrUpdateGlobalComments(
        not(contains("1. [Project")));
    verify(pullRequestFacade)
      .createOrUpdateGlobalComments(
        contains(
          "1. ![BLOCKER](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-blocker.png) [Foo.php#L2](http://github/blob/abc123/src/Foo.php#L2): msg2 [![rule](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule)"));

    verify(pullRequestFacade).createOrUpdateSonarQubeStatus(GHCommitState.ERROR, "SonarQube reported 5 issues, with 5 blocker");
  }

  @Test
  public void testSortIssues() {
    ArgumentCaptor<String> commentCaptor = forClass(String.class);
    DefaultInputFile inputFile1 = new DefaultInputFile("foo", "src/Foo.php");
    DefaultInputFile inputFile2 = new DefaultInputFile("foo", "src/Foo2.php");

    // Blocker and 8th line => Should be displayed in 3rd position
    PostJobIssue newIssue = newMockedIssue("foo:src/Foo.php", inputFile1, 8, Severity.BLOCKER, true, "msg1");
    when(pullRequestFacade.getGithubUrl(inputFile1, 1)).thenReturn("http://github/blob/abc123/src/Foo.php#L1");

    // Blocker and 2nd line (Foo2.php) => Should be displayed in 4th position
    PostJobIssue issueInSecondFile = newMockedIssue("foo:src/Foo2.php", inputFile2, 2, Severity.BLOCKER, true, "msg2");
    when(pullRequestFacade.getGithubUrl(inputFile1, 2)).thenReturn("http://github/blob/abc123/src/Foo.php#L2");

    // Major => Should be displayed in 6th position
    PostJobIssue newIssue2 = newMockedIssue("foo:src/Foo.php", inputFile1, 4, Severity.MAJOR, true, "msg3");

    // Critical => Should be displayed in 5th position
    PostJobIssue newIssue3 = newMockedIssue("foo:src/Foo.php", inputFile1, 3, Severity.CRITICAL, true, "msg4");

    // Critical => Should be displayed in 7th position
    PostJobIssue newIssue4 = newMockedIssue("foo:src/Foo.php", inputFile1, 13, Severity.INFO, true, "msg5");

    // Blocker on project => Should be displayed 1st position
    PostJobIssue issueOnProject = newMockedIssue("foo", Severity.BLOCKER, true, "msg6");

    // Blocker and no line => Should be displayed in 2nd position
    PostJobIssue globalIssue = newMockedIssue("foo:src/Foo.php", inputFile1, null, Severity.BLOCKER, true, "msg7");

    when(context.issues()).thenReturn(Arrays.<PostJobIssue>asList(newIssue, globalIssue, issueOnProject, newIssue4, newIssue2, issueInSecondFile, newIssue3));
    when(pullRequestFacade.hasFile(any(InputFile.class))).thenReturn(true);
    when(pullRequestFacade.hasFileLine(any(InputFile.class), anyInt())).thenReturn(false);

    pullRequestIssuePostJob.execute(context);

    verify(pullRequestFacade).createOrUpdateGlobalComments(commentCaptor.capture());

    String comment = commentCaptor.getValue();
    assertThat(comment).containsSequence("msg6", "msg7", "msg1", "msg2", "msg4", "msg3", "msg5");
  }

  @Test
  public void testPullRequestAnalysisWithNewCriticalIssues() {
    DefaultInputFile inputFile1 = new DefaultInputFile("foo", "src/Foo.php");
    PostJobIssue newIssue = newMockedIssue("foo:src/Foo.php", inputFile1, 1, Severity.CRITICAL, true, "msg1");
    when(pullRequestFacade.getGithubUrl(inputFile1, 1)).thenReturn("http://github/blob/abc123/src/Foo.php#L1");

    when(context.issues()).thenReturn(Arrays.<PostJobIssue>asList(newIssue));
    when(pullRequestFacade.hasFile(inputFile1)).thenReturn(true);
    when(pullRequestFacade.hasFileLine(inputFile1, 1)).thenReturn(true);

    pullRequestIssuePostJob.execute(context);

    verify(pullRequestFacade).createOrUpdateSonarQubeStatus(GHCommitState.ERROR, "SonarQube reported 1 issue, with 1 critical");
  }

  @Test
  public void testPullRequestAnalysisWithNewIssuesNoBlockerNorCritical() {
    DefaultInputFile inputFile1 = new DefaultInputFile("foo", "src/Foo.php");
    PostJobIssue newIssue = newMockedIssue("foo:src/Foo.php", inputFile1, 1, Severity.MAJOR, true, "msg1");
    when(pullRequestFacade.getGithubUrl(inputFile1, 1)).thenReturn("http://github/blob/abc123/src/Foo.php#L1");

    when(context.issues()).thenReturn(Arrays.<PostJobIssue>asList(newIssue));
    when(pullRequestFacade.hasFile(inputFile1)).thenReturn(true);
    when(pullRequestFacade.hasFileLine(inputFile1, 1)).thenReturn(true);

    pullRequestIssuePostJob.execute(context);

    verify(pullRequestFacade).createOrUpdateSonarQubeStatus(GHCommitState.SUCCESS, "SonarQube reported 1 issue, no criticals or blockers");
  }

  @Test
  public void testPullRequestAnalysisWithNewBlockerAndCriticalIssues() {
    DefaultInputFile inputFile1 = new DefaultInputFile("foo", "src/Foo.php");
    PostJobIssue newIssue = newMockedIssue("foo:src/Foo.php", inputFile1, 1, Severity.CRITICAL, true, "msg1");
    when(pullRequestFacade.getGithubUrl(inputFile1, 1)).thenReturn("http://github/blob/abc123/src/Foo.php#L1");

    PostJobIssue lineNotVisible = newMockedIssue("foo:src/Foo.php", inputFile1, 2, Severity.BLOCKER, true, "msg2");
    when(pullRequestFacade.getGithubUrl(inputFile1, 2)).thenReturn("http://github/blob/abc123/src/Foo.php#L2");

    when(context.issues()).thenReturn(Arrays.<PostJobIssue>asList(newIssue, lineNotVisible));
    when(pullRequestFacade.hasFile(inputFile1)).thenReturn(true);
    when(pullRequestFacade.hasFileLine(inputFile1, 1)).thenReturn(true);

    pullRequestIssuePostJob.execute(context);

    verify(pullRequestFacade).createOrUpdateSonarQubeStatus(GHCommitState.ERROR, "SonarQube reported 2 issues, with 1 critical and 1 blocker");
  }
}
