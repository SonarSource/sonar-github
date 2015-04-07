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

import org.sonar.api.batch.PostJob;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.issue.Issue;
import org.sonar.api.issue.ProjectIssues;
import org.sonar.api.resources.Project;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

public class PullRequestIssuePostJob implements PostJob {

  private final ProjectIssues issues;
  private final GitHubPluginConfiguration gitHubPluginConfiguration;
  private final PullRequestFacade pullRequestFacade;
  private final FileCache fileCache;

  public PullRequestIssuePostJob(ProjectIssues issues, GitHubPluginConfiguration gitHubPluginConfiguration, PullRequestFacade pullRequestFacade, FileCache fileCache) {
    this.issues = issues;
    this.gitHubPluginConfiguration = gitHubPluginConfiguration;
    this.pullRequestFacade = pullRequestFacade;
    this.fileCache = fileCache;
  }

  @Override
  public void executeOn(Project project, SensorContext context) {
    int pullRequestNumber = gitHubPluginConfiguration.pullRequestNumber();
    if (pullRequestNumber == 0) {
      return;
    }

    GlobalReport report = new GlobalReport();
    Map<String, Map<Integer, StringBuilder>> commentsToBeAddedByLine = processIssues(report);

    updateReviewComments(commentsToBeAddedByLine);

    pullRequestFacade.deleteOutdatedComments();

    pullRequestFacade.addGlobalComment(report.formatForMarkdown());

    pullRequestFacade.createOrUpdateSonarQubeStatus(report.getStatus(), report.getStatusDescription());

  }

  private Map<String, Map<Integer, StringBuilder>> processIssues(GlobalReport report) {
    Map<String, Map<Integer, StringBuilder>> commentToBeAddedByFileAndByLine = new HashMap<String, Map<Integer, StringBuilder>>();
    for (Issue issue : issues.issues()) {
      String componentKey = issue.componentKey();
      String severity = issue.severity();
      boolean isNew = issue.isNew();
      if (isNew) {
        switch (severity.toLowerCase()) {
          case "blocker":
            report.incrementNewBlocker();
            break;
          case "critical":
            report.incrementNewCritical();
            break;
          case "major":
            report.incrementNewMajor();
            break;
          case "minor":
            report.incrementNewMinor();
            break;
          case "info":
            report.incrementNewInfo();
            break;
        }
      }
      String fullPath = fileCache.getPathFromProjectBaseDir(componentKey);
      if (fullPath != null) {
        if (!pullRequestFacade.hasFile(fullPath)) {
          continue;
        }
        Integer issueLine = issue.line();
        if (issueLine == null) {
          // Global issue
          continue;
        }
        int line = issueLine.intValue();
        if (!pullRequestFacade.hasFileLine(fullPath, line)) {
          continue;
        }
        String message = issue.message();
        String ruleKey = issue.ruleKey().toString();
        if (!commentToBeAddedByFileAndByLine.containsKey(fullPath)) {
          commentToBeAddedByFileAndByLine.put(fullPath, new HashMap<Integer, StringBuilder>());
        }
        if (!commentToBeAddedByFileAndByLine.get(fullPath).containsKey(line)) {
          commentToBeAddedByFileAndByLine.get(fullPath).put(line, new StringBuilder());
        }
        commentToBeAddedByFileAndByLine.get(fullPath).get(line).append(formatMessage(severity, message, ruleKey, isNew)).append("\n");
      }
    }
    return commentToBeAddedByFileAndByLine;
  }

  private static String formatMessage(String severity, String message, String ruleKey, boolean isNew) {

    return (isNew ? ":new: " : "") + getImageMarkdownForSeverity(severity) + message
      + "[![...](https://raw.githubusercontent.com/henryju/image-hosting/master/more.png)](http://nemo.sonarqube.org/coding_rules#rule_key=" + encodeForUrl(ruleKey) + ")";
  }

  public static String encodeForUrl(String url) {
    try {
      return URLEncoder.encode(url, "UTF-8");

    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("Encoding not supported", e);
    }
  }

  private static String getImageMarkdownForSeverity(String severity) {
    return "![" + severity + "](https://raw.githubusercontent.com/henryju/image-hosting/master/"
      + severity.toLowerCase() + ".png)";
  }

  private void updateReviewComments(Map<String, Map<Integer, StringBuilder>> commentsToBeAddedByLine) {
    for (String fullpath : commentsToBeAddedByLine.keySet()) {
      for (Integer line : commentsToBeAddedByLine.get(fullpath).keySet()) {
        String body = commentsToBeAddedByLine.get(fullpath).get(line).toString();
        pullRequestFacade.createOrUpdateReviewComment(fullpath, line, body);
      }
    }
  }

}
