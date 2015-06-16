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
package org.sonar.plugins.github;

import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.io.IOUtils;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHCommitStatus;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestFileDetail;
import org.kohsuke.github.GHPullRequestReviewComment;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.BatchComponent;
import org.sonar.api.batch.InstantiationStrategy;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.InputPath;
import org.sonar.api.scan.filesystem.PathResolver;

/**
 * Facade for all WS interaction with GitHub.
 */
@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
public class PullRequestFacade implements BatchComponent {

  private static final Logger LOG = LoggerFactory.getLogger(PullRequestFacade.class);

  @VisibleForTesting
  static final String COMMIT_CONTEXT = "sonarqube";

  private final GitHubPluginConfiguration config;
  private Map<String, Map<Integer, Integer>> patchPositionMappingByFile;
  private Map<String, Map<Integer, GHPullRequestReviewComment>> existingReviewCommentsByLocationByFile;
  private GHRepository ghRepo;
  private GHPullRequest pr;
  private Map<Integer, GHPullRequestReviewComment> reviewCommentToBeDeletedById = new HashMap<>();
  private File gitBaseDir;
  private String myself;

  public PullRequestFacade(GitHubPluginConfiguration config) {
    this.config = config;
  }

  public void init(int pullRequestNumber, File projectBaseDir) {
    if (findGitBaseDir(projectBaseDir) == null) {
      throw new IllegalStateException("Unable to find Git root directory. Is " + projectBaseDir + " part of a Git repository?");
    }
    try {
      GitHub github = new GitHubBuilder().withEndpoint(config.endpoint()).withOAuthToken(config.oauth(), config.login()).build();
      setGhRepo(github.getRepository(config.repository()));
      setPr(ghRepo.getPullRequest(pullRequestNumber));
      LOG.info("Starting analysis of pull request: " + pr.getHtmlUrl());
      myself = github.getMyself().getLogin();
      loadExistingReviewComments();
      patchPositionMappingByFile = mapPatchPositionsToLines(pr);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to perform GitHub WS operation", e);
    }
  }

  @VisibleForTesting
  void setGhRepo(GHRepository ghRepo) {
    this.ghRepo = ghRepo;
  }

  @VisibleForTesting
  void setPr(GHPullRequest pr) {
    this.pr = pr;
  }

  public File findGitBaseDir(@Nullable File baseDir) {
    if (baseDir == null) {
      return null;
    }
    if (new File(baseDir, ".git").exists()) {
      setGitBaseDir(baseDir);
      return baseDir;
    }
    return findGitBaseDir(baseDir.getParentFile());
  }

  @VisibleForTesting
  void setGitBaseDir(File gitBaseDir) {
    this.gitBaseDir = gitBaseDir;
  }

  /**
   * Load all previous comments made by provided github account.
   */
  private void loadExistingReviewComments() throws IOException {
    for (GHPullRequestReviewComment comment : pr.listReviewComments()) {
      if (!myself.equals(comment.getUser().getLogin())) {
        // Ignore comments from other users
        continue;
      }
      if (!existingReviewCommentsByLocationByFile.containsKey(comment.getPath())) {
        existingReviewCommentsByLocationByFile.put(comment.getPath(), new HashMap<Integer, GHPullRequestReviewComment>());
      }
      // By default all previous comments will be marked for deletion
      reviewCommentToBeDeletedById.put(comment.getId(), comment);
      existingReviewCommentsByLocationByFile.get(comment.getPath()).put(comment.getPosition(), comment);
    }
  }

