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
package org.sonar.github;

import com.google.common.collect.Maps;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.kohsuke.github.*;

import java.io.*;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SQPullRequestReporter {

  public static void main(String[] args) throws Exception {
    String repository = args[0];
    String sqJsonReportPath = args[1];
    int pullRequestNumber = Integer.parseInt(args[2]);

    perform(repository, sqJsonReportPath, pullRequestNumber);
  }

  private static void perform(String repository, String sqJsonReportPath, int pullRequestNumber) throws IOException, FileNotFoundException {
    GitHub github = GitHubBuilder.fromEnvironment().build();
    GHRepository ghRepo = github.getRepository(repository);

    GHPullRequest pr = ghRepo.getPullRequest(pullRequestNumber);

    Set<Integer> reviewCommentIdsToBeDeleted = new HashSet<>();
    Map<String, Map<Integer, GHPullRequestReviewComment>> existingReviewCommentsByLocationByFile = collectExistingReviewComments(github.getMyself().getLogin(), pr,
      reviewCommentIdsToBeDeleted);

    Map<String, Map<Integer, Integer>> patchPositionMappingByFile = mapPatchPositionsToLines(pr);

    GlobalReport report = new GlobalReport();
    Map<String, Map<Integer, StringBuilder>> commentsToBeAddedByPosition = parseSqJsonReport(patchPositionMappingByFile, sqJsonReportPath, report);

    updateReviewComments(pr, existingReviewCommentsByLocationByFile, reviewCommentIdsToBeDeleted, commentsToBeAddedByPosition);

    pr.comment(report.formatForMarkdown());

    ghRepo.createCommitStatus(pr.getHead().getSha(), report.getStatus(), null, report.getStatusDescription(), "sonarqube");
  }

  private static Map<String, Map<Integer, GHPullRequestReviewComment>> collectExistingReviewComments(String githubAccount, GHPullRequest pr,
    Set<Integer> reviewCommentIdsToBeDeleted)
    throws IOException {
    Map<String, Map<Integer, GHPullRequestReviewComment>> existingReviewCommentsByLocationByFile = new HashMap<String, Map<Integer, GHPullRequestReviewComment>>();
    for (GHPullRequestReviewComment comment : pr.listReviewComments()) {
      if (!githubAccount.equals(comment.getUser().getLogin())) {
        // Ignore comments from other users
        continue;
      }
      if (!existingReviewCommentsByLocationByFile.containsKey(comment.getPath())) {
        existingReviewCommentsByLocationByFile.put(comment.getPath(), new HashMap<Integer, GHPullRequestReviewComment>());
      }
      reviewCommentIdsToBeDeleted.add(comment.getId());
      existingReviewCommentsByLocationByFile.get(comment.getPath()).put(comment.getPosition(), comment);
    }
    return existingReviewCommentsByLocationByFile;
  }

  private static void updateReviewComments(GHPullRequest pr, Map<String, Map<Integer, GHPullRequestReviewComment>> existingReviewCommentsByLocationByFile,
    Set<Integer> reviewCommentIdsToBeDeleted, Map<String, Map<Integer, StringBuilder>> commentsToBeAddedByPosition) throws IOException {
    for (String path : commentsToBeAddedByPosition.keySet()) {
      for (Integer position : commentsToBeAddedByPosition.get(path).keySet()) {
        String body = commentsToBeAddedByPosition.get(path).get(position).toString();
        if (existingReviewCommentsByLocationByFile.containsKey(path) && existingReviewCommentsByLocationByFile.get(path).containsKey(position)) {
          GHPullRequestReviewComment existingReview = existingReviewCommentsByLocationByFile.get(path).get(position);
          if (!existingReview.getBody().equals(body)) {
            pr.updateReviewComment(existingReview.getId(), body);
          }
          reviewCommentIdsToBeDeleted.remove(existingReview.getId());
        } else {
          pr.createReviewComment(body, pr.getHead().getSha(), path, position);
        }
      }
    }

    for (Integer idReviewToDelete : reviewCommentIdsToBeDeleted) {
      pr.deleteReviewComment(idReviewToDelete);
    }
  }

  private static class GlobalReport {
    private int newBlocker = 0;
    private int newCritical = 0;
    private int newMajor = 0;
    private int newMinor = 0;
    private int newInfo = 0;

    public String formatForMarkdown() {
      StringBuilder sb = new StringBuilder();
      printNewIssuesMarkdown(sb);
      return sb.toString();
    }

    public String getStatusDescription() {
      StringBuilder sb = new StringBuilder();
      printNewIssuesInline(sb);
      return sb.toString();
    }

    public GHCommitState getStatus() {
      return (newBlocker > 0 || newCritical > 0) ? GHCommitState.ERROR : GHCommitState.SUCCESS;
    }

    private void printNewIssuesMarkdown(StringBuilder sb) {
      sb.append("SonarQube analysis reported ");
      int newIssues = newBlocker + newCritical + newMajor + newMinor + newInfo;
      if (newIssues > 0) {
        sb.append(newIssues).append(" new issue" + (newIssues > 1 ? "s" : "")).append(":\n");
        printNewIssuesForMarkdown(sb, newBlocker, "blocking");
        printNewIssuesForMarkdown(sb, newCritical, "critical");
        printNewIssuesForMarkdown(sb, newMajor, "major");
        printNewIssuesForMarkdown(sb, newMinor, "minor");
        printNewIssuesForMarkdown(sb, newInfo, "info");
      } else {
        sb.append("no new issue.");
      }
    }

    private void printNewIssuesInline(StringBuilder sb) {
      int newIssues = newBlocker + newCritical + newMajor + newMinor + newInfo;
      if (newIssues > 0) {
        sb.append("+").append(newIssues).append(" issue" + (newIssues > 1 ? "s" : "")).append(" (");
        printNewIssuesInline(sb, newBlocker, "blocking");
        printNewIssuesInline(sb, newCritical, "critical");
        printNewIssuesInline(sb, newMajor, "major");
        printNewIssuesInline(sb, newMinor, "minor");
        printNewIssuesInline(sb, newInfo, "info");
        sb.append(")");
      } else {
        sb.append("No new issue");
      }
    }

    private void printNewIssuesInline(StringBuilder sb, int issueCount, String severityLabel) {
      if (issueCount > 0) {
        if (sb.charAt(sb.length() - 1) != '(') {
          sb.append(" ");
        }
        sb.append("+").append(issueCount).append(" ").append(severityLabel);
      }
    }

    private void printNewIssuesForMarkdown(StringBuilder sb, int issueCount, String severityLabel) {
      if (issueCount > 0) {
        sb.append("* ").append(issueCount).append(" ").append(severityLabel).append("\n");
      }
    }
  }

  private static Map<String, Map<Integer, StringBuilder>> parseSqJsonReport(Map<String, Map<Integer, Integer>> patchPositionMappingByFile, String sqJsonReportPath,
    GlobalReport report)
    throws IOException,
    FileNotFoundException {
    Map<String, Map<Integer, StringBuilder>> commentToBeAddedByPosition = new HashMap<String, Map<Integer, StringBuilder>>();
    try (FileReader fileReader = new FileReader(sqJsonReportPath)) {
      Object obj = JSONValue.parse(fileReader);
      JSONObject sonarResult = (JSONObject) obj;
      // Start by resolving all components path in a cache
      Map<String, String> pathsByKey = Maps.newHashMap();
      final JSONArray components = (JSONArray) sonarResult.get("components");
      for (Object component : components) {
        String key = ObjectUtils.toString(((JSONObject) component).get("key"));
        String path = ObjectUtils.toString(((JSONObject) component).get("path"));
        String moduleKey = ObjectUtils.toString(((JSONObject) component).get("moduleKey"));
        String fullpath = "";
        if (StringUtils.isNotBlank(moduleKey)) {
          fullpath = pathsByKey.get(moduleKey);
        }
        fullpath += (StringUtils.isNotBlank(fullpath) ? "/" : "") + path;
        pathsByKey.put(key, fullpath);
      }

      JSONArray issues = (JSONArray) sonarResult.get("issues");
      for (Object issueObj : issues) {
        JSONObject jsonIssue = (JSONObject) issueObj;
        String componentKey = ObjectUtils.toString(jsonIssue.get("component"));
        String severity = ObjectUtils.toString(jsonIssue.get("severity"));
        boolean isNew = Boolean.TRUE.equals(jsonIssue.get("isNew"));
        if (isNew) {
          switch (severity.toLowerCase()) {
            case "blocker":
              report.newBlocker++;
              break;
            case "critical":
              report.newCritical++;
              break;
            case "major":
              report.newMajor++;
              break;
            case "minor":
              report.newMinor++;
              break;
            case "info":
              report.newInfo++;
              break;
          }
        }
        if (pathsByKey.containsKey(componentKey)) {
          String fullpath = pathsByKey.get(componentKey);
          if (!patchPositionMappingByFile.containsKey(fullpath)) {
            continue;
          }
          Long issueLine = (Long) jsonIssue.get("line");
          if (issueLine == null) {
            continue;
          }
          Integer position = patchPositionMappingByFile.get(fullpath).get(issueLine.intValue());
          if (position == null) {
            continue;
          }
          String message = ObjectUtils.toString(jsonIssue.get("message"));
          String ruleKey = ObjectUtils.toString(jsonIssue.get("rule"));
          if (!commentToBeAddedByPosition.containsKey(fullpath)) {
            commentToBeAddedByPosition.put(fullpath, new HashMap<Integer, StringBuilder>());
          }
          if (!commentToBeAddedByPosition.get(fullpath).containsKey(position)) {
            commentToBeAddedByPosition.get(fullpath).put(position, new StringBuilder());
          }
          commentToBeAddedByPosition.get(fullpath).get(position).append(formatMessage(severity, message, ruleKey, isNew)).append("\n");
        }
      }
    }
    return commentToBeAddedByPosition;
  }

  /**
   * GitHub expect review comments to be added on "patch lines" (aka position) but not on file lines. So we have to iterate over each patch and compute corresponding file line in order to later map issues to the correct position.
   * @return Map File path -> Line -> Position
   */
  private static Map<String, Map<Integer, Integer>> mapPatchPositionsToLines(GHPullRequest pr) throws IOException {
    Map<String, Map<Integer, Integer>> patchPositionMappingByFile = new HashMap<String, Map<Integer, Integer>>();
    for (GHPullRequestFileDetail file : pr.listFiles()) {
      Map<Integer, Integer> patchLocationMapping = new HashMap<>();
      patchPositionMappingByFile.put(file.getFilename(), patchLocationMapping);
      String patch = file.getPatch();
      if (patch == null) {
        continue;
      }
      int currentLine = -1;
      int patchLocation = 0;
      for (String line : IOUtils.readLines(new StringReader(patch))) {
        if (line.startsWith("@")) {
          Matcher matcher = Pattern.compile("@@\\p{IsWhite_Space}-[0-9]+,[0-9]+\\p{IsWhite_Space}\\+([0-9]+),[0-9]+\\p{IsWhite_Space}@@.*").matcher(line);
          if (!matcher.matches()) {
            throw new IllegalStateException("Unable to parse patch line " + line);
          }
          currentLine = Integer.parseInt(matcher.group(1));
        } else if (line.startsWith("-")) {
          // Skip
        } else {
          patchLocationMapping.put(currentLine, patchLocation);
          currentLine++;
        }
        patchLocation++;
      }
    }
    return patchPositionMappingByFile;
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
}
