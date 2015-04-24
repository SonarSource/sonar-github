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

import org.kohsuke.github.GHCommitState;
import org.sonar.api.batch.postjob.issue.Issue;
import org.sonar.api.batch.rule.Severity;

public class GlobalReport {
  private int[] newIssuesBySeverity = new int[Severity.values().length];
  private StringBuilder details = new StringBuilder();

  private void increment(Severity severity) {
    this.newIssuesBySeverity[severity.ordinal()]++;
  }

  public String formatForMarkdown() {
    StringBuilder sb = new StringBuilder();
    printNewIssuesMarkdown(sb);
    sb.append("\n").append(details.toString());
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

  private int newIssues(Severity s) {
    return newIssuesBySeverity[s.ordinal()];
  }

  private void printNewIssuesMarkdown(StringBuilder sb) {
    sb.append("SonarQube analysis reported ");
    int newIssues = newIssues(Severity.BLOCKER) + newIssues(Severity.CRITICAL) + newIssues(Severity.MAJOR) + newIssues(Severity.MINOR) + newIssues(Severity.INFO);
    if (newIssues > 0) {
      sb.append(newIssues).append(" new issue" + (newIssues > 1 ? "s" : "")).append(":\n");
      printNewIssuesForMarkdown(sb, newIssues(Severity.BLOCKER), "blocking");
      printNewIssuesForMarkdown(sb, newIssues(Severity.CRITICAL), "critical");
      printNewIssuesForMarkdown(sb, newIssues(Severity.MAJOR), "major");
      printNewIssuesForMarkdown(sb, newIssues(Severity.MINOR), "minor");
      printNewIssuesForMarkdown(sb, newIssues(Severity.INFO), "info");
    } else {
      sb.append("no new issues.");
    }
  }

  private void printNewIssuesInline(StringBuilder sb) {
    int newIssues = newIssues(Severity.BLOCKER) + newIssues(Severity.CRITICAL) + newIssues(Severity.MAJOR) + newIssues(Severity.MINOR) + newIssues(Severity.INFO);
    if (newIssues > 0) {
      sb.append("+").append(newIssues).append(" issue" + (newIssues > 1 ? "s" : "")).append(" (");
      printNewIssuesInline(sb, newIssues(Severity.BLOCKER), "blocking");
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

  public void process(Issue issue) {
    if (!issue.isNew()) {
      return;
    }
    increment(issue.severity());
    details.append(" * ").append(issue.componentKey());
    Integer line = issue.line();
    if (line != null) {
      details.append(" (L").append(line).append(")");
    }
    details.append(" ").append(issue.message()).append(" (").append(issue.ruleKey()).append(")").append("\n");
  }
}
