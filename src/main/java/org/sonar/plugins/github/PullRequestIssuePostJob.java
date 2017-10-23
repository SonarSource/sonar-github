/*
 * SonarQube :: GitHub Plugin
 * Copyright (C) 2015-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
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

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import org.apache.commons.lang.StringUtils;

import org.kohsuke.github.GHCommitState;
import org.sonar.api.batch.fs.InputComponent;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.postjob.PostJob;
import org.sonar.api.batch.postjob.PostJobContext;
import org.sonar.api.batch.postjob.PostJobDescriptor;
import org.sonar.api.batch.postjob.issue.PostJobIssue;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

/**
 * Compute comments to be added on the pull request.
 */
public class PullRequestIssuePostJob implements PostJob {
  private static final Logger LOG = Loggers.get(PullRequestFacade.class);

  private static final Comparator<PostJobIssue> ISSUE_COMPARATOR = new IssueComparator();

  private final PullRequestFacade pullRequestFacade;
  private final GitHubPluginConfiguration gitHubPluginConfiguration;
  private final MarkDownUtils markDownUtils;

  public PullRequestIssuePostJob(GitHubPluginConfiguration gitHubPluginConfiguration, PullRequestFacade pullRequestFacade, MarkDownUtils markDownUtils) {
    this.gitHubPluginConfiguration = gitHubPluginConfiguration;
    this.pullRequestFacade = pullRequestFacade;
    this.markDownUtils = markDownUtils;
  }

  @Override
  public void describe(PostJobDescriptor descriptor) {
    descriptor
      .name("GitHub Pull Request Issue Publisher")
      .requireProperty(GitHubPlugin.GITHUB_PULL_REQUEST);
  }

  @Override
  public void execute(PostJobContext context) {
    String projectKey = gitHubPluginConfiguration.projectKey();

    GlobalReport report = new GlobalReport(markDownUtils, gitHubPluginConfiguration.tryReportIssuesInline(), projectKey);
    try {
      Map<InputFile, Map<Integer, StringBuilder>> commentsToBeAddedByLine = processIssues(report, context.issues(), projectKey);

      updateReviewComments(commentsToBeAddedByLine);

      pullRequestFacade.deleteOutdatedComments();

      pullRequestFacade.createOrUpdateGlobalComments(report.hasNewIssue() ? report.formatForMarkdown() : null);

      pullRequestFacade.createOrUpdateSonarQubeStatus(report.getStatus(), report.getStatusDescription());
    } catch (Exception e) {
      LOG.error("SonarQube analysis failed to complete the review of this pull request", e);
      pullRequestFacade.createOrUpdateSonarQubeStatus(GHCommitState.ERROR, StringUtils.abbreviate("SonarQube analysis failed: " + e.getMessage(), 140));
    }
  }

  private Map<InputFile, Map<Integer, StringBuilder>> processIssues(
          GlobalReport report, Iterable<PostJobIssue> issues, @Nullable String projectKey) {
    Map<InputFile, Map<Integer, StringBuilder>> commentToBeAddedByFileAndByLine = new HashMap<>();

    StreamSupport.stream(issues.spliterator(), false)
      .filter(PostJobIssue::isNew)
      // SONARGITUB-13 Ignore issues on files not modified by the P/R
      .filter(i -> {
        InputComponent inputComponent = i.inputComponent();
        return inputComponent == null ||
          !inputComponent.isFile() ||
          pullRequestFacade.hasFile((InputFile) inputComponent);
      })
      .sorted(ISSUE_COMPARATOR)
      .forEach(i -> processIssue(report, commentToBeAddedByFileAndByLine, i, projectKey));
    return commentToBeAddedByFileAndByLine;

  }

  private void processIssue(GlobalReport report, Map<InputFile, Map<Integer, StringBuilder>> commentToBeAddedByFileAndByLine, PostJobIssue issue, @Nullable String projectKey) {
    boolean reportedInline = false;
    InputComponent inputComponent = issue.inputComponent();
    if (gitHubPluginConfiguration.tryReportIssuesInline() && inputComponent != null && inputComponent.isFile()) {
      reportedInline = tryReportInline(commentToBeAddedByFileAndByLine, issue, (InputFile) inputComponent, projectKey);
    }
    report.process(issue, pullRequestFacade.getGithubUrl(inputComponent, issue.line()), reportedInline);
  }

  private boolean tryReportInline(Map<InputFile, Map<Integer, StringBuilder>> commentToBeAddedByFileAndByLine, PostJobIssue issue, InputFile inputFile, @Nullable String projectKey) {
    Integer lineOrNull = issue.line();
    if (lineOrNull != null) {
      int line = lineOrNull.intValue();
      if (pullRequestFacade.hasFileLine(inputFile, line)) {
        String message = issue.message();
        String ruleKey = issue.ruleKey().toString();
        if (!commentToBeAddedByFileAndByLine.containsKey(inputFile)) {
          commentToBeAddedByFileAndByLine.put(inputFile, new HashMap<>());
        }
        Map<Integer, StringBuilder> commentsByLine = commentToBeAddedByFileAndByLine.get(inputFile);

        if (!commentsByLine.containsKey(line)) {
          StringBuilder stringBuilder = new StringBuilder();
          commentsByLine.put(line, stringBuilder);
        }

        commentsByLine.get(line).append(markDownUtils.inlineIssue(issue.severity(), message, ruleKey)).append("\n");

        if (!StringUtils.isEmpty(projectKey)) {
          commentsByLine.get(line).append(MarkDownUtils.projectId(projectKey)).append("\n");
        }

        return true;
      }
    }
    return false;
  }

  private void updateReviewComments(Map<InputFile, Map<Integer, StringBuilder>> commentsToBeAddedByLine) {
    for (Map.Entry<InputFile, Map<Integer, StringBuilder>> entry : commentsToBeAddedByLine.entrySet()) {
      for (Map.Entry<Integer, StringBuilder> entryPerLine : entry.getValue().entrySet()) {
        String body = entryPerLine.getValue().toString();
        pullRequestFacade.createOrUpdateReviewComment(entry.getKey(), entryPerLine.getKey(), body);
      }
    }
  }

}
