/*
 * SonarQube :: GitHub Plugin
 * Copyright (C) 2015-2017 SonarSource SA
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.apache.commons.lang.StringUtils;
import org.sonar.api.batch.postjob.issue.PostJobIssue;
import org.sonar.api.batch.rule.Severity;

public class MarkDownReportBuilder implements ReportBuilder {

  private final MarkDownUtils markDownUtils;
  private final StringBuilder sb = new StringBuilder();

  // note: ordered implementation for consistent user experience and testability
  private final Set<String> links = new TreeSet<>();

  private final List<IssueHolder> extraIssues = new ArrayList<>();

  private static class IssueHolder {
    private final PostJobIssue issue;
    private final String gitHubUrl;

    private IssueHolder(PostJobIssue issue, String gitHubUrl) {
      this.issue = issue;
      this.gitHubUrl = gitHubUrl;
    }
  }

  MarkDownReportBuilder(MarkDownUtils markDownUtils) {
    this.markDownUtils = markDownUtils;
  }

  @Override
  public ReportBuilder appendProjectId(String projectId) {
    if (!StringUtils.isEmpty(projectId)) {
      sb.append(MarkDownUtils.projectId(projectId));
    }

    return this;
  }

  @Override
  public ReportBuilder append(Object o) {
    sb.append(o);
    return this;
  }

  @Override
  public ReportBuilder append(Severity severity) {
    links.add(formatImageLinkDefinition(severity));
    sb.append(formatImageLinkReference(severity));
    return this;
  }

  private static String formatImageLinkDefinition(Severity severity) {
    return String.format("[%s]: %s 'Severity: %s'", severity.name(), MarkDownUtils.getImageUrl(severity), severity.name());
  }

  private static String formatImageLinkReference(Severity severity) {
    return String.format("![%s][%s]", severity.name(), severity.name());
  }

  @Override
  public ReportBuilder registerExtraIssue(PostJobIssue issue, String gitHubUrl) {
    extraIssues.add(new IssueHolder(issue, gitHubUrl));
    return this;
  }

  @Override
  public ReportBuilder appendExtraIssues() {
    // need a blank line before lists to be displayed correctly
    sb.append("\n");
    for (IssueHolder holder : extraIssues) {
      PostJobIssue issue = holder.issue;
      links.add(formatImageLinkDefinition(issue.severity()));
      String image = formatImageLinkReference(issue.severity());
      String text = markDownUtils.globalIssue(issue.message(), issue.ruleKey().toString(), holder.gitHubUrl, issue.componentKey());
      sb.append("1. ").append(image).append(" ").append(text).append("\n");
    }
    return this;
  }

  @Override
  public String toString() {
    StringBuilder copy = new StringBuilder(sb);
    for (String link : links) {
      copy.append("\n").append(link);
    }
    return copy.toString();
  }
}
