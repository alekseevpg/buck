/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.facebook.buck.cxx.toolchain;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.facebook.buck.apple.clang.ModuleMap;
import com.facebook.buck.core.build.buildable.context.FakeBuildableContext;
import com.facebook.buck.core.build.context.BuildContext;
import com.facebook.buck.core.build.context.FakeBuildContext;
import com.facebook.buck.core.cell.name.CanonicalCellName;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.BuildTargetFactory;
import com.facebook.buck.core.model.impl.BuildTargetPaths;
import com.facebook.buck.core.model.targetgraph.TargetGraph;
import com.facebook.buck.core.rulekey.RuleKey;
import com.facebook.buck.core.rules.BuildRuleResolver;
import com.facebook.buck.core.rules.resolver.impl.TestActionGraphBuilder;
import com.facebook.buck.core.sourcepath.FakeSourcePath;
import com.facebook.buck.core.sourcepath.PathSourcePath;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolverAdapter;
import com.facebook.buck.io.BuildCellRelativePath;
import com.facebook.buck.io.file.MorePaths;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.TestProjectFilesystems;
import com.facebook.buck.io.filesystem.impl.FakeProjectFilesystem;
import com.facebook.buck.rules.keys.TestDefaultRuleKeyFactory;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.MakeCleanDirectoryStep;
import com.facebook.buck.step.fs.SymlinkMapsPaths;
import com.facebook.buck.step.fs.SymlinkTreeMergeStep;
import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.util.cache.FileHashCacheMode;
import com.facebook.buck.util.cache.impl.DefaultFileHashCache;
import com.facebook.buck.util.cache.impl.StackedFileHashCache;
import com.facebook.buck.util.hashing.FileHashLoader;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class HeaderSymlinkTreeWithModuleMapTest {

  @Rule public final TemporaryPaths tmpDir = new TemporaryPaths();

  private ProjectFilesystem projectFilesystem;
  private BuildTarget buildTarget;
  private HeaderSymlinkTreeWithModuleMap symlinkTreeBuildRule;
  private ImmutableMap<Path, SourcePath> links;
  private RelPath symlinkTreeRoot;
  private BuildRuleResolver ruleResolver;
  private SourcePathResolverAdapter resolver;

  @Before
  public void setUp() throws Exception {
    projectFilesystem = new FakeProjectFilesystem(CanonicalCellName.rootCell(), tmpDir.getRoot());

    // Create a build target to use when building the symlink tree.
    buildTarget = BuildTargetFactory.newInstance("//test:test");

    // Get the first file we're symlinking
    Path link1 = Paths.get("SomeModule", "SomeModule.h");
    AbsPath file1 = tmpDir.newFile();
    Files.write(file1.getPath(), "hello world".getBytes(StandardCharsets.UTF_8));

    // Get the second file we're symlinking
    Path link2 = Paths.get("SomeModule", "Header.h");
    AbsPath file2 = tmpDir.newFile();
    Files.write(file2.getPath(), "hello world".getBytes(StandardCharsets.UTF_8));

    // Setup the map representing the link tree.
    links =
        ImmutableMap.of(
            link1,
            PathSourcePath.of(projectFilesystem, MorePaths.relativize(tmpDir.getRoot(), file1)),
            link2,
            PathSourcePath.of(projectFilesystem, MorePaths.relativize(tmpDir.getRoot(), file2)));

    // The output path used by the buildable for the link tree.
    symlinkTreeRoot =
        BuildTargetPaths.getGenPath(projectFilesystem, buildTarget, "%s/symlink-tree-root");

    ruleResolver = new TestActionGraphBuilder(TargetGraph.EMPTY);
    resolver = ruleResolver.getSourcePathResolver();

    // Setup the symlink tree buildable.
    symlinkTreeBuildRule =
        HeaderSymlinkTreeWithModuleMap.create(
            buildTarget, projectFilesystem, symlinkTreeRoot.getPath(), links, Optional.empty());
  }

  @Test
  public void testSymlinkTreeBuildSteps() {
    BuildContext buildContext = FakeBuildContext.withSourcePathResolver(resolver);
    FakeBuildableContext buildableContext = new FakeBuildableContext();

    ImmutableList<Step> expectedBuildSteps =
        new ImmutableList.Builder<Step>()
            .addAll(
                MakeCleanDirectoryStep.of(
                    BuildCellRelativePath.fromCellRelativePath(
                        buildContext.getBuildCellRootPath(), projectFilesystem, symlinkTreeRoot)))
            .add(
                new SymlinkTreeMergeStep(
                    "cxx_header",
                    projectFilesystem,
                    symlinkTreeRoot.getPath(),
                    new SymlinkMapsPaths(
                        resolver.getMappedPaths(links).entrySet().stream()
                            .collect(
                                ImmutableMap.toImmutableMap(
                                    Map.Entry::getKey, e -> e.getValue().getPath()))),
                    (fs, p) -> false))
            .add(
                new ModuleMapStep(
                    projectFilesystem,
                    BuildTargetPaths.getGenPath(
                            projectFilesystem, buildTarget, "%s/SomeModule/module.modulemap")
                        .getPath(),
                    ModuleMap.create("SomeModule", ModuleMap.SwiftMode.NO_SWIFT, links.keySet())))
            .build();
    ImmutableList<Step> actualBuildSteps =
        symlinkTreeBuildRule.getBuildSteps(buildContext, buildableContext);
    assertEquals(expectedBuildSteps, actualBuildSteps.subList(1, actualBuildSteps.size()));
  }

  @Test
  public void testRecognisesSwiftHeader() {
    BuildContext buildContext = FakeBuildContext.withSourcePathResolver(resolver);
    FakeBuildableContext buildableContext = new FakeBuildableContext();

    HeaderSymlinkTreeWithModuleMap linksWithSwiftHeader =
        HeaderSymlinkTreeWithModuleMap.create(
            buildTarget,
            projectFilesystem,
            symlinkTreeRoot.getPath(),
            new ImmutableMap.Builder<Path, SourcePath>()
                .putAll(links)
                .put(Paths.get("SomeModule", "SomeModule-Swift.h"), FakeSourcePath.of("SomeModule"))
                .build(),
            Optional.empty());

    ImmutableList<Step> actualBuildSteps =
        linksWithSwiftHeader.getBuildSteps(buildContext, buildableContext);

    ModuleMapStep moduleMapStep =
        new ModuleMapStep(
            projectFilesystem,
            BuildTargetPaths.getGenPath(
                    projectFilesystem, buildTarget, "%s/SomeModule/module.modulemap")
                .getPath(),
            ModuleMap.create(
                "SomeModule", ModuleMap.SwiftMode.INCLUDE_SWIFT_HEADER, links.keySet()));
    assertThat(actualBuildSteps, hasItem(moduleMapStep));
  }

  @Test
  public void testSymlinkTreeRuleKeyChangesIfModuleNameChanges() throws Exception {
    AbsPath aFile = tmpDir.newFile();
    Files.write(aFile.getPath(), "hello world".getBytes(StandardCharsets.UTF_8));
    HeaderSymlinkTreeWithModuleMap modifiedSymlinkTreeBuildRule =
        HeaderSymlinkTreeWithModuleMap.create(
            buildTarget,
            projectFilesystem,
            symlinkTreeRoot.getPath(),
            ImmutableMap.of(
                Paths.get("OtherModule", "Header.h"),
                PathSourcePath.of(
                    projectFilesystem, MorePaths.relativize(tmpDir.getRoot(), aFile))),
            Optional.empty());

    // Calculate their rule keys and verify they're different.
    DefaultFileHashCache hashCache =
        DefaultFileHashCache.createDefaultFileHashCache(
            TestProjectFilesystems.createProjectFilesystem(tmpDir.getRoot()),
            FileHashCacheMode.DEFAULT);
    FileHashLoader hashLoader = new StackedFileHashCache(ImmutableList.of(hashCache));
    RuleKey key1 =
        new TestDefaultRuleKeyFactory(hashLoader, ruleResolver).build(symlinkTreeBuildRule);
    RuleKey key2 =
        new TestDefaultRuleKeyFactory(hashLoader, ruleResolver).build(modifiedSymlinkTreeBuildRule);
    assertNotEquals(key1, key2);
  }
}
