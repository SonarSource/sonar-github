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

import org.sonar.api.BatchComponent;
import org.sonar.api.batch.InstantiationStrategy;
import org.sonar.api.resources.Project;

import javax.annotation.CheckForNull;

import java.util.HashMap;
import java.util.Map;

@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
public class FileCache implements BatchComponent {

  private static class File {

    private Project parentModule;
    private String relativePath;

    public File(Project parent, String relativePath) {
      this.parentModule = parent;
      this.relativePath = relativePath;
    }
  }

  private Map<String, File> componentsFullPathByKey = new HashMap<>();

  public void add(String key, Project parent, String relativePath) {
    componentsFullPathByKey.put(key, new File(parent, relativePath));
  }

  @CheckForNull
  public String getPathFromProjectBaseDir(String fileKey) {
    File file = componentsFullPathByKey.get(fileKey);
    if (file == null) {
      return null;
    }
    StringBuilder fullPath = new StringBuilder();
    prependParentPath(fullPath, file.parentModule);
    if (fullPath.length() > 0) {
      fullPath.append("/");
    }
    fullPath.append(file.relativePath);
    return fullPath.toString();
  }

  private void prependParentPath(StringBuilder fullPath, Project module) {
    if (module.getParent() != null) {
      prependParentPath(fullPath, module.getParent());
    }
    if (fullPath.length() > 0) {
      fullPath.append("/");
    }
    fullPath.append(module.getPath());
  }
}
