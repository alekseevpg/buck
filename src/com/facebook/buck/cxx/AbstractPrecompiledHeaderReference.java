/*
 * Copyright 2016-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.cxx;

import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildTargetSourcePath;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.util.immutables.BuckStyleTuple;
import com.google.common.collect.ImmutableList;

import org.immutables.value.Value;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Tuple containing a {@link SourcePath} along with a way of retrieving dependencies for that
 * {@code SourcePath}.
 *
 * Users of precompiled headers need to be able to provide the dependencies in order to work
 * correctly with DependencyFileRuleKey, as files included via precompiled headers are not emitted
 * by the compiler in the dep file.
 *
 * @see com.facebook.buck.rules.keys.SupportsDependencyFileRuleKey
 */
@Value.Immutable
@BuckStyleTuple
abstract class AbstractPrecompiledHeaderReference {

  public abstract CxxPrecompiledHeader getPrecompiledHeader();

  @Value.Derived
  public SourcePath getSourcePath() {
    return new BuildTargetSourcePath(getPrecompiledHeader().getBuildTarget());
  }

  public ImmutableList<Path> readDepFileLines(BuildContext buildContext)
    throws IOException, Depfiles.HeaderVerificationException {
    return getPrecompiledHeader().readDepFileLines(buildContext);
  }
}
