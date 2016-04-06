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

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.sonar.api.CoreProperties;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.Settings;
import org.sonar.api.issue.internal.DefaultIssue;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rule.Severity;

import static org.assertj.core.api.Assertions.assertThat;

public class GlobalReportTest {

  private static final String GITHUB_URL = "https://github.com/SonarCommunity/sonar-github";

  private List<DefaultIssue> issues = new ArrayList<>();

  private Settings settings;

  @Before
  public void setup() {
    for (int i = 0; i < 20; i++) {
      issues.add(new DefaultIssue()
        .setSeverity(Severity.MAJOR)
        .setMessage("Issue number:" + i)
        .setRuleKey(RuleKey.of("repo", "issue" + i))
        .setComponentKey("component" + i));
    }
    settings = new Settings(new PropertyDefinitions(PropertyDefinition.builder(CoreProperties.SERVER_BASE_URL)
      .name("Server base URL")
      .description("HTTP URL of this SonarQube server, such as <i>http://yourhost.yourdomain/sonar</i>. This value is used i.e. to create links in emails.")
      .category(CoreProperties.CATEGORY_GENERAL)
      .defaultValue(CoreProperties.SERVER_BASE_URL_DEFAULT_VALUE)
      .build()));

    settings.setProperty("sonar.host.url", "http://myserver");
  }

  @Test
  public void noIssues() {
    GlobalReport globalReport = new GlobalReport(new MarkDownUtils(settings), true);

    String desiredMarkdown = "#### Analysis summary\n" +
      "SonarQube analysis reported no issues.";

    String formattedGlobalReport = globalReport.formatForMarkdown();

    assertThat(formattedGlobalReport).isEqualTo(desiredMarkdown);
  }

  @Test
  public void oneIssue() {
    GlobalReport globalReport = new GlobalReport(new MarkDownUtils(settings), true);
    globalReport.process(issues.get(0).setSeverity(Severity.INFO), GITHUB_URL, true);

    String desiredMarkdown = "#### Analysis summary\n" +
      "SonarQube analysis reported 1 issue\n" +
      "* ![INFO](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-info.png) 1 info\n" +
      "\nWatch the comments in this conversation to review them.\n";

    String formattedGlobalReport = globalReport.formatForMarkdown();

    assertThat(formattedGlobalReport).isEqualTo(desiredMarkdown);
  }

  @Test
  public void oneIssueOnDir() {
    GlobalReport globalReport = new GlobalReport(new MarkDownUtils(settings), true);
    globalReport.process(issues.get(0).setSeverity(Severity.INFO), null, false);

    String desiredMarkdown = "#### Analysis summary\n" +
      "SonarQube analysis reported 1 issue\n\n" +
      "#### 1 unreported issues\n\n" +
      "Note: the following issues could not be reported as comments because they are located on lines that are not displayed in this pull request:\n\n" +
      "1. component0: ![INFO](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-info.png) Issue number:0 [![rule](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Aissue0)\n";

    String formattedGlobalReport = globalReport.formatForMarkdown();

    assertThat(formattedGlobalReport).isEqualTo(desiredMarkdown);
  }

  @Test
  public void shouldFormatIssuesForMarkdownNoInline() {
    GlobalReport globalReport = new GlobalReport(new MarkDownUtils(settings), true);
    globalReport.process(issues.get(0).setSeverity(Severity.INFO), GITHUB_URL, true);
    globalReport.process(issues.get(1).setSeverity(Severity.MINOR), GITHUB_URL, true);
    globalReport.process(issues.get(2).setSeverity(Severity.MAJOR), GITHUB_URL, true);
    globalReport.process(issues.get(3).setSeverity(Severity.CRITICAL), GITHUB_URL, true);
    globalReport.process(issues.get(4).setSeverity(Severity.BLOCKER), GITHUB_URL, true);

    String desiredMarkdown = "#### Analysis summary\n" +
      "SonarQube analysis reported 5 issues\n" +
      "* ![BLOCKER](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-blocker.png) 1 blocker\n" +
      "* ![CRITICAL](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-critical.png) 1 critical\n" +
      "* ![MAJOR](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-major.png) 1 major\n" +
      "* ![MINOR](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-minor.png) 1 minor\n" +
      "* ![INFO](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-info.png) 1 info\n" +
      "\nWatch the comments in this conversation to review them.\n";

    String formattedGlobalReport = globalReport.formatForMarkdown();

    assertThat(formattedGlobalReport).isEqualTo(desiredMarkdown);
  }

