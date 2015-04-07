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

import org.apache.commons.io.IOUtils;
import org.kohsuke.github.*;
import org.sonar.api.BatchComponent;
import org.sonar.api.batch.InstantiationStrategy;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
public class PullRequestFacade implements BatchComponent {

  private final GitHubPluginConfiguration config;
  private Map<String, Map<Integer, Integer>> patchPositionMappingByFile;
  private Map<String, Map<Integer, GHPullRequestReviewComment>> existingReviewCommentsByLocationByFile;
  private GHRepository ghRepo;
  private GHPullRequest pr;
  private Set<Integer> reviewCommentIdsToBeDeleted = new HashSet<>();

  public PullRequestFacade(GitHubPluginConfiguration config) {
    this.config = config;
  }

  public void init(int pullRequestNumber) {
    try {
      GitHub github = new GitHubBuilder().withOAuthToken(config.oauth(), config.login()).build();
      ghRepo = github.getRepository(config.repository());
      pr = ghRepo.getPullRequest(pullRequestNumber);
      existingReviewCommentsByLocationByFile = loadExistingReviewComments(github.getMyself().getLogin());
      patchPositionMappingByFile = mapPatchPositionsToLines(pr);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to perform GitHub WS operation", e);
    }
  }

  /**
   * Load all previous comments made by provided github account.
   */
  private Map<String, Map<Integer, GHPullRequestReviewComment>> loadExistingReviewComments(String githubAccount) throws IOException {
    Map<String, Map<Integer, GHPullRequestReviewComment>> existingReviewCommentsByLocationByFile = new HashMap<String, Map<Integer, GHPullRequestReviewComment>>();
    for (GHPullRequestReviewComment comment : pr.listReviewComments()) {
      if (!githubAccount.equals(comment.getUser().getLogin())) {
        // Ignore comments from other users
        continue;
      }
      if (!existingReviewCommentsByLocationByFile.containsKey(comment.getPath())) {
        existingReviewCommentsByLocationByFile.put(comment.getPath(), new HashMap<Integer, GHPullRequestReviewComment>());
      }
      // By default all previous comments will be marked for deletion
      reviewCommentIdsToBeDeleted.add(comment.getId());
      existingReviewCommentsByLocationByFile.get(comment.getPath()).put(comment.getPosition(), comment);
    }
    return existingReviewCommentsByLocationByFile;
  }

  /**
   * GitHub expect review comments to be added on "patch lines" (aka position) but not on file lines. So we have to iterate over each patch and compute corresponding file line in order to later map issues to the correct position.
   * @return Map File path -> Line -> Position
   */
  private static Map<String, Map<Integer, Integer>> mapPatchPositionsToLines(GHPullRequest pr) throws IOException {
    Map<String, Map<Integer, Integer>> patchPositionMappingByFile = new HashMap<String, Map<Integer, Integer>>();
    for (GHPullRequestFileDetail file : pr.listFiles()) {
      Map<Integer, Integer> patchLocationMapping = new HashMap<>();
      patchPositionMappingByFile.put(file.getFilename(), patchLocationMapping);
      String patch = file.getPatch();
      if (patch == null) {
        continue;
      }
      int currentLine = -1;
      int patchLocation = 0;
      for (String line : IOUtils.readLines(new StringReader(patch))) {
        if (line.startsWith("@")) {
          Matcher matcher = Pattern.compile("@@\\p{IsWhite_Space}-[0-9]+,[0-9]+\\p{IsWhite_Space}\\+([0-9]+),[0-9]+\\p{IsWhite_Space}@@.*").matcher(line);
          if (!matcher.matches()) {
            throw new IllegalStateException("Unable to parse patch line " + line);
          }
          currentLine = Integer.parseInt(matcher.group(1));
        } else if (line.startsWith("-")) {
          // Skip
        } else {
          patchLocationMapping.put(currentLine, patchLocation);
          currentLine++;
        }
        patchLocation++;
      }
    }
    return patchPositionMappingByFile;
  }

  /**
   * Test if the P/R contains the provided file path (ie this file was added/modified/updated)
   */
  public boolean hasFile(String fullpath) {
    return patchPositionMappingByFile.containsKey(fullpath);
  }

  /**
   * Test if the P/R contains the provided line for the file path (ie this line is "visible" in diff)
   */
  public boolean hasFileLine(String fullpath, int line) {
    return patchPositionMappingByFile.get(fullpath).containsKey(line);
  }

  public void createOrUpdateReviewComment(String fullpath, Integer line, String body) {
    Integer lineInPatch = patchPositionMappingByFile.get(fullpath).get(line);
    if (existingReviewCommentsByLocationByFile.containsKey(fullpath) && existingReviewCommentsByLocationByFile.get(fullpath).containsKey(lineInPatch)) {
      GHPullRequestReviewComment existingReview = existingReviewCommentsByLocationByFile.get(fullpath).get(lineInPatch);
      try {
        if (!existingReview.getBody().equals(body)) {
          pr.updateReviewComment(existingReview.getId(), body);
          reviewCommentIdsToBeDeleted.remove(existingReview.getId());
        } else {
          pr.createReviewComment(body, pr.getHead().getSha(), fullpath, lineInPatch);
        }
      } catch (IOException e) {
        throw new IllegalStateException("Unable to create or update review comment in file " + fullpath + " at line " + line, e);
      }
    }

  }

  public void deleteOutdatedComments() {
    for (Integer idReviewToDelete : reviewCommentIdsToBeDeleted) {
      try {
        pr.deleteReviewComment(idReviewToDelete);
      } catch (IOException e) {
        throw new IllegalStateException("Unable to delete review comment with id " + idReviewToDelete, e);
      }
    }
  }

  public void addGlobalComment(String comment) {
    try {
      pr.comment(comment);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to comment the pull request", e);
    }
  }

  public void createOrUpdateSonarQubeStatus(GHCommitState status, String statusDescription) {
    try {
      ghRepo.createCommitStatus(pr.getHead().getSha(), status, null, statusDescription, "sonarqube");
    } catch (IOException e) {
      throw new IllegalStateException("Unable to update commit status", e);
    }
  }
}
