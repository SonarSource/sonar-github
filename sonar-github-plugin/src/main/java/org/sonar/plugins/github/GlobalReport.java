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

import javax.annotation.Nullable;
import org.kohsuke.github.GHCommitState;
import org.sonar.api.issue.Issue;
import org.sonar.api.rule.Severity;

public class GlobalReport {
  private int[] newIssuesBySeverity = new int[Severity.ALL.size()];
  private StringBuilder notReportedOnDiff = new StringBuilder();

  private void increment(String severity) {
    this.newIssuesBySeverity[Severity.ALL.indexOf(severity)]++;
  }

  public String formatForMarkdown() {
    StringBuilder sb = new StringBuilder();
    printNewIssuesMarkdown(sb);
    if (hasNewIssue()) {
      sb.append("\nWatch the comments in this conversation to review them.");
    }
    if (notReportedOnDiff.length() > 0) {
      sb.append("\nNote: the following issues could not be reported as comments because they are located on lines that are not part of this pull request:\n")
        .append(notReportedOnDiff.toString());
    }
    return sb.toString();
  }

  public String getStatusDescription() {
    StringBuilder sb = new StringBuilder();
    printNewIssuesInline(sb);
    return sb.toString();
  }

  public GHCommitState getStatus() {
    return (newIssues(Severity.BLOCKER) > 0 || newIssues(Severity.CRITICAL) > 0) ? GHCommitState.ERROR : GHCommitState.SUCCESS;
  }

  private int newIssues(String s) {
    return newIssuesBySeverity[Severity.ALL.indexOf(s)];
  }

  private void printNewIssuesMarkdown(StringBuilder sb) {
    sb.append("SonarQube analysis reported ");
    int newIssues = newIssues(Severity.BLOCKER) + newIssues(Severity.CRITICAL) + newIssues(Severity.MAJOR) + newIssues(Severity.MINOR) + newIssues(Severity.INFO);
    if (newIssues > 0) {
      sb.append(newIssues).append(" issue" + (newIssues > 1 ? "s" : "")).append(":\n");
      printNewIssuesForMarkdown(sb, newIssues(Severity.BLOCKER), "blocker");
      printNewIssuesForMarkdown(sb, newIssues(Severity.CRITICAL), "critical");
      printNewIssuesForMarkdown(sb, newIssues(Severity.MAJOR), "major");
      printNewIssuesForMarkdown(sb, newIssues(Severity.MINOR), "minor");
      printNewIssuesForMarkdown(sb, newIssues(Severity.INFO), "info");
    } else {
      sb.append("no issues.");
    }
  }

  private void printNewIssuesInline(StringBuilder sb) {
    int newIssues = newIssues(Severity.BLOCKER) + newIssues(Severity.CRITICAL) + newIssues(Severity.MAJOR) + newIssues(Severity.MINOR) + newIssues(Severity.INFO);
    if (newIssues > 0) {
      sb.append("+").append(newIssues).append(" issue" + (newIssues > 1 ? "s" : "")).append(" (");
      printNewIssuesInline(sb, newIssues(Severity.BLOCKER), "blocker");
      printNewIssuesInline(sb, newIssues(Severity.CRITICAL), "critical");
      printNewIssuesInline(sb, newIssues(Severity.MAJOR), "major");
      printNewIssuesInline(sb, newIssues(Severity.MINOR), "minor");
      printNewIssuesInline(sb, newIssues(Severity.INFO), "info");
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

  public void process(Issue issue, @Nullable String githubUrl, boolean reportedOnDiff) {
    increment(issue.severity());
    if (!reportedOnDiff) {
      notReportedOnDiff
        .append("* ")
        .append(MarkDownUtils.globalIssue(issue.severity(), issue.message(), issue.ruleKey().toString(), githubUrl, issue.componentKey()))
        .append("\n");
    }
  }

  public boolean hasNewIssue() {
    return newIssues(Severity.BLOCKER) + newIssues(Severity.CRITICAL) + newIssues(Severity.MAJOR) + newIssues(Severity.MINOR) + newIssues(Severity.INFO) > 0;
  }
}