  @Test
  public void shouldFormatIssuesForMarkdownMixInlineGlobal() {
    GlobalReport globalReport = new GlobalReport(new MarkDownUtils(settings), true);
    globalReport.process(issues.get(0).setSeverity(Severity.INFO), GITHUB_URL, true);
    globalReport.process(issues.get(1).setSeverity(Severity.MINOR), GITHUB_URL, false);
    globalReport.process(issues.get(2).setSeverity(Severity.MAJOR), GITHUB_URL, true);
    globalReport.process(issues.get(3).setSeverity(Severity.CRITICAL), GITHUB_URL, false);
    globalReport.process(issues.get(4).setSeverity(Severity.BLOCKER), GITHUB_URL, true);

    String desiredMarkdown = "#### Analysis summary\n" +
      "SonarQube analysis reported 5 issues\n" +
      "* ![BLOCKER](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-blocker.png) 1 blocker\n" +
      "* ![CRITICAL](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-critical.png) 1 critical\n" +
      "* ![MAJOR](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-major.png) 1 major\n" +
      "* ![MINOR](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-minor.png) 1 minor\n" +
      "* ![INFO](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-info.png) 1 info\n" +
      "\nWatch the comments in this conversation to review them.\n" +
      "\n#### 2 unreported issues\n" +
      "\nNote: the following issues could not be reported as comments because they are located on lines that are not displayed in this pull request:\n\n" +
      "1. [sonar-github](https://github.com/SonarCommunity/sonar-github): ![MINOR](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-minor.png) Issue number:1 [![rule](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Aissue1)\n"
      +
      "1. [sonar-github](https://github.com/SonarCommunity/sonar-github): ![CRITICAL](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-critical.png) Issue number:3 [![rule](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Aissue3)\n";

    String formattedGlobalReport = globalReport.formatForMarkdown();

    assertThat(formattedGlobalReport).isEqualTo(desiredMarkdown);
  }

  @Test
  public void shouldFormatIssuesForMarkdownWhenInlineCommentsDisabled() {
    GlobalReport globalReport = new GlobalReport(new MarkDownUtils(settings), false);
    globalReport.process(issues.get(0).setSeverity(Severity.INFO), GITHUB_URL, false);
    globalReport.process(issues.get(1).setSeverity(Severity.MINOR), GITHUB_URL, false);
    globalReport.process(issues.get(2).setSeverity(Severity.MAJOR), GITHUB_URL, false);
    globalReport.process(issues.get(3).setSeverity(Severity.CRITICAL), GITHUB_URL, false);
    globalReport.process(issues.get(4).setSeverity(Severity.BLOCKER), GITHUB_URL, false);

    String desiredMarkdown = "#### Analysis summary\n" +
      "SonarQube analysis reported 5 issues\n\n" +
      "1. [sonar-github](https://github.com/SonarCommunity/sonar-github): ![INFO](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-info.png) Issue number:0 [![rule](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Aissue0)\n"
      +
      "1. [sonar-github](https://github.com/SonarCommunity/sonar-github): ![MINOR](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-minor.png) Issue number:1 [![rule](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Aissue1)\n"
      +
      "1. [sonar-github](https://github.com/SonarCommunity/sonar-github): ![MAJOR](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-major.png) Issue number:2 [![rule](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Aissue2)\n"
      +
      "1. [sonar-github](https://github.com/SonarCommunity/sonar-github): ![CRITICAL](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-critical.png) Issue number:3 [![rule](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Aissue3)\n"
      +
      "1. [sonar-github](https://github.com/SonarCommunity/sonar-github): ![BLOCKER](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-blocker.png) Issue number:4 [![rule](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Aissue4)\n";

    String formattedGlobalReport = globalReport.formatForMarkdown();

    assertThat(formattedGlobalReport).isEqualTo(desiredMarkdown);
  }

