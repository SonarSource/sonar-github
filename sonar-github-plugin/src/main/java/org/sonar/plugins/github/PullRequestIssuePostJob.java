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

import java.util.HashMap;
import java.util.Map;
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

  private static final String IMAGES_ROOT_URL = "https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/";
  private final PullRequestFacade pullRequestFacade;
  private final ProjectIssues projectIssues;
  private final GitHubPluginConfiguration gitHubPluginConfiguration;
  private final InputFileCache inputFileCache;

  public PullRequestIssuePostJob(GitHubPluginConfiguration gitHubPluginConfiguration, PullRequestFacade pullRequestFacade, ProjectIssues projectIssues,
    InputFileCache inputFileCache) {
    this.gitHubPluginConfiguration = gitHubPluginConfiguration;
    this.pullRequestFacade = pullRequestFacade;
    this.projectIssues = projectIssues;
    this.inputFileCache = inputFileCache;
  }

  @Override
  public boolean shouldExecuteOnProject(Project project) {
    return gitHubPluginConfiguration.isEnabled();
  }

  @Override
  public void executeOn(Project project, SensorContext context) {
    GlobalReport report = new GlobalReport();
    Map<InputFile, Map<Integer, StringBuilder>> commentsToBeAddedByLine = processIssues(context, report);

    updateReviewComments(commentsToBeAddedByLine);

    pullRequestFacade.deleteOutdatedComments();

    pullRequestFacade.removePreviousGlobalComments();
    if (report.hasNewIssue()) {
      pullRequestFacade.addGlobalComment(report.formatForMarkdown());
    }

    pullRequestFacade.createOrUpdateSonarQubeStatus(report.getStatus(), report.getStatusDescription());
  }

  @Override
  public String toString() {
    return "GitHub Pull Request Issue Publisher";
  }

  private Map<InputFile, Map<Integer, StringBuilder>> processIssues(SensorContext context, GlobalReport report) {
    Map<InputFile, Map<Integer, StringBuilder>> commentToBeAddedByFileAndByLine = new HashMap<>();
    for (Issue issue : projectIssues.issues()) {
      String severity = issue.severity();
      boolean isNew = issue.isNew();
      if (!isNew) {
        continue;
      }
      Integer issueLine = issue.line();
      InputFile inputFile = inputFileCache.byKey(issue.componentKey());
      boolean reportedInline = false;
      if (inputFile != null) {
        if (pullRequestFacade.hasFile(inputFile) && issueLine != null) {
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
            commentsByLine.get(line).append(MarkDownUtils.inlineIssue(severity, message, ruleKey)).append("\n");
            reportedInline = true;
          }
        }
      }
      report.process(issue, pullRequestFacade.getGithubUrl(inputFile, issueLine), reportedInline);

    }
    return commentToBeAddedByFileAndByLine;
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
