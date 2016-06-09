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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.data.MapEntry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.kohsuke.github.GHCommitStatus;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedIterable;
import org.mockito.Mockito;
import org.sonar.api.batch.fs.InputPath;
import org.sonar.api.batch.fs.internal.DefaultInputFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class PullRequestFacadeTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void testGetGithubUrl() throws Exception {

    File gitBasedir = temp.newFolder();

    PullRequestFacade facade = new PullRequestFacade(mock(GitHubPluginConfiguration.class));
    facade.setGitBaseDir(gitBasedir);
    GHRepository ghRepo = mock(GHRepository.class);
    when(ghRepo.getHtmlUrl()).thenReturn(new URL("https://github.com/SonarSource/sonar-java"));
    facade.setGhRepo(ghRepo);
    GHPullRequest pr = mock(GHPullRequest.class, withSettings().defaultAnswer(RETURNS_DEEP_STUBS));
    when(pr.getHead().getSha()).thenReturn("abc123");
    facade.setPr(pr);
    InputPath inputPath = mock(InputPath.class);
    when(inputPath.file()).thenReturn(new File(gitBasedir, "src/main/Foo.java"));
    assertThat(facade.getGithubUrl(inputPath, 10)).isEqualTo("https://github.com/SonarSource/sonar-java/blob/abc123/src/main/Foo.java#L10");
  }

  @Test
  public void testPatchLineMapping_some_deleted_lines() throws IOException {
    Map<Integer, Integer> patchLocationMapping = new LinkedHashMap<Integer, Integer>();
    PullRequestFacade
      .processPatch(
        patchLocationMapping,
        "@@ -17,9 +17,6 @@\n  * along with this program; if not, write to the Free Software Foundation,\n  * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.\n  */\n-/**\n- * Deprecated in 4.5.1. JFreechart charts are replaced by Javascript charts.\n- */\n @ParametersAreNonnullByDefault\n package org.sonar.plugins.core.charts;\n ");

    assertThat(patchLocationMapping).containsOnly(MapEntry.entry(17, 1), MapEntry.entry(18, 2), MapEntry.entry(19, 3), MapEntry.entry(20, 7), MapEntry.entry(21, 8),
      MapEntry.entry(22, 9));
  }

  @Test
  public void testPatchLineMapping_some_added_lines() throws IOException {
    Map<Integer, Integer> patchLocationMapping = new LinkedHashMap<Integer, Integer>();
    PullRequestFacade
      .processPatch(
        patchLocationMapping,
        "@@ -24,9 +24,9 @@\n /**\n  * A plugin is a group of extensions. See <code>org.sonar.api.Extension</code> interface to browse\n  * available extension points.\n- * <p/>\n  * <p>The manifest property <code>Plugin-Class</code> must declare the name of the implementation class.\n  * It is automatically set by sonar-packaging-maven-plugin when building plugins.</p>\n+ * <p>Implementation must declare a public constructor with no-parameters.</p>\n  *\n  * @see org.sonar.api.Extension\n  * @since 1.10");

    assertThat(patchLocationMapping).containsOnly(MapEntry.entry(24, 1), MapEntry.entry(25, 2), MapEntry.entry(26, 3), MapEntry.entry(27, 5), MapEntry.entry(28, 6),
      MapEntry.entry(29, 7), MapEntry.entry(30, 8), MapEntry.entry(31, 9), MapEntry.entry(32, 10));
  }

  @Test
  public void testPatchLineMapping_no_newline_at_the_end() throws IOException {
    Map<Integer, Integer> patchLocationMapping = new LinkedHashMap<Integer, Integer>();
    PullRequestFacade
      .processPatch(
        patchLocationMapping,
        "@@ -1 +0,0 @@\n-<fake/>\n\\ No newline at end of file");

    assertThat(patchLocationMapping).isEmpty();
  }

  @Test
  public void testEmptyGetCommitStatusForContext() throws IOException {
    PullRequestFacade facade = new PullRequestFacade(mock(GitHubPluginConfiguration.class));
    GHRepository ghRepo = mock(GHRepository.class);
    PagedIterable<GHCommitStatus> ghCommitStatuses = Mockito.mock(PagedIterable.class);
    GHPullRequest pr = mock(GHPullRequest.class, withSettings().defaultAnswer(RETURNS_DEEP_STUBS));
    when(pr.getRepository()).thenReturn(ghRepo);
    when(pr.getHead().getSha()).thenReturn("abc123");
    when(ghRepo.listCommitStatuses(pr.getHead().getSha())).thenReturn(ghCommitStatuses);
    assertThat(facade.getCommitStatusForContext(pr, PullRequestFacade.COMMIT_CONTEXT)).isNull();
  }

  @Test
  public void testGetCommitStatusForContextWithOneCorrectStatus() throws IOException {
    PullRequestFacade facade = new PullRequestFacade(mock(GitHubPluginConfiguration.class));
    GHRepository ghRepo = mock(GHRepository.class);
    PagedIterable<GHCommitStatus> ghCommitStatuses = Mockito.mock(PagedIterable.class);
    List<GHCommitStatus> ghCommitStatusesList = new ArrayList<>();
    GHCommitStatus ghCommitStatusGHPRHContext = Mockito.mock(GHCommitStatus.class);
    ghCommitStatusesList.add(ghCommitStatusGHPRHContext);
    GHPullRequest pr = mock(GHPullRequest.class, withSettings().defaultAnswer(RETURNS_DEEP_STUBS));
    when(pr.getRepository()).thenReturn(ghRepo);
    when(pr.getHead().getSha()).thenReturn("abc123");
    when(ghRepo.listCommitStatuses(pr.getHead().getSha())).thenReturn(ghCommitStatuses);
    when(ghCommitStatuses.asList()).thenReturn(ghCommitStatusesList);
    when(ghCommitStatusGHPRHContext.getContext()).thenReturn(PullRequestFacade.COMMIT_CONTEXT);
    assertThat(facade.getCommitStatusForContext(pr, PullRequestFacade.COMMIT_CONTEXT).getContext()).isEqualTo(PullRequestFacade.COMMIT_CONTEXT);
  }

  @Test
  public void testInitGitBaseDirNotFound() throws Exception {
    PullRequestFacade facade = new PullRequestFacade(mock(GitHubPluginConfiguration.class));
    File projectBaseDir = temp.newFolder();
    facade.initGitBaseDir(projectBaseDir);
    assertThat(facade.getPath(new DefaultInputFile("foo", "src/main/java/Foo.java").setModuleBaseDir(projectBaseDir.toPath()))).isEqualTo("src/main/java/Foo.java");
  }

  @Test
  public void testInitGitBaseDir() throws Exception {
    PullRequestFacade facade = new PullRequestFacade(mock(GitHubPluginConfiguration.class));
    File gitBaseDir = temp.newFolder();
    Files.createDirectory(gitBaseDir.toPath().resolve(".git"));
    File projectBaseDir = new File(gitBaseDir, "myProject");
    facade.initGitBaseDir(projectBaseDir);
    assertThat(facade.getPath(new DefaultInputFile("foo", "src/main/java/Foo.java").setModuleBaseDir(projectBaseDir.toPath()))).isEqualTo("myProject/src/main/java/Foo.java");
  }
}