  @Test
  public void shouldFormatIssuesForMarkdownWhenInlineCommentsDisabledAndLimitReached() {
    GlobalReport globalReport = new GlobalReport(new MarkDownUtils(settings), false, 4);
    globalReport.process(issues.get(0).setSeverity(Severity.INFO), GITHUB_URL, false);
    globalReport.process(issues.get(1).setSeverity(Severity.MINOR), GITHUB_URL, false);
    globalReport.process(issues.get(2).setSeverity(Severity.MAJOR), GITHUB_URL, false);
    globalReport.process(issues.get(3).setSeverity(Severity.CRITICAL), GITHUB_URL, false);
    globalReport.process(issues.get(4).setSeverity(Severity.BLOCKER), GITHUB_URL, false);

    String desiredMarkdown = "#### Analysis summary\n" +
      "SonarQube analysis reported 5 issues\n" +
      "* ![BLOCKER](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-blocker.png) 1 blocker\n" +
      "* ![CRITICAL](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-critical.png) 1 critical\n" +
      "* ![MAJOR](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-major.png) 1 major\n" +
      "* ![MINOR](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-minor.png) 1 minor\n" +
      "* ![INFO](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-info.png) 1 info\n" +
      "\n#### Top 4 issues\n\n" +
      "1. [sonar-github](https://github.com/SonarCommunity/sonar-github): ![INFO](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-info.png) Issue number:0 [![rule](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Aissue0)\n"
      +
      "1. [sonar-github](https://github.com/SonarCommunity/sonar-github): ![MINOR](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-minor.png) Issue number:1 [![rule](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Aissue1)\n"
      +
      "1. [sonar-github](https://github.com/SonarCommunity/sonar-github): ![MAJOR](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-major.png) Issue number:2 [![rule](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Aissue2)\n"
      +
      "1. [sonar-github](https://github.com/SonarCommunity/sonar-github): ![CRITICAL](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-critical.png) Issue number:3 [![rule](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Aissue3)\n";

    String formattedGlobalReport = globalReport.formatForMarkdown();

    assertThat(formattedGlobalReport).isEqualTo(desiredMarkdown);
  }

  @Test
  public void shouldLimitGlobalIssues() {
    GlobalReport globalReport = new GlobalReport(new MarkDownUtils(settings), true);
    for (int i = 0; i < 17; i++) {
      globalReport.process(issues.get(i), GITHUB_URL + "/File.java#L" + i, false);
    }

    String desiredMarkdown = "#### Analysis summary\n" +
      "SonarQube analysis reported 17 issues\n" +
      "* ![MAJOR](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-major.png) 17 major\n" +
      "\n#### Top 10 unreported issues\n" +
      "\nNote: the following issues could not be reported as comments because they are located on lines that are not displayed in this pull request:\n\n" +
      "1. [File.java#L0](https://github.com/SonarCommunity/sonar-github/File.java#L0): ![MAJOR](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-major.png) Issue number:0 [![rule](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Aissue0)\n"
      +
      "1. [File.java#L1](https://github.com/SonarCommunity/sonar-github/File.java#L1): ![MAJOR](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-major.png) Issue number:1 [![rule](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Aissue1)\n"
      +
      "1. [File.java#L2](https://github.com/SonarCommunity/sonar-github/File.java#L2): ![MAJOR](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-major.png) Issue number:2 [![rule](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Aissue2)\n"
      +
      "1. [File.java#L3](https://github.com/SonarCommunity/sonar-github/File.java#L3): ![MAJOR](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-major.png) Issue number:3 [![rule](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Aissue3)\n"
      +
      "1. [File.java#L4](https://github.com/SonarCommunity/sonar-github/File.java#L4): ![MAJOR](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-major.png) Issue number:4 [![rule](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Aissue4)\n"
      +
      "1. [File.java#L5](https://github.com/SonarCommunity/sonar-github/File.java#L5): ![MAJOR](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-major.png) Issue number:5 [![rule](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Aissue5)\n"
      +
      "1. [File.java#L6](https://github.com/SonarCommunity/sonar-github/File.java#L6): ![MAJOR](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-major.png) Issue number:6 [![rule](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Aissue6)\n"
      +
      "1. [File.java#L7](https://github.com/SonarCommunity/sonar-github/File.java#L7): ![MAJOR](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-major.png) Issue number:7 [![rule](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Aissue7)\n"
      +
      "1. [File.java#L8](https://github.com/SonarCommunity/sonar-github/File.java#L8): ![MAJOR](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-major.png) Issue number:8 [![rule](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Aissue8)\n"
      +
      "1. [File.java#L9](https://github.com/SonarCommunity/sonar-github/File.java#L9): ![MAJOR](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-major.png) Issue number:9 [![rule](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Aissue9)\n";

    String formattedGlobalReport = globalReport.formatForMarkdown();

    assertThat(formattedGlobalReport).isEqualTo(desiredMarkdown);
  }

