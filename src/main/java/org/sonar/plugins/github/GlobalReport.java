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

import com.google.common.annotations.VisibleForTesting;
import javax.annotation.Nullable;
import org.kohsuke.github.GHCommitState;
import org.sonar.api.issue.Issue;
import org.sonar.api.rule.Severity;

public class GlobalReport {
  private final MarkDownUtils markDownUtils;
  private final boolean tryReportIssuesInline;
  private int[] newIssuesBySeverity = new int[Severity.ALL.size()];
  private StringBuilder notReportedOnDiff = new StringBuilder();
  private int notReportedIssueCount = 0;
  private int maxGlobalReportedIssues;

  public GlobalReport(MarkDownUtils markDownUtils, boolean tryReportIssuesInline) {
    this(markDownUtils, tryReportIssuesInline, GitHubPluginConfiguration.MAX_GLOBAL_ISSUES);
  }

  @VisibleForTesting
  public GlobalReport(MarkDownUtils markDownUtils, boolean tryReportIssuesInline, int maxGlobalReportedIssues) {
    this.markDownUtils = markDownUtils;
    this.tryReportIssuesInline = tryReportIssuesInline;
    this.maxGlobalReportedIssues = maxGlobalReportedIssues;
  }

  private void increment(String severity) {
    this.newIssuesBySeverity[Severity.ALL.indexOf(severity)]++;
  }

  public String formatForMarkdown() {
    StringBuilder sb = new StringBuilder();
    sb.append("#### Analysis summary\n");
    sb.append("SonarQube analysis reported ");
    int newIssues = newIssues(Severity.BLOCKER) + newIssues(Severity.CRITICAL) + newIssues(Severity.MAJOR) + newIssues(Severity.MINOR) + newIssues(Severity.INFO);
    if (newIssues > 0) {
      sb.append(newIssues).append(" issue" + (newIssues > 1 ? "s" : "")).append(":\n");
      if (newIssues > notReportedIssueCount || notReportedIssueCount > maxGlobalReportedIssues) {
        printSummaryBySeverityMarkdown(sb);
      }
      if (tryReportIssuesInline) {
        sb.append("\nWatch the comments in this conversation to review them.\n");
      }
    } else {
      sb.append("no issues.");
    }

    if (notReportedIssueCount > 0) {
      if (tryReportIssuesInline) {
        if (notReportedIssueCount <= maxGlobalReportedIssues) {
          sb.append("\n#### ").append(notReportedIssueCount);
        } else {
          sb.append("\n#### Top ").append(maxGlobalReportedIssues);
        }
        sb.append(" unreported issues\n");
        sb.append("\nNote: the following issues could not be reported as comments because they are located on lines that are not displayed in this pull request:\n");
      } else if (notReportedIssueCount > maxGlobalReportedIssues) {
        sb.append("\n#### Top ").append(maxGlobalReportedIssues).append(" issues\n");
      }
      // Need to add an extra line break for ordered list to be displayed properly
      sb.append('\n')
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

  private void printSummaryBySeverityMarkdown(StringBuilder sb) {
    printNewIssuesForMarkdown(sb, Severity.BLOCKER);
    printNewIssuesForMarkdown(sb, Severity.CRITICAL);
    printNewIssuesForMarkdown(sb, Severity.MAJOR);
    printNewIssuesForMarkdown(sb, Severity.MINOR);
    printNewIssuesForMarkdown(sb, Severity.INFO);
  }

  private void printNewIssuesInline(StringBuilder sb) {
    sb.append("SonarQube reported ");
    int newIssues = newIssues(Severity.BLOCKER) + newIssues(Severity.CRITICAL) + newIssues(Severity.MAJOR) + newIssues(Severity.MINOR) + newIssues(Severity.INFO);
    if (newIssues > 0) {
      sb.append(newIssues).append(" issue" + (newIssues > 1 ? "s" : "")).append(",");
      int newCriticalOrBlockerIssues = newIssues(Severity.BLOCKER) + newIssues(Severity.CRITICAL);
      if (newCriticalOrBlockerIssues > 0) {
        printNewIssuesInline(sb, Severity.CRITICAL);
        printNewIssuesInline(sb, Severity.BLOCKER);
      } else {
        sb.append(" no critical nor blocker");
      }
    } else {
      sb.append("no issues");
    }
  }

  private void printNewIssuesInline(StringBuilder sb, String severity) {
    int issueCount = newIssues(severity);
    if (issueCount > 0) {
      if (sb.charAt(sb.length() - 1) == ',') {
        sb.append(" with ");
      } else {
        sb.append(" and ");
      }
      sb.append(issueCount).append(" ").append(severity.toLowerCase());
    }
  }

  private void printNewIssuesForMarkdown(StringBuilder sb, String severity) {
    int issueCount = newIssues(severity);
    if (issueCount > 0) {
      sb.append("* ").append(MarkDownUtils.getImageMarkdownForSeverity(severity)).append(" ").append(issueCount).append(" ").append(severity.toLowerCase()).append("\n");
    }
  }

  public void process(Issue issue, @Nullable String githubUrl, boolean reportedOnDiff) {
    increment(issue.severity());
    if (!reportedOnDiff) {
      if (notReportedIssueCount < maxGlobalReportedIssues) {
        notReportedOnDiff
          .append("1. ")
          .append(markDownUtils.globalIssue(issue.severity(), issue.message(), issue.ruleKey().toString(), githubUrl, issue.componentKey()))
          .append("\n");
      }
      notReportedIssueCount++;
    }
  }

  public boolean hasNewIssue() {
    return newIssues(Severity.BLOCKER) + newIssues(Severity.CRITICAL) + newIssues(Severity.MAJOR) + newIssues(Severity.MINOR) + newIssues(Severity.INFO) > 0;
  }
}
