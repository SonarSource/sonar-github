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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import javax.annotation.CheckForNull;
import org.junit.Before;
import org.junit.Test;
import org.sonar.api.CoreProperties;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.postjob.issue.PostJobIssue;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.Settings;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.rule.RuleKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GlobalReportTest {

  private static final URL GITHUB_URL = parse("https://github.com/SonarSource/sonar-github");

  private MapSettings settings;

  @Before
  public void setup() {
    settings = new MapSettings(new PropertyDefinitions(PropertyDefinition.builder(CoreProperties.SERVER_BASE_URL)
      .name("Server base URL")
      .description("HTTP URL of this SonarQube server, such as <i>http://yourhost.yourdomain/sonar</i>. This value is used i.e. to create links in emails.")
      .category(CoreProperties.CATEGORY_GENERAL)
      .defaultValue(CoreProperties.SERVER_BASE_URL_DEFAULT_VALUE)
      .build()));

    settings.setProperty("sonar.host.url", "http://myserver");
  }

  private static URL parse(String url) {
    try {
      return new URL(url);
    } catch (MalformedURLException e) {
      throw new IllegalStateException(e);
    }
  }

  private PostJobIssue newMockedIssue(String componentKey, @CheckForNull DefaultInputFile inputFile, @CheckForNull Integer line, Severity severity, boolean isNew, String message,
    String rule) {
    PostJobIssue issue = mock(PostJobIssue.class);
    when(issue.inputComponent()).thenReturn(inputFile);
    when(issue.componentKey()).thenReturn(componentKey);
    if (line != null) {
      when(issue.line()).thenReturn(line);
    }
    when(issue.ruleKey()).thenReturn(RuleKey.of("repo", rule));
    when(issue.severity()).thenReturn(severity);
    when(issue.isNew()).thenReturn(isNew);
    when(issue.message()).thenReturn(message);

    return issue;
  }

  @Test
  public void noIssues() {
    GlobalReport globalReport = new GlobalReport(new MarkDownUtils(settings), true);

    String desiredMarkdown = "SonarQube analysis reported no issues.";

    String formattedGlobalReport = globalReport.formatForMarkdown();

    assertThat(formattedGlobalReport).isEqualTo(desiredMarkdown);
  }

  @Test
  public void oneIssue() {
    GlobalReport globalReport = new GlobalReport(new MarkDownUtils(settings), true);
    globalReport.process(newMockedIssue("component", null, null, Severity.INFO, true, "Issue", "rule"), GITHUB_URL, true);

    String desiredMarkdown = "SonarQube analysis reported 1 issue\n" +
      "* ![INFO][INFO] 1 info\n" +
      "\nWatch the comments in this conversation to review them.\n" +
      "\n[INFO]: https://sonarsource.github.io/sonar-github/severity-info.png 'Severity: INFO'";

    String formattedGlobalReport = globalReport.formatForMarkdown();

    assertThat(formattedGlobalReport).isEqualTo(desiredMarkdown);
  }

  @Test
  public void oneIssueOnDir() {
    GlobalReport globalReport = new GlobalReport(new MarkDownUtils(settings), true);
    globalReport.process(newMockedIssue("component0", null, null, Severity.INFO, true, "Issue0", "rule0"), null, false);

    String desiredMarkdown = "SonarQube analysis reported 1 issue\n\n" +
      "Note: The following issues were found on lines that were not modified in the pull request. Because these issues can't be reported as line comments, they are summarized here:\n\n"
      +
      "1. ![INFO][INFO] component0: Issue0 [![rule](https://sonarsource.github.io/sonar-github/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule0)\n" +
      "\n[INFO]: https://sonarsource.github.io/sonar-github/severity-info.png 'Severity: INFO'";

    String formattedGlobalReport = globalReport.formatForMarkdown();

    assertThat(formattedGlobalReport).isEqualTo(desiredMarkdown);
  }

  @Test
  public void shouldFormatIssuesForMarkdownNoInline() {
    GlobalReport globalReport = new GlobalReport(new MarkDownUtils(settings), true);
    globalReport.process(newMockedIssue("component", null, null, Severity.INFO, true, "Issue", "rule"), GITHUB_URL, true);
    globalReport.process(newMockedIssue("component", null, null, Severity.MINOR, true, "Issue", "rule"), GITHUB_URL, true);
    globalReport.process(newMockedIssue("component", null, null, Severity.MAJOR, true, "Issue", "rule"), GITHUB_URL, true);
    globalReport.process(newMockedIssue("component", null, null, Severity.CRITICAL, true, "Issue", "rule"), GITHUB_URL, true);
    globalReport.process(newMockedIssue("component", null, null, Severity.BLOCKER, true, "Issue", "rule"), GITHUB_URL, true);

    String desiredMarkdown = "SonarQube analysis reported 5 issues\n" +
      "* ![BLOCKER][BLOCKER] 1 blocker\n" +
      "* ![CRITICAL][CRITICAL] 1 critical\n" +
      "* ![MAJOR][MAJOR] 1 major\n" +
      "* ![MINOR][MINOR] 1 minor\n" +
      "* ![INFO][INFO] 1 info\n" +
      "\nWatch the comments in this conversation to review them.\n"
      + "\n"
      + "[BLOCKER]: https://sonarsource.github.io/sonar-github/severity-blocker.png 'Severity: BLOCKER'\n"
      + "[CRITICAL]: https://sonarsource.github.io/sonar-github/severity-critical.png 'Severity: CRITICAL'\n"
      + "[INFO]: https://sonarsource.github.io/sonar-github/severity-info.png 'Severity: INFO'\n"
      + "[MAJOR]: https://sonarsource.github.io/sonar-github/severity-major.png 'Severity: MAJOR'\n"
      + "[MINOR]: https://sonarsource.github.io/sonar-github/severity-minor.png 'Severity: MINOR'";

    String formattedGlobalReport = globalReport.formatForMarkdown();

    assertThat(formattedGlobalReport).isEqualTo(desiredMarkdown);
  }

  @Test
  public void shouldFormatIssuesForMarkdownMixInlineGlobal() {
    GlobalReport globalReport = new GlobalReport(new MarkDownUtils(settings), true);
    globalReport.process(newMockedIssue("component", null, null, Severity.INFO, true, "Issue 0", "rule0"), GITHUB_URL, true);
    globalReport.process(newMockedIssue("component", null, null, Severity.MINOR, true, "Issue 1", "rule1"), GITHUB_URL, false);
    globalReport.process(newMockedIssue("component", null, null, Severity.MAJOR, true, "Issue 2", "rule2"), GITHUB_URL, true);
    globalReport.process(newMockedIssue("component", null, null, Severity.CRITICAL, true, "Issue 3", "rule3"), GITHUB_URL, false);
    globalReport.process(newMockedIssue("component", null, null, Severity.BLOCKER, true, "Issue 4", "rule4"), GITHUB_URL, true);

    String desiredMarkdown = "SonarQube analysis reported 5 issues\n" +
      "* ![BLOCKER][BLOCKER] 1 blocker\n" +
      "* ![CRITICAL][CRITICAL] 1 critical\n" +
      "* ![MAJOR][MAJOR] 1 major\n" +
      "* ![MINOR][MINOR] 1 minor\n" +
      "* ![INFO][INFO] 1 info\n" +
      "\nWatch the comments in this conversation to review them.\n" +
      "\n#### 2 extra issues\n" +
      "\nNote: The following issues were found on lines that were not modified in the pull request. Because these issues can't be reported as line comments, they are summarized here:\n\n"
      +
      "1. ![MINOR][MINOR] [sonar-github](https://github.com/SonarSource/sonar-github): Issue 1 [![rule](https://sonarsource.github.io/sonar-github/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule1)\n"
      +
      "1. ![CRITICAL][CRITICAL] [sonar-github](https://github.com/SonarSource/sonar-github): Issue 3 [![rule](https://sonarsource.github.io/sonar-github/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule3)\n"
      + "\n"
      + "[BLOCKER]: https://sonarsource.github.io/sonar-github/severity-blocker.png 'Severity: BLOCKER'\n"
      + "[CRITICAL]: https://sonarsource.github.io/sonar-github/severity-critical.png 'Severity: CRITICAL'\n"
      + "[INFO]: https://sonarsource.github.io/sonar-github/severity-info.png 'Severity: INFO'\n"
      + "[MAJOR]: https://sonarsource.github.io/sonar-github/severity-major.png 'Severity: MAJOR'\n"
      + "[MINOR]: https://sonarsource.github.io/sonar-github/severity-minor.png 'Severity: MINOR'";

    String formattedGlobalReport = globalReport.formatForMarkdown();

    assertThat(formattedGlobalReport).isEqualTo(desiredMarkdown);
  }

  @Test
  public void shouldFormatIssuesForMarkdownWhenInlineCommentsDisabled() {
    GlobalReport globalReport = new GlobalReport(new MarkDownUtils(settings), false);
    globalReport.process(newMockedIssue("component", null, null, Severity.INFO, true, "Issue 0", "rule0"), GITHUB_URL, false);
    globalReport.process(newMockedIssue("component", null, null, Severity.MINOR, true, "Issue 1", "rule1"), GITHUB_URL, false);
    globalReport.process(newMockedIssue("component", null, null, Severity.MAJOR, true, "Issue 2", "rule2"), GITHUB_URL, false);
    globalReport.process(newMockedIssue("component", null, null, Severity.CRITICAL, true, "Issue 3", "rule3"), GITHUB_URL, false);
    globalReport.process(newMockedIssue("component", null, null, Severity.BLOCKER, true, "Issue 4", "rule4"), GITHUB_URL, false);

    String desiredMarkdown = "SonarQube analysis reported 5 issues\n\n" +
      "1. ![INFO][INFO] [sonar-github](https://github.com/SonarSource/sonar-github): Issue 0 [![rule](https://sonarsource.github.io/sonar-github/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule0)\n"
      +
      "1. ![MINOR][MINOR] [sonar-github](https://github.com/SonarSource/sonar-github): Issue 1 [![rule](https://sonarsource.github.io/sonar-github/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule1)\n"
      +
      "1. ![MAJOR][MAJOR] [sonar-github](https://github.com/SonarSource/sonar-github): Issue 2 [![rule](https://sonarsource.github.io/sonar-github/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule2)\n"
      +
      "1. ![CRITICAL][CRITICAL] [sonar-github](https://github.com/SonarSource/sonar-github): Issue 3 [![rule](https://sonarsource.github.io/sonar-github/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule3)\n"
      +
      "1. ![BLOCKER][BLOCKER] [sonar-github](https://github.com/SonarSource/sonar-github): Issue 4 [![rule](https://sonarsource.github.io/sonar-github/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule4)\n"
      + "\n"
      + "[BLOCKER]: https://sonarsource.github.io/sonar-github/severity-blocker.png 'Severity: BLOCKER'\n"
      + "[CRITICAL]: https://sonarsource.github.io/sonar-github/severity-critical.png 'Severity: CRITICAL'\n"
      + "[INFO]: https://sonarsource.github.io/sonar-github/severity-info.png 'Severity: INFO'\n"
      + "[MAJOR]: https://sonarsource.github.io/sonar-github/severity-major.png 'Severity: MAJOR'\n"
      + "[MINOR]: https://sonarsource.github.io/sonar-github/severity-minor.png 'Severity: MINOR'";

    String formattedGlobalReport = globalReport.formatForMarkdown();

    assertThat(formattedGlobalReport).isEqualTo(desiredMarkdown);
  }

  @Test
  public void shouldFormatIssuesForMarkdownWhenInlineCommentsDisabledAndLimitReached() {
    GlobalReport globalReport = new GlobalReport(new MarkDownUtils(settings), false, 4);
    globalReport.process(newMockedIssue("component", null, null, Severity.INFO, true, "Issue 0", "rule0"), GITHUB_URL, false);
    globalReport.process(newMockedIssue("component", null, null, Severity.MINOR, true, "Issue 1", "rule1"), GITHUB_URL, false);
    globalReport.process(newMockedIssue("component", null, null, Severity.MAJOR, true, "Issue 2", "rule2"), GITHUB_URL, false);
    globalReport.process(newMockedIssue("component", null, null, Severity.CRITICAL, true, "Issue 3", "rule3"), GITHUB_URL, false);
    globalReport.process(newMockedIssue("component", null, null, Severity.BLOCKER, true, "Issue 4", "rule4"), GITHUB_URL, false);

    String desiredMarkdown = "SonarQube analysis reported 5 issues\n" +
      "* ![BLOCKER][BLOCKER] 1 blocker\n" +
      "* ![CRITICAL][CRITICAL] 1 critical\n" +
      "* ![MAJOR][MAJOR] 1 major\n" +
      "* ![MINOR][MINOR] 1 minor\n" +
      "* ![INFO][INFO] 1 info\n" +
      "\n#### Top 4 issues\n\n" +
      "1. ![INFO][INFO] [sonar-github](https://github.com/SonarSource/sonar-github): Issue 0 [![rule](https://sonarsource.github.io/sonar-github/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule0)\n"
      +
      "1. ![MINOR][MINOR] [sonar-github](https://github.com/SonarSource/sonar-github): Issue 1 [![rule](https://sonarsource.github.io/sonar-github/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule1)\n"
      +
      "1. ![MAJOR][MAJOR] [sonar-github](https://github.com/SonarSource/sonar-github): Issue 2 [![rule](https://sonarsource.github.io/sonar-github/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule2)\n"
      +
      "1. ![CRITICAL][CRITICAL] [sonar-github](https://github.com/SonarSource/sonar-github): Issue 3 [![rule](https://sonarsource.github.io/sonar-github/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule3)\n"
      + "\n"
      + "[BLOCKER]: https://sonarsource.github.io/sonar-github/severity-blocker.png 'Severity: BLOCKER'\n"
      + "[CRITICAL]: https://sonarsource.github.io/sonar-github/severity-critical.png 'Severity: CRITICAL'\n"
      + "[INFO]: https://sonarsource.github.io/sonar-github/severity-info.png 'Severity: INFO'\n"
      + "[MAJOR]: https://sonarsource.github.io/sonar-github/severity-major.png 'Severity: MAJOR'\n"
      + "[MINOR]: https://sonarsource.github.io/sonar-github/severity-minor.png 'Severity: MINOR'";

    String formattedGlobalReport = globalReport.formatForMarkdown();

    assertThat(formattedGlobalReport).isEqualTo(desiredMarkdown);
  }

  @Test
  public void shouldLimitGlobalIssues() throws MalformedURLException, URISyntaxException {
    GlobalReport globalReport = new GlobalReport(new MarkDownUtils(settings), true);
    for (int i = 0; i < 17; i++) {
      globalReport.process(newMockedIssue("component", null, null, Severity.MAJOR, true, "Issue number:" + i, "rule" + i),
        new URI(GITHUB_URL.getProtocol(), null, GITHUB_URL.getHost(), GITHUB_URL.getPort(),
          GITHUB_URL.getFile() + "/with space/File.java", null, "L" + i).toURL(),
        false);
    }

    String desiredMarkdown = "SonarQube analysis reported 17 issues\n" +
      "* ![MAJOR][MAJOR] 17 major\n" +
      "\n#### Top 10 extra issues\n" +
      "\nNote: The following issues were found on lines that were not modified in the pull request. Because these issues can't be reported as line comments, they are summarized here:\n\n"
      +
      "1. ![MAJOR][MAJOR] [File.java#L0](https://github.com/SonarSource/sonar-github/with%20space/File.java#L0): Issue number:0 [![rule](https://sonarsource.github.io/sonar-github/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule0)\n"
      +
      "1. ![MAJOR][MAJOR] [File.java#L1](https://github.com/SonarSource/sonar-github/with%20space/File.java#L1): Issue number:1 [![rule](https://sonarsource.github.io/sonar-github/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule1)\n"
      +
      "1. ![MAJOR][MAJOR] [File.java#L2](https://github.com/SonarSource/sonar-github/with%20space/File.java#L2): Issue number:2 [![rule](https://sonarsource.github.io/sonar-github/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule2)\n"
      +
      "1. ![MAJOR][MAJOR] [File.java#L3](https://github.com/SonarSource/sonar-github/with%20space/File.java#L3): Issue number:3 [![rule](https://sonarsource.github.io/sonar-github/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule3)\n"
      +
      "1. ![MAJOR][MAJOR] [File.java#L4](https://github.com/SonarSource/sonar-github/with%20space/File.java#L4): Issue number:4 [![rule](https://sonarsource.github.io/sonar-github/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule4)\n"
      +
      "1. ![MAJOR][MAJOR] [File.java#L5](https://github.com/SonarSource/sonar-github/with%20space/File.java#L5): Issue number:5 [![rule](https://sonarsource.github.io/sonar-github/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule5)\n"
      +
      "1. ![MAJOR][MAJOR] [File.java#L6](https://github.com/SonarSource/sonar-github/with%20space/File.java#L6): Issue number:6 [![rule](https://sonarsource.github.io/sonar-github/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule6)\n"
      +
      "1. ![MAJOR][MAJOR] [File.java#L7](https://github.com/SonarSource/sonar-github/with%20space/File.java#L7): Issue number:7 [![rule](https://sonarsource.github.io/sonar-github/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule7)\n"
      +
      "1. ![MAJOR][MAJOR] [File.java#L8](https://github.com/SonarSource/sonar-github/with%20space/File.java#L8): Issue number:8 [![rule](https://sonarsource.github.io/sonar-github/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule8)\n"
      +
      "1. ![MAJOR][MAJOR] [File.java#L9](https://github.com/SonarSource/sonar-github/with%20space/File.java#L9): Issue number:9 [![rule](https://sonarsource.github.io/sonar-github/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule9)\n"
      + "\n"
      + "[MAJOR]: https://sonarsource.github.io/sonar-github/severity-major.png 'Severity: MAJOR'";

    String formattedGlobalReport = globalReport.formatForMarkdown();

    assertThat(formattedGlobalReport).isEqualTo(desiredMarkdown);
  }

  @Test
  public void shouldLimitGlobalIssuesWhenInlineCommentsDisabled() throws MalformedURLException, URISyntaxException {
    GlobalReport globalReport = new GlobalReport(new MarkDownUtils(settings), false);
    for (int i = 0; i < 17; i++) {
      globalReport.process(newMockedIssue("component", null, null, Severity.MAJOR, true, "Issue number:" + i, "rule" + i),
        new URI(GITHUB_URL.getProtocol(), null, GITHUB_URL.getHost(), GITHUB_URL.getPort(),
          GITHUB_URL.getFile() + "/File.java", null, "L" + i).toURL(),
        false);
    }

    String desiredMarkdown = "SonarQube analysis reported 17 issues\n" +
      "* ![MAJOR][MAJOR] 17 major\n" +
      "\n#### Top 10 issues\n\n" +
      "1. ![MAJOR][MAJOR] [File.java#L0](https://github.com/SonarSource/sonar-github/File.java#L0): Issue number:0 [![rule](https://sonarsource.github.io/sonar-github/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule0)\n"
      +
      "1. ![MAJOR][MAJOR] [File.java#L1](https://github.com/SonarSource/sonar-github/File.java#L1): Issue number:1 [![rule](https://sonarsource.github.io/sonar-github/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule1)\n"
      +
      "1. ![MAJOR][MAJOR] [File.java#L2](https://github.com/SonarSource/sonar-github/File.java#L2): Issue number:2 [![rule](https://sonarsource.github.io/sonar-github/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule2)\n"
      +
      "1. ![MAJOR][MAJOR] [File.java#L3](https://github.com/SonarSource/sonar-github/File.java#L3): Issue number:3 [![rule](https://sonarsource.github.io/sonar-github/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule3)\n"
      +
      "1. ![MAJOR][MAJOR] [File.java#L4](https://github.com/SonarSource/sonar-github/File.java#L4): Issue number:4 [![rule](https://sonarsource.github.io/sonar-github/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule4)\n"
      +
      "1. ![MAJOR][MAJOR] [File.java#L5](https://github.com/SonarSource/sonar-github/File.java#L5): Issue number:5 [![rule](https://sonarsource.github.io/sonar-github/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule5)\n"
      +
      "1. ![MAJOR][MAJOR] [File.java#L6](https://github.com/SonarSource/sonar-github/File.java#L6): Issue number:6 [![rule](https://sonarsource.github.io/sonar-github/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule6)\n"
      +
      "1. ![MAJOR][MAJOR] [File.java#L7](https://github.com/SonarSource/sonar-github/File.java#L7): Issue number:7 [![rule](https://sonarsource.github.io/sonar-github/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule7)\n"
      +
      "1. ![MAJOR][MAJOR] [File.java#L8](https://github.com/SonarSource/sonar-github/File.java#L8): Issue number:8 [![rule](https://sonarsource.github.io/sonar-github/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule8)\n"
      +
      "1. ![MAJOR][MAJOR] [File.java#L9](https://github.com/SonarSource/sonar-github/File.java#L9): Issue number:9 [![rule](https://sonarsource.github.io/sonar-github/rule.png)](http://myserver/coding_rules#rule_key=repo%3Arule9)\n"
      + "\n"
      + "[MAJOR]: https://sonarsource.github.io/sonar-github/severity-major.png 'Severity: MAJOR'";

    String formattedGlobalReport = globalReport.formatForMarkdown();

    assertThat(formattedGlobalReport).isEqualTo(desiredMarkdown);
  }
}
