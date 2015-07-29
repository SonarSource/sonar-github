/*
 * SonarQube :: GitHub Plugin
 * Copyright (C) 2015 SonarSource
 * sonarqube@googlegroups.com
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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import javax.annotation.Nullable;

public class MarkDownUtils {

  private static final String IMAGES_ROOT_URL = "https://raw.githubusercontent.com/SonarCommunity/sonar-github/master/images/";

  private MarkDownUtils() {
    // Utility class
  }

  public static String inlineIssue(String severity, String message, String ruleKey) {
    String ruleLink = getRuleLink(ruleKey);
    StringBuilder sb = new StringBuilder();
    sb.append(getImageMarkdownForSeverity(severity))
      .append(" ")
      .append(message)
      .append(" ")
      .append(ruleLink);
    return sb.toString();
  }

  public static String globalIssue(String severity, String message, String ruleKey, @Nullable String url, String componentKey) {
    String ruleLink = getRuleLink(ruleKey);
    StringBuilder sb = new StringBuilder();
    sb.append(getImageMarkdownForSeverity(severity)).append(" ");
    if (url != null) {
      sb.append("[").append(message).append("]").append("(").append(url).append(")");
    } else {
      sb.append(message).append(" ").append("(").append(componentKey).append(")");
    }
    sb.append(" ").append(ruleLink);
    return sb.toString();
  }

  static String getRuleLink(String ruleKey) {
    return "[![rule](" + IMAGES_ROOT_URL + "rule.png)](http://nemo.sonarqube.org/coding_rules#rule_key=" + encodeForUrl(ruleKey) + ")";
  }

  static String encodeForUrl(String url) {
    try {
      return URLEncoder.encode(url, "UTF-8");

    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("Encoding not supported", e);
    }
  }

  public static String getImageMarkdownForSeverity(String severity) {
    return "![" + severity + "](" + IMAGES_ROOT_URL + "severity-" + severity.toLowerCase() + ".png)";
  }

}