  /**
   * GitHub expect review comments to be added on "patch lines" (aka position) but not on file lines. 
   * So we have to iterate over each patch and compute corresponding file line in order to later map issues to the correct position.
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
      processPatch(patchLocationMapping, patch);
    }
    return patchPositionMappingByFile;
  }

  @VisibleForTesting
  static void processPatch(Map<Integer, Integer> patchLocationMapping, String patch) throws IOException {
    int currentLine = -1;
    int patchLocation = 0;
    for (String line : IOUtils.readLines(new StringReader(patch))) {
      if (line.startsWith("@")) {
        // http://en.wikipedia.org/wiki/Diff_utility#Unified_format
        Matcher matcher = Pattern.compile("@@\\p{IsWhite_Space}-[0-9]+(?:,[0-9]+)?\\p{IsWhite_Space}\\+([0-9]+)(?:,[0-9]+)?\\p{IsWhite_Space}@@.*").matcher(line);
        if (!matcher.matches()) {
          throw new IllegalStateException("Unable to parse patch line " + line + "\nFull patch: \n" + patch);
        }
        currentLine = Integer.parseInt(matcher.group(1));
      } else if (line.startsWith("-")) {
        // Skip removed lines
      } else if (line.startsWith("+") || line.startsWith(" ")) {
        // Count added and unmodified lines
        patchLocationMapping.put(currentLine, patchLocation);
        currentLine++;
      } else if (line.startsWith("\\")) {
        // I'm only aware of \ No newline at end of file
        // Ignore
      }
      patchLocation++;
    }
  }

  private String getPath(InputPath inputPath) {
    return new PathResolver().relativePath(gitBaseDir, inputPath.file());
  }

  /**
   * Test if the P/R contains the provided file path (ie this file was added/modified/updated)
   */
  public boolean hasFile(InputFile inputFile) {
    return patchPositionMappingByFile.containsKey(getPath(inputFile));
  }

  /**
   * Test if the P/R contains the provided line for the file path (ie this line is "visible" in diff)
   */
  public boolean hasFileLine(InputFile inputFile, int line) {
    return patchPositionMappingByFile.get(getPath(inputFile)).containsKey(line);
  }

  public void createOrUpdateReviewComment(InputFile inputFile, Integer line, String body) {
    String fullpath = getPath(inputFile);
    Integer lineInPatch = patchPositionMappingByFile.get(fullpath).get(line);
    try {
      if (existingReviewCommentsByLocationByFile.containsKey(fullpath) && existingReviewCommentsByLocationByFile.get(fullpath).containsKey(lineInPatch)) {
        GHPullRequestReviewComment existingReview = existingReviewCommentsByLocationByFile.get(fullpath).get(lineInPatch);
        if (!existingReview.getBody().equals(body)) {
          existingReview.update(body);
        }
        reviewCommentToBeDeletedById.remove(existingReview.getId());
      } else {
        pr.createReviewComment(body, pr.getHead().getSha(), fullpath, lineInPatch);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Unable to create or update review comment in file " + fullpath + " at line " + line, e);
    }

  }

  public void deleteOutdatedComments() {
    for (GHPullRequestReviewComment reviewToDelete : reviewCommentToBeDeletedById.values()) {
      try {
        reviewToDelete.delete();
      } catch (IOException e) {
        throw new IllegalStateException("Unable to delete review comment with id " + reviewToDelete.getId(), e);
      }
    }
  }

  public void removePreviousGlobalComments() {
    try {
      for (GHIssueComment comment : pr.listComments()) {
        if (myself.equals(comment.getUser().getLogin())) {
          comment.delete();
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("Unable to comment the pull request", e);
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
      // Copy previous targetUrl in case it was set by an external system (like the CI job).
      String targetUrl = null;
      GHCommitStatus lastStatus = getCommitStatusForContext(pr, COMMIT_CONTEXT);
      if (lastStatus != null) {
        targetUrl = lastStatus.getTargetUrl();
      }
      ghRepo.createCommitStatus(pr.getHead().getSha(), status, targetUrl, statusDescription, COMMIT_CONTEXT);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to update commit status", e);
    }
  }

  @CheckForNull
  public String getGithubUrl(@Nullable InputPath inputPath, @Nullable Integer issueLine) {
    if (inputPath != null) {
      String path = getPath(inputPath);
      return ghRepo.getHtmlUrl().toString() + "/blob/" + pr.getHead().getSha() + "/" + path + (issueLine != null ? ("#L" + issueLine) : "");
    }
    return null;
  }

  @VisibleForTesting
  @CheckForNull
  GHCommitStatus getCommitStatusForContext(GHPullRequest pr, String context) {
    List<GHCommitStatus> statuses = null;
    try {
      statuses = pr.getRepository().listCommitStatuses(pr.getHead().getSha()).asList();
    } catch (IOException e) {
      throw new IllegalStateException("Unable to retrieve commit statuses.", e);
    }
    for (GHCommitStatus status : statuses) {
      if (context.equals(status.getContext())) {
        return status;
      }
    }
    return null;
  }
}
