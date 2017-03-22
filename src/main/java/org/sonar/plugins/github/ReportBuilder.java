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

import java.net.URL;
import javax.annotation.Nullable;
import org.sonar.api.batch.postjob.issue.PostJobIssue;
import org.sonar.api.batch.rule.Severity;

public interface ReportBuilder {
  /**
   * Append project id to the report.
   *
   * @param projectId Project id to append
   * @return a reference to this object
     */
  ReportBuilder appendProjectId(String projectId);

  /**
   * Append an object to the report, using its toString() method.
   *
   * @param o object to append
   * @return a reference to this object
   */
  ReportBuilder append(Object o);

  /**
   * Append a severity image.
   *
   * @param severity the severity to display
   * @return a reference to this object
   */
  ReportBuilder append(Severity severity);

  /**
   * Register an "extra issue" (not reported on a diff), without appending.
   * Note that extra issues are not always included in the final rendered report.
   *
   * @param issue the extra issue to append
   * @param gitHubUrl GitHub URL
   * @return a reference to this object
   */
  ReportBuilder registerExtraIssue(PostJobIssue issue, @Nullable URL gitHubUrl);

  /**
   * Append the registered extra issues.
   *
   * @return a reference to this object
   */
  ReportBuilder appendExtraIssues();
}
