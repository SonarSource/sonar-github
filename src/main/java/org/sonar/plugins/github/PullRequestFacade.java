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

import java.io.BufferedReader;
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
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHCommitStatus;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestFileDetail;
import org.kohsuke.github.GHPullRequestReviewComment;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.sonar.api.batch.BatchSide;
import org.sonar.api.batch.InstantiationStrategy;
import org.sonar.api.batch.fs.InputComponent;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.InputPath;
import org.sonar.api.scan.filesystem.PathResolver;
import org.sonar.api.utils.MessageException;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

/**
 * Facade for all WS interaction with GitHub.
 */
@BatchSide
@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
public class PullRequestFacade {

  private static final Logger LOG = Loggers.get(PullRequestFacade.class);

  static final String COMMIT_CONTEXT = "sonarqube";

  private final GitHubPluginConfiguration config;
  private Map<String, Map<Integer, Integer>> patchPositionMappingByFile;
  private Map<String, Map<Integer, GHPullRequestReviewComment>> existingReviewCommentsByLocationByFile = new HashMap<>();
  private GHRepository ghRepo;
  private GHPullRequest pr;
  private Map<Integer, GHPullRequestReviewComment> reviewCommentToBeDeletedById = new HashMap<>();
  private File gitBaseDir;
  private String myself;

  public PullRequestFacade(GitHubPluginConfiguration config) {
    this.config = config;
  }

  public void init(int pullRequestNumber, File projectBaseDir) {
    initGitBaseDir(projectBaseDir);
    try {
      GitHub github = new GitHubBuilder().withEndpoint(config.endpoint()).withOAuthToken(config.oauth()).build();
      setGhRepo(github.getRepository(config.repository()));
      setPr(ghRepo.getPullRequest(pullRequestNumber));
      LOG.info("Starting analysis of pull request: " + pr.getHtmlUrl());
      myself = github.getMyself().getLogin();
      loadExistingReviewComments();
      patchPositionMappingByFile = mapPatchPositionsToLines(pr);
    } catch (IOException e) {
      LOG.debug("Unable to perform GitHub WS operation", e);
      throw MessageException.of("Unable to perform GitHub WS operation: " + e.getMessage());
    }
  }

  void initGitBaseDir(File projectBaseDir) {
    File detectedGitBaseDir = findGitBaseDir(projectBaseDir);
    if (detectedGitBaseDir == null) {
      LOG.debug("Unable to find Git root directory. Is " + projectBaseDir + " part of a Git repository?");
      setGitBaseDir(projectBaseDir);
    } else {
      setGitBaseDir(detectedGitBaseDir);
    }
  }

  void setGhRepo(GHRepository ghRepo) {
    this.ghRepo = ghRepo;
  }

  void setPr(GHPullRequest pr) {
    this.pr = pr;
  }

  public File findGitBaseDir(@Nullable File baseDir) {
    if (baseDir == null) {
      return null;
    }
    if (new File(baseDir, ".git").exists()) {
      return baseDir;
    }
    return findGitBaseDir(baseDir.getParentFile());
  }

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
  private Map<String, Map<Integer, Integer>> mapPatchPositionsToLines(GHPullRequest pr) throws IOException {
    Map<String, Map<Integer, Integer>> result = new HashMap<>();
    for (GHPullRequestFileDetail file : pr.listFiles()) {
      Map<Integer, Integer> patchLocationMapping = new HashMap<>();
      result.put(file.getFilename(), patchLocationMapping);
      if (config.tryReportIssuesInline()) {
        String patch = file.getPatch();
        if (patch == null) {
          continue;
        }
        processPatch(patchLocationMapping, patch);
      }
    }
    return result;
  }

  static void processPatch(Map<Integer, Integer> patchLocationMapping, String patch) throws IOException {
    int currentLine = -1;
    int patchLocation = 0;
    BufferedReader reader = new BufferedReader(new StringReader(patch));
    String line;
    while ((line = reader.readLine()) != null) {
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

  String getPath(InputPath inputPath) {
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

  public void createOrUpdateGlobalComments(@Nullable String markup) {
    try {
      boolean found = findAndDeleteOthers(markup);
      if (markup != null && !found) {
        pr.comment(markup);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Unable to read the pull request comments", e);
    }
  }

  private boolean findAndDeleteOthers(String markup) throws IOException {
    boolean found = false;
    for (GHIssueComment comment : pr.listComments()) {
      if (myself.equals(comment.getUser().getLogin())) {
        if (markup == null || found || !markup.equals(comment.getBody())) {
          comment.delete();
          continue;
        }
        if (markup.equals(comment.getBody())) {
          found = true;
        }
      }
    }
    return found;
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
  public String getGithubUrl(@Nullable InputComponent inputComponent, @Nullable Integer issueLine) {
    if (inputComponent instanceof InputPath) {
      String path = getPath((InputPath) inputComponent);
      return ghRepo.getHtmlUrl().toString() + "/blob/" + pr.getHead().getSha() + "/" + path + (issueLine != null ? ("#L" + issueLine) : "");
    }
    return null;
  }

  @CheckForNull
  GHCommitStatus getCommitStatusForContext(GHPullRequest pr, String context) {
    List<GHCommitStatus> statuses;
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
