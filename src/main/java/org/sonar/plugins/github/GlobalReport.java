/*
 * SonarQube :: GitHub Plugin
 * Copyright (C) 2015-2018 SonarSource SA
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

import java.net.URL;
import java.util.Locale;
import javax.annotation.Nullable;
import org.kohsuke.github.GHCommitState;
import org.sonar.api.batch.postjob.issue.PostJobIssue;
import org.sonar.api.batch.rule.Severity;

public class GlobalReport {
  private final boolean tryReportIssuesInline;
  private int[] newIssuesBySeverity = new int[Severity.values().length];
  private int extraIssueCount = 0;
  private int maxGlobalReportedIssues;
  private final ReportBuilder builder;

  public GlobalReport(MarkDownUtils markDownUtils, boolean tryReportIssuesInline) {
    this(markDownUtils, tryReportIssuesInline, GitHubPluginConfiguration.MAX_GLOBAL_ISSUES);
  }

  public GlobalReport(MarkDownUtils markDownUtils, boolean tryReportIssuesInline, int maxGlobalReportedIssues) {
    this.tryReportIssuesInline = tryReportIssuesInline;
    this.maxGlobalReportedIssues = maxGlobalReportedIssues;
    this.builder = new MarkDownReportBuilder(markDownUtils);
  }

  private void increment(Severity severity) {
    this.newIssuesBySeverity[severity.ordinal()]++;
  }

  public String formatForMarkdown() {
    int newIssues = newIssues(Severity.BLOCKER) + newIssues(Severity.CRITICAL) + newIssues(Severity.MAJOR) + newIssues(Severity.MINOR) + newIssues(Severity.INFO);
    if (newIssues == 0) {
      return "SonarQube analysis reported no issues.";
    }

    boolean hasInlineIssues = newIssues > extraIssueCount;
    boolean extraIssuesTruncated = extraIssueCount > maxGlobalReportedIssues;
    builder.append("SonarQube analysis reported ").append(newIssues).append(" issue").append(newIssues > 1 ? "s" : "").append("\n");
    if (hasInlineIssues || extraIssuesTruncated) {
      appendSummaryBySeverity(builder);
    }
    if (tryReportIssuesInline && hasInlineIssues) {
      builder.append("\nWatch the comments in this conversation to review them.\n");
    }

    if (extraIssueCount > 0) {
      appendExtraIssues(builder, hasInlineIssues, extraIssuesTruncated);
    }

    return builder.toString();
  }

  private void appendExtraIssues(ReportBuilder builder, boolean hasInlineIssues, boolean extraIssuesTruncated) {
    if (tryReportIssuesInline) {
      if (hasInlineIssues || extraIssuesTruncated) {
        int extraCount;
        builder.append("\n#### ");
        if (extraIssueCount <= maxGlobalReportedIssues) {
          extraCount = extraIssueCount;
        } else {
          extraCount = maxGlobalReportedIssues;
          builder.append("Top ");
        }
        builder.append(extraCount).append(" extra issue").append(extraCount > 1 ? "s" : "").append("\n");
      }
      builder.append(
        "\nNote: The following issues were found on lines that were not modified in the pull request. "
          + "Because these issues can't be reported as line comments, they are summarized here:\n");
    } else if (extraIssuesTruncated) {
      builder.append("\n#### Top ").append(maxGlobalReportedIssues).append(" issues\n");
    }
    builder.appendExtraIssues();
  }

  public String getStatusDescription() {
    StringBuilder sb = new StringBuilder();
    appendNewIssuesInline(sb);
    return sb.toString();
  }

  public GHCommitState getStatus() {
    return (newIssues(Severity.BLOCKER) > 0 || newIssues(Severity.CRITICAL) > 0) ? GHCommitState.ERROR : GHCommitState.SUCCESS;
  }

  private int newIssues(Severity s) {
    return newIssuesBySeverity[s.ordinal()];
  }

  private void appendSummaryBySeverity(ReportBuilder builder) {
    appendNewIssues(builder, Severity.BLOCKER);
    appendNewIssues(builder, Severity.CRITICAL);
    appendNewIssues(builder, Severity.MAJOR);
    appendNewIssues(builder, Severity.MINOR);
    appendNewIssues(builder, Severity.INFO);
  }

  private void appendNewIssuesInline(StringBuilder sb) {
    sb.append("SonarQube reported ");
    int newIssues = newIssues(Severity.BLOCKER) + newIssues(Severity.CRITICAL) + newIssues(Severity.MAJOR) + newIssues(Severity.MINOR) + newIssues(Severity.INFO);
    if (newIssues > 0) {
      sb.append(newIssues).append(" issue" + (newIssues > 1 ? "s" : "")).append(",");
      int newCriticalOrBlockerIssues = newIssues(Severity.BLOCKER) + newIssues(Severity.CRITICAL);
      if (newCriticalOrBlockerIssues > 0) {
        appendNewIssuesInline(sb, Severity.CRITICAL);
        appendNewIssuesInline(sb, Severity.BLOCKER);
      } else {
        sb.append(" no criticals or blockers");
      }
    } else {
      sb.append("no issues");
    }
  }

  private void appendNewIssuesInline(StringBuilder sb, Severity severity) {
    int issueCount = newIssues(severity);
    if (issueCount > 0) {
      if (sb.charAt(sb.length() - 1) == ',') {
        sb.append(" with ");
      } else {
        sb.append(" and ");
      }
      sb.append(issueCount).append(" ").append(severity.name().toLowerCase(Locale.ENGLISH));
    }
  }

  private void appendNewIssues(ReportBuilder builder, Severity severity) {
    int issueCount = newIssues(severity);
    if (issueCount > 0) {
      builder
        .append("* ").append(severity)
        .append(" ").append(issueCount)
        .append(" ").append(severity.name().toLowerCase(Locale.ENGLISH))
        .append("\n");
    }
  }

  public void process(PostJobIssue issue, @Nullable URL gitHubUrl, boolean reportedOnDiff) {
    increment(issue.severity());
    if (!reportedOnDiff) {
      if (extraIssueCount < maxGlobalReportedIssues) {
        builder.registerExtraIssue(issue, gitHubUrl);
      }
      extraIssueCount++;
    }
  }

  public boolean hasNewIssue() {
    return newIssues(Severity.BLOCKER) + newIssues(Severity.CRITICAL) + newIssues(Severity.MAJOR) + newIssues(Severity.MINOR) + newIssues(Severity.INFO) > 0;
  }
}