  @Test
  public void shouldLimitGlobalIssuesWhenInlineCommentsDisabled() {
    GlobalReport globalReport = new GlobalReport(new MarkDownUtils(settings), false);
    for (int i = 0; i < 17; i++) {
      globalReport.process(issues.get(i), GITHUB_URL + "/File.java#L" + i, false);
    }

    String desiredMarkdown = "#### Analysis summary\n" +
      "SonarQube analysis reported 17 issues\n" +
      "* ![MAJOR](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-major.png) 17 major\n" +
      "\n#### Top 10 issues\n\n" +
      "1. [File.java#L0](https://github.com/SonarCommunity/sonar-github/File.java#L0): ![MAJOR](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-major.png) Issue number:0 [![rule](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Aissue0)\n"
      +
      "1. [File.java#L1](https://github.com/SonarCommunity/sonar-github/File.java#L1): ![MAJOR](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-major.png) Issue number:1 [![rule](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Aissue1)\n"
      +
      "1. [File.java#L2](https://github.com/SonarCommunity/sonar-github/File.java#L2): ![MAJOR](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-major.png) Issue number:2 [![rule](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Aissue2)\n"
      +
      "1. [File.java#L3](https://github.com/SonarCommunity/sonar-github/File.java#L3): ![MAJOR](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-major.png) Issue number:3 [![rule](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Aissue3)\n"
      +
      "1. [File.java#L4](https://github.com/SonarCommunity/sonar-github/File.java#L4): ![MAJOR](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-major.png) Issue number:4 [![rule](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Aissue4)\n"
      +
      "1. [File.java#L5](https://github.com/SonarCommunity/sonar-github/File.java#L5): ![MAJOR](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-major.png) Issue number:5 [![rule](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Aissue5)\n"
      +
      "1. [File.java#L6](https://github.com/SonarCommunity/sonar-github/File.java#L6): ![MAJOR](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-major.png) Issue number:6 [![rule](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Aissue6)\n"
      +
      "1. [File.java#L7](https://github.com/SonarCommunity/sonar-github/File.java#L7): ![MAJOR](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-major.png) Issue number:7 [![rule](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Aissue7)\n"
      +
      "1. [File.java#L8](https://github.com/SonarCommunity/sonar-github/File.java#L8): ![MAJOR](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-major.png) Issue number:8 [![rule](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Aissue8)\n"
      +
      "1. [File.java#L9](https://github.com/SonarCommunity/sonar-github/File.java#L9): ![MAJOR](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/severity-major.png) Issue number:9 [![rule](https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/rule.png)](http://myserver/coding_rules#rule_key=repo%3Aissue9)\n";

    String formattedGlobalReport = globalReport.formatForMarkdown();

    assertThat(formattedGlobalReport).isEqualTo(desiredMarkdown);
  }
}
