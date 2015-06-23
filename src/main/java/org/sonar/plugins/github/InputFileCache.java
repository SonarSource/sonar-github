/*
 * SonarQube :: GitHub Plugin :: Parent
 * Copyright (C) 2009 ${owner}
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

import java.util.HashMap;
import java.util.Map;
import javax.annotation.CheckForNull;
import org.sonar.api.batch.Sensor;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.resources.Project;

/**
 * This is a temporary solution before being able to use new postjob API in SQ 5.2.
 */
public class InputFileCache implements Sensor {

  private final GitHubPluginConfiguration gitHubPluginConfiguration;
  private final FileSystem fs;
  private final Map<String, InputFile> inputFileByKey = new HashMap<>();

  public InputFileCache(GitHubPluginConfiguration gitHubPluginConfiguration, FileSystem fs) {
    this.gitHubPluginConfiguration = gitHubPluginConfiguration;
    this.fs = fs;
  }

  @Override
  public boolean shouldExecuteOnProject(Project project) {
    return gitHubPluginConfiguration.isEnabled();
  }

  @Override
  public void analyse(Project module, SensorContext context) {
    for (InputFile inputFile : fs.inputFiles(fs.predicates().all())) {
      inputFileByKey.put(context.getResource(inputFile).getEffectiveKey(), inputFile);
    }
  }

  @CheckForNull
  public InputFile byKey(String componentKey) {
    return inputFileByKey.get(componentKey);
  }

  @Override
  public String toString() {
    return "GitHub Plugin InputFile Cache";
  }

}
