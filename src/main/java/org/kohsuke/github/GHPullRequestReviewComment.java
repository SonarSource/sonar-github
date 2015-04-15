/*
 * SonarQube :: GitHub Plugin
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
package org.kohsuke.github;

import java.io.IOException;

/**
 * Review comment to the pull request
 *
 * @author Julien Henry
 */
public class GHPullRequestReviewComment extends GHObject {
  GHPullRequest owner;

  private String body;
  private GHUser user;
  private String path;
  private int position;
  private int originalPosition;

  /* package */GHPullRequestReviewComment wrapUp(GHPullRequest owner) {
    this.owner = owner;
    return this;
  }

  /**
   * Gets the pull request to which this review comment is associated.
   */
  public GHPullRequest getParent() {
    return owner;
  }

  /**
   * The comment itself.
   */
  public String getBody() {
    return body;
  }

  /**
   * Gets the user who posted this comment.
   */
  public GHUser getUser() throws IOException {
    return owner.root.getUser(user.getLogin());
  }

  public String getPath() {
    return path;
  }

  public int getPosition() {
    return position;
  }

  public int getOriginalPosition() {
    return originalPosition;
  }
}
