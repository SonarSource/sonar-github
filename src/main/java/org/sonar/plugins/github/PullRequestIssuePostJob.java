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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
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

    pullRequestFacade.addGlobalComment(report.formatForMarkdown());

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
      Integer issueLine = issue.line();
      InputFile inputFile = inputFileCache.byKey(issue.componentKey());
      report.process(issue, pullRequestFacade.getGithubUrl(inputFile, issueLine));
      if (inputFile != null) {
        if (!pullRequestFacade.hasFile(inputFile)) {
          continue;
        }
        if (issueLine == null) {
          // Global issue
          continue;
        }
        int line = issueLine.intValue();
        if (!pullRequestFacade.hasFileLine(inputFile, line)) {
          continue;
        }
        String message = issue.message();
        String ruleKey = issue.ruleKey().toString();
        if (!commentToBeAddedByFileAndByLine.containsKey(inputFile)) {
          commentToBeAddedByFileAndByLine.put(inputFile, new HashMap<Integer, StringBuilder>());
        }
        Map<Integer, StringBuilder> commentsByLine = commentToBeAddedByFileAndByLine.get(inputFile);
        if (!commentsByLine.containsKey(line)) {
          commentsByLine.put(line, new StringBuilder());
        }
        commentsByLine.get(line).append(formatMessage(severity, message, ruleKey, isNew)).append("\n");
      }
    }
    return commentToBeAddedByFileAndByLine;
  }

  private static String formatMessage(String severity, String message, String ruleKey, boolean isNew) {
    String ruleLink = getRuleLink(ruleKey);
    StringBuilder sb = new StringBuilder();
    if (isNew) {
      sb.append(("![New](" + IMAGES_ROOT_URL + "new.png) "));
    }
    sb.append(getImageMarkdownForSeverity(severity))
      .append(" ")
      .append(message)
      .append(" ")
      .append(ruleLink);
    return sb.toString();
  }

  static String getRuleLink(String ruleKey) {
    return "[![rule](" + IMAGES_ROOT_URL + "rule.png)](http://nemo.sonarqube.org/coding_rules#rule_key=" + encodeForUrl(ruleKey) + ")";
  }

  static String encodeForUrl(String url) {
    try {
      return URLEncoder.encode(url, "UTF-8");

    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("Encoding not supported", e);
    }
  }

  private static String getImageMarkdownForSeverity(String severity) {
    return "![" + severity + "](" + IMAGES_ROOT_URL + "severity-" + severity.toLowerCase() + ".png)";
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
