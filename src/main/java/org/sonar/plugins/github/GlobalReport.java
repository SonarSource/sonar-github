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

import org.kohsuke.github.GHCommitState;

public class GlobalReport {
  private int newBlocker = 0;
  private int newCritical = 0;
  private int newMajor = 0;
  private int newMinor = 0;
  private int newInfo = 0;

  public void incrementNewBlocker() {
    this.newBlocker++;
  }

  public void incrementNewCritical() {
    this.newCritical++;
  }

  public void incrementNewMajor() {
    this.newMajor++;
  }

  public void incrementNewMinor() {
    this.newMinor++;
  }

  public void incrementNewInfo() {
    this.newInfo++;
  }

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
      sb.append("no new issues.");
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
