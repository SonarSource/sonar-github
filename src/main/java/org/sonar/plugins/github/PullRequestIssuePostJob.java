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

import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.InputPath;
import org.sonar.api.batch.postjob.PostJob;
import org.sonar.api.batch.postjob.PostJobContext;
import org.sonar.api.batch.postjob.PostJobDescriptor;
import org.sonar.api.batch.postjob.issue.Issue;
import org.sonar.api.batch.rule.Severity;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/**
 * Compute comments to be added on the pull request.
 */
public class PullRequestIssuePostJob implements PostJob {

  private final PullRequestFacade pullRequestFacade;

  public PullRequestIssuePostJob(PullRequestFacade pullRequestFacade) {
    this.pullRequestFacade = pullRequestFacade;
  }

  @Override
  public void describe(PostJobDescriptor descriptor) {
    descriptor.name("GitHub Pull Request Issue Publisher")
      .requireProperty(GitHubPlugin.GITHUB_PULL_REQUEST);
  }

  @Override
  public void execute(PostJobContext context) {
    GlobalReport report = new GlobalReport();
    Map<InputFile, Map<Integer, StringBuilder>> commentsToBeAddedByLine = processIssues(context, report);

    updateReviewComments(commentsToBeAddedByLine);

    pullRequestFacade.deleteOutdatedComments();

    pullRequestFacade.addGlobalComment(report.formatForMarkdown());

    pullRequestFacade.createOrUpdateSonarQubeStatus(report.getStatus(), report.getStatusDescription());

  }

  private Map<InputFile, Map<Integer, StringBuilder>> processIssues(PostJobContext context, GlobalReport report) {
    Map<InputFile, Map<Integer, StringBuilder>> commentToBeAddedByFileAndByLine = new HashMap<>();
    for (Issue issue : context.issues()) {
      Severity severity = issue.severity();
      boolean isNew = issue.isNew();
      report.process(issue);
      InputPath inputPath = issue.inputPath();
      if (inputPath instanceof InputFile) {
        InputFile inputFile = (InputFile) inputPath;
        if (!pullRequestFacade.hasFile(inputFile)) {
          continue;
        }
        Integer issueLine = issue.line();
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

  private static String formatMessage(Severity severity, String message, String ruleKey, boolean isNew) {

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

  private static String getImageMarkdownForSeverity(Severity severity) {
    return "![" + severity + "](https://raw.githubusercontent.com/henryju/image-hosting/master/"
      + severity.name().toLowerCase() + ".png)";
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
