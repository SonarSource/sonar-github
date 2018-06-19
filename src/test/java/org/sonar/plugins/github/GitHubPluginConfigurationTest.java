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

import java.net.Proxy;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.CoreProperties;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.Settings;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.utils.MessageException;
import org.sonar.api.utils.System2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GitHubPluginConfigurationTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private MapSettings settings;
  private GitHubPluginConfiguration config;

  @Before
  public void prepare() {
    settings = new MapSettings(new PropertyDefinitions(GitHubPlugin.class));
    config = new GitHubPluginConfiguration(settings, new System2());
  }

  @Test
  public void guessRepositoryFromScmUrl() {
    try {
      config.repository();
    } catch (Exception e) {
      assertThat(e).isInstanceOf(MessageException.class)
        .hasMessage("Unable to determine GitHub repository name for this project. Please provide it using property '" + GitHubPlugin.GITHUB_REPO
          + "' or configure property '" + CoreProperties.LINKS_SOURCES + "'.");
    }

    settings.setProperty(CoreProperties.LINKS_SOURCES, "do_not_match_1");
    try {
      config.repository();
    } catch (Exception e) {
      assertThat(e).isInstanceOf(MessageException.class)
        .hasMessage("Unable to parse GitHub repository name for this project. Please check configuration:\n  * " + CoreProperties.LINKS_SOURCES_DEV
          + ": null\n  * " + CoreProperties.LINKS_SOURCES + ": do_not_match_1");
    }
    settings.clear();
    settings.setProperty(CoreProperties.LINKS_SOURCES_DEV, "do_not_match_2");
    try {
      config.repository();
    } catch (Exception e) {
      assertThat(e).isInstanceOf(MessageException.class)
        .hasMessage("Unable to parse GitHub repository name for this project. Please check configuration:\n  * " + CoreProperties.LINKS_SOURCES_DEV
          + ": do_not_match_2\n  * " + CoreProperties.LINKS_SOURCES + ": null");
    }

    settings.clear();
    settings.setProperty(CoreProperties.LINKS_SOURCES, "scm:git:git@github.com:SonarSource/github-integration.git");
    assertThat(config.repository()).isEqualTo("SonarSource/github-integration");

    settings.setProperty(CoreProperties.LINKS_SOURCES_DEV, "do_not_parse");
    assertThat(config.repository()).isEqualTo("SonarSource/github-integration");

    settings.setProperty(CoreProperties.LINKS_SOURCES_DEV, "scm:git:git@github.com:SonarCommunity2/github-integration.git");
    assertThat(config.repository()).isEqualTo("SonarCommunity2/github-integration");

    settings.removeProperty(CoreProperties.LINKS_SOURCES);
    assertThat(config.repository()).isEqualTo("SonarCommunity2/github-integration");

    settings.setProperty(GitHubPlugin.GITHUB_REPO, "https://github.com/SonarSource/sonar-github.git");
    assertThat(config.repository()).isEqualTo("SonarSource/sonar-github");
    settings.setProperty(GitHubPlugin.GITHUB_REPO, "http://github.com/SonarSource/sonar-github.git");
    assertThat(config.repository()).isEqualTo("SonarSource/sonar-github");
    settings.setProperty(GitHubPlugin.GITHUB_REPO, "SonarCommunity3/github-integration");
    assertThat(config.repository()).isEqualTo("SonarCommunity3/github-integration");
  }

  @Test
  public void other() {
    settings.setProperty(GitHubPlugin.GITHUB_OAUTH, "oauth");
    assertThat(config.oauth()).isEqualTo("oauth");

    assertThat(config.isEnabled()).isFalse();
    settings.setProperty(GitHubPlugin.GITHUB_PULL_REQUEST, "3");
    assertThat(config.pullRequestNumber()).isEqualTo(3);
    assertThat(config.isEnabled()).isTrue();

    assertThat(config.endpoint()).isEqualTo("https://api.github.com");
    settings.setProperty(GitHubPlugin.GITHUB_ENDPOINT, "http://myprivate-endpoint");
    assertThat(config.endpoint()).isEqualTo("http://myprivate-endpoint");

    assertThat(config.tryReportIssuesInline()).isTrue();
    settings.setProperty(GitHubPlugin.GITHUB_DISABLE_INLINE_COMMENTS, "true");
    assertThat(config.tryReportIssuesInline()).isFalse();
  }

  @Test
  public void testProxyConfiguration() {
    System2 system2 = mock(System2.class);
    config = new GitHubPluginConfiguration(settings, system2);
    assertThat(config.isProxyConnectionEnabled()).isFalse();
    when(system2.property("http.proxyHost")).thenReturn("foo");
    assertThat(config.isProxyConnectionEnabled()).isTrue();
    when(system2.property("https.proxyHost")).thenReturn("bar");
    assertThat(config.getHttpProxy()).isEqualTo(Proxy.NO_PROXY);

    settings.setProperty(GitHubPlugin.GITHUB_ENDPOINT, "wrong url");
    thrown.expect(IllegalArgumentException.class);
    config.getHttpProxy();
  }

}
