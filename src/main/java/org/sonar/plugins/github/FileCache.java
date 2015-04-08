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
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.scan.filesystem.PathResolver;

import javax.annotation.CheckForNull;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
public class FileCache implements BatchComponent {

  private Map<String, String> componentsFullPathByKey = new HashMap<>();
  private File baseDir;
  private PathResolver resolver;

  public FileCache(PathResolver resolver) {
    this.resolver = resolver;
  }

  public void add(String effectiveKey, InputFile inputFile) {
    componentsFullPathByKey.put(effectiveKey, resolver.relativePath(baseDir, inputFile.file()));
  }

  @CheckForNull
  public String getPathFromProjectBaseDir(String fileKey) {
    return componentsFullPathByKey.get(fileKey);
  }

  public void setProjectBaseDir(File baseDir) {
    this.baseDir = baseDir;
  }

}
