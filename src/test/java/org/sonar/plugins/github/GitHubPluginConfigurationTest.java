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

import org.junit.Before;
import org.junit.Test;
import org.sonar.api.CoreProperties;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.Settings;

import static org.assertj.core.api.Assertions.assertThat;

public class GitHubPluginConfigurationTest {

  private Settings settings;
  private GitHubPluginConfiguration config;

  @Before
  public void prepare() {
    settings = new Settings(new PropertyDefinitions(GitHubPlugin.class));
    config = new GitHubPluginConfiguration(settings);
  }

  @Test
  public void guessRepositoryFromScmUrl() {
    assertThat(config.repository()).isNull();

    settings.setProperty(CoreProperties.LINKS_SOURCES, "do_not_match");
    settings.setProperty(CoreProperties.LINKS_SOURCES_DEV, "do_not_match");
    assertThat(config.repository()).isNull();

    settings.setProperty(CoreProperties.LINKS_SOURCES, "scm:git:git@github.com:SonarCommunity/github-integration.git");
    assertThat(config.repository()).isEqualTo("SonarCommunity/github-integration");

    settings.setProperty(CoreProperties.LINKS_SOURCES_DEV, "scm:git:git@github.com:SonarCommunity2/github-integration.git");
    assertThat(config.repository()).isEqualTo("SonarCommunity2/github-integration");

    settings.setProperty(GitHubPlugin.GITHUB_REPO, "SonarCommunity3/github-integration");
    assertThat(config.repository()).isEqualTo("SonarCommunity3/github-integration");
  }

  @Test
  public void other() {
    settings.setProperty(GitHubPlugin.GITHUB_LOGIN, "login");
    assertThat(config.login()).isEqualTo("login");

    settings.setProperty(GitHubPlugin.GITHUB_OAUTH, "oauth");
    assertThat(config.oauth()).isEqualTo("oauth");

    assertThat(config.isEnabled()).isFalse();
    settings.setProperty(GitHubPlugin.GITHUB_PULL_REQUEST, "3");
    assertThat(config.pullRequestNumber()).isEqualTo(3);
    assertThat(config.isEnabled()).isTrue();
  }

}
