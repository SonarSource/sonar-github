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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.apache.commons.lang.StringUtils;
import org.sonar.api.CoreProperties;
import org.sonar.api.batch.BatchSide;
import org.sonar.api.batch.InstantiationStrategy;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.config.Settings;

@BatchSide
@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
public class MarkDownUtils {

  private static final String IMAGES_ROOT_URL = "https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/";
  private final String ruleUrlPrefix;

  public MarkDownUtils(Settings settings) {
    // If server base URL was not configured in SQ server then is is better to take URL configured on batch side
    String baseUrl = settings.hasKey(CoreProperties.SERVER_BASE_URL) ? settings.getString(CoreProperties.SERVER_BASE_URL) : settings.getString("sonar.host.url");
    if (!baseUrl.endsWith("/")) {
      baseUrl += "/";
    }
    this.ruleUrlPrefix = baseUrl;
  }

  public String inlineIssue(Severity severity, String message, String ruleKey) {
    String ruleLink = getRuleLink(ruleKey);
    StringBuilder sb = new StringBuilder();
    sb.append(getImageMarkdownForSeverity(severity))
      .append(" ")
      .append(message)
      .append(" ")
      .append(ruleLink);
    return sb.toString();
  }

  private static String getLocation(String url) {
    String filename = Pattern.compile(".*/", Pattern.DOTALL).matcher(url).replaceAll(StringUtils.EMPTY);
    if (filename.length() <= 0) {
      filename = "Project";
    }

    return filename;
  }

  public String globalIssue(Severity severity, String message, String ruleKey, @Nullable String url, String componentKey) {
    String ruleLink = getRuleLink(ruleKey);
    StringBuilder sb = new StringBuilder();
    sb.append(getImageMarkdownForSeverity(severity)).append(" ");
    if (url != null) {
      sb.append("[").append(getLocation(url)).append("]").append("(").append(url).append(")");
    } else {
      sb.append(componentKey);
    }
    sb.append(": ").append(message).append(" ").append(ruleLink);
    return sb.toString();
  }

  String getRuleLink(String ruleKey) {
    return "[![rule](" + IMAGES_ROOT_URL + "rule.png)](" + ruleUrlPrefix + "coding_rules#rule_key=" + encodeForUrl(ruleKey) + ")";
  }

  static String encodeForUrl(String url) {
    try {
      return URLEncoder.encode(url, "UTF-8");

    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("Encoding not supported", e);
    }
  }

  public static String getImageMarkdownForSeverity(Severity severity) {
    return "![" + severity.name() + "](" + IMAGES_ROOT_URL + "severity-" + severity.name().toLowerCase(Locale.ENGLISH) + ".png)";
  }

}
