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

import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.sonar.api.batch.CheckProject;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.issue.Issue;
import org.sonar.api.issue.ProjectIssues;
import org.sonar.api.resources.Project;

/**
 * Compute comments to be added on the pull request.
 */
public class PullRequestIssuePostJob implements org.sonar.api.batch.PostJob, CheckProject {
  private static final Comparator<Issue> ISSUE_COMPARATOR = new IssueComparator();

  private final PullRequestFacade pullRequestFacade;
  private final ProjectIssues projectIssues;
  private final GitHubPluginConfiguration gitHubPluginConfiguration;
  private final InputFileCache inputFileCache;
  private final MarkDownUtils markDownUtils;

  public PullRequestIssuePostJob(GitHubPluginConfiguration gitHubPluginConfiguration, PullRequestFacade pullRequestFacade, ProjectIssues projectIssues,
    InputFileCache inputFileCache, MarkDownUtils markDownUtils) {
    this.gitHubPluginConfiguration = gitHubPluginConfiguration;
    this.pullRequestFacade = pullRequestFacade;
    this.projectIssues = projectIssues;
    this.inputFileCache = inputFileCache;
    this.markDownUtils = markDownUtils;
  }

  @Override
  public boolean shouldExecuteOnProject(Project project) {
    return gitHubPluginConfiguration.isEnabled();
  }

  @Override
  public void executeOn(Project project, SensorContext context) {
    GlobalReport report = new GlobalReport(markDownUtils, gitHubPluginConfiguration.tryReportIssuesInline());
    Map<InputFile, Map<Integer, StringBuilder>> commentsToBeAddedByLine = processIssues(report);

    updateReviewComments(commentsToBeAddedByLine);

    pullRequestFacade.deleteOutdatedComments();

    pullRequestFacade.createOrUpdateGlobalComments(report.hasNewIssue() ? report.formatForMarkdown() : null);

    pullRequestFacade.createOrUpdateSonarQubeStatus(report.getStatus(), report.getStatusDescription());
  }

  @Override
  public String toString() {
    return "GitHub Pull Request Issue Publisher";
  }

  private static List<Issue> sortProjectIssues(ProjectIssues projectIssues) {
    // Dump issues to a new list and sort it.
    List<Issue> issues = Lists.newArrayList(projectIssues.issues());
    Collections.sort(issues, ISSUE_COMPARATOR);
    return issues;
  }

  private Map<InputFile, Map<Integer, StringBuilder>> processIssues(GlobalReport report) {
    Map<InputFile, Map<Integer, StringBuilder>> commentToBeAddedByFileAndByLine = new HashMap<>();

    List<Issue> issues = sortProjectIssues(projectIssues);
    for (Issue issue : issues) {
      if (!issue.isNew()) {
        continue;
      }
      String severity = issue.severity();
      Integer issueLine = issue.line();
      InputFile inputFile = inputFileCache.byKey(issue.componentKey());
      if (inputFile != null && !pullRequestFacade.hasFile(inputFile)) {
        // SONARGITUB-13 Ignore issues on files not modified by the P/R
        continue;
      }
      processIssue(report, commentToBeAddedByFileAndByLine, issue, severity, issueLine, inputFile);
    }
    return commentToBeAddedByFileAndByLine;
  }

  private void processIssue(GlobalReport report, Map<InputFile, Map<Integer, StringBuilder>> commentToBeAddedByFileAndByLine, Issue issue, String severity,
    @Nullable Integer issueLine,
    @Nullable InputFile inputFile) {
    boolean reportedInline = false;
    if (gitHubPluginConfiguration.tryReportIssuesInline()) {
      reportedInline = tryReportInline(commentToBeAddedByFileAndByLine, issue, severity, issueLine, inputFile);
    }
    report.process(issue, pullRequestFacade.getGithubUrl(inputFile, issueLine), reportedInline);
  }

  private boolean tryReportInline(Map<InputFile, Map<Integer, StringBuilder>> commentToBeAddedByFileAndByLine, Issue issue, String severity, @Nullable Integer issueLine,
    @Nullable InputFile inputFile) {
    if (inputFile != null && issueLine != null) {
      int line = issueLine.intValue();
      if (pullRequestFacade.hasFileLine(inputFile, line)) {
        String message = issue.message();
        String ruleKey = issue.ruleKey().toString();
        if (!commentToBeAddedByFileAndByLine.containsKey(inputFile)) {
          commentToBeAddedByFileAndByLine.put(inputFile, new HashMap<Integer, StringBuilder>());
        }
        Map<Integer, StringBuilder> commentsByLine = commentToBeAddedByFileAndByLine.get(inputFile);
        if (!commentsByLine.containsKey(line)) {
          commentsByLine.put(line, new StringBuilder());
        }
        commentsByLine.get(line).append(markDownUtils.inlineIssue(severity, message, ruleKey)).append("\n");
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
