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

import java.net.MalformedURLException;
import java.net.URL;
import org.junit.Test;
import org.sonar.api.batch.postjob.issue.PostJobIssue;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.rule.RuleKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MarkDownReportBuilderTest {

  @Test
  public void test_empty_report() {
    ReportBuilder builder = new MarkDownReportBuilder(mock(MarkDownUtils.class));
    assertThat(builder.toString()).isEmpty();
  }

  @Test
  public void should_append_nothing_if_no_references() {
    ReportBuilder builder = new MarkDownReportBuilder(mock(MarkDownUtils.class));
    String someText = "some text";
    builder.append(someText);
    assertThat(builder.toString()).isEqualTo(someText);
  }

  @Test
  public void should_append_severity_using_reference_links() {
    ReportBuilder builder = new MarkDownReportBuilder(mock(MarkDownUtils.class));
    builder.append(Severity.BLOCKER).append(" fix the leak!\n");
    builder.append("Check comments too!\n");
    assertThat(builder.toString()).isEqualTo("![BLOCKER][BLOCKER] fix the leak!\n"
      + "Check comments too!\n"
      + "\n"
      + "[BLOCKER]: https://sonarsource.github.io/sonar-github/severity-blocker.png 'Severity: BLOCKER'");
  }

  @Test
  public void should_append_reference_definition_only_once() {
    ReportBuilder builder = new MarkDownReportBuilder(mock(MarkDownUtils.class));
    builder.append(Severity.BLOCKER).append(" fix the leak!\n");
    builder.append(Severity.BLOCKER).append(" fix the leak!\n");
    builder.append("Check comments too!\n");
    assertThat(builder.toString()).isEqualTo("![BLOCKER][BLOCKER] fix the leak!\n"
      + "![BLOCKER][BLOCKER] fix the leak!\n"
      + "Check comments too!\n"
      + "\n"
      + "[BLOCKER]: https://sonarsource.github.io/sonar-github/severity-blocker.png 'Severity: BLOCKER'");
  }

  @Test
  public void should_append_reference_definition_for_all_known_severity() {
    ReportBuilder builder = new MarkDownReportBuilder(mock(MarkDownUtils.class));
    for (Severity severity : Severity.values()) {
      builder.append(severity).append(" a ").append(severity.name()).append("-level issue\n");
    }
    assertThat(builder.toString()).isEqualTo("![INFO][INFO] a INFO-level issue\n"
      + "![MINOR][MINOR] a MINOR-level issue\n"
      + "![MAJOR][MAJOR] a MAJOR-level issue\n"
      + "![CRITICAL][CRITICAL] a CRITICAL-level issue\n"
      + "![BLOCKER][BLOCKER] a BLOCKER-level issue\n"
      + "\n"
      + "[BLOCKER]: https://sonarsource.github.io/sonar-github/severity-blocker.png 'Severity: BLOCKER'\n"
      + "[CRITICAL]: https://sonarsource.github.io/sonar-github/severity-critical.png 'Severity: CRITICAL'\n"
      + "[INFO]: https://sonarsource.github.io/sonar-github/severity-info.png 'Severity: INFO'\n"
      + "[MAJOR]: https://sonarsource.github.io/sonar-github/severity-major.png 'Severity: MAJOR'\n"
      + "[MINOR]: https://sonarsource.github.io/sonar-github/severity-minor.png 'Severity: MINOR'");
  }

  @Test
  public void should_append_reference_definitions_for_extra_issues_too() throws MalformedURLException {
    ReportBuilder builder = new MarkDownReportBuilder(mock(MarkDownUtils.class));
    builder.append(Severity.BLOCKER).append(" fix the leak!\n");

    PostJobIssue postJobIssue = mock(PostJobIssue.class);
    when(postJobIssue.severity()).thenReturn(Severity.INFO);
    when(postJobIssue.ruleKey()).thenReturn(mock(RuleKey.class));
    builder.registerExtraIssue(postJobIssue, new URL("http://github.com/dummy"));
    builder.appendExtraIssues();

    builder.append("\nCheck comments too!\n");
    assertThat(builder.toString()).isEqualTo("![BLOCKER][BLOCKER] fix the leak!\n"
      + "\n"
      + "1. ![INFO][INFO] null\n"
      + "\n"
      + "Check comments too!\n"
      + "\n"
      + "[BLOCKER]: https://sonarsource.github.io/sonar-github/severity-blocker.png 'Severity: BLOCKER'\n"
      + "[INFO]: https://sonarsource.github.io/sonar-github/severity-info.png 'Severity: INFO'");
  }

  @Test
  public void should_append_reference_definitions_for_extra_issues_only_if_used() throws MalformedURLException {
    ReportBuilder builder = new MarkDownReportBuilder(mock(MarkDownUtils.class));
    builder.append(Severity.BLOCKER).append(" fix the leak!\n");

    PostJobIssue postJobIssue = mock(PostJobIssue.class);
    when(postJobIssue.severity()).thenReturn(Severity.INFO);
    when(postJobIssue.ruleKey()).thenReturn(mock(RuleKey.class));
    builder.registerExtraIssue(postJobIssue, new URL("http://github.com/dummy"));

    builder.append("Check comments too!\n");
    assertThat(builder.toString()).isEqualTo("![BLOCKER][BLOCKER] fix the leak!\n"
      + "Check comments too!\n"
      + "\n"
      + "[BLOCKER]: https://sonarsource.github.io/sonar-github/severity-blocker.png 'Severity: BLOCKER'");
  }
}
