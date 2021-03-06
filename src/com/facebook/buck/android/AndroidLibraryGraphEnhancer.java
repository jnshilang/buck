/*
 * Copyright 2013-present Facebook, Inc.
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

package com.facebook.buck.android;

import static com.facebook.buck.android.UnsortedAndroidResourceDeps.Callback;

import com.facebook.buck.java.JavacOptions;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.Flavor;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.BuildRuleType;
import com.facebook.buck.rules.Buildables;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;

public class AndroidLibraryGraphEnhancer {

  private static final Flavor DUMMY_R_DOT_JAVA_FLAVOR = new Flavor("dummy_r_dot_java");

  private final BuildTarget dummyRDotJavaBuildTarget;
  private final BuildRuleParams originalBuildRuleParams;
  private final JavacOptions javacOptions;

  public AndroidLibraryGraphEnhancer(
      BuildTarget buildTarget,
      BuildRuleParams buildRuleParams,
      JavacOptions javacOptions) {
    Preconditions.checkNotNull(buildTarget);
    this.dummyRDotJavaBuildTarget = new BuildTarget(
        buildTarget.getBaseName(),
        buildTarget.getShortName(),
        DUMMY_R_DOT_JAVA_FLAVOR);
    this.originalBuildRuleParams = Preconditions.checkNotNull(buildRuleParams);
    // Override javacoptions because DummyRDotJava doesn't require annotation processing
    // params data and more importantly, because javacoptions' rule key is not available when
    // DummyRDotJava is built.
    Preconditions.checkNotNull(javacOptions);
    this.javacOptions = JavacOptions.builder(JavacOptions.DEFAULTS)
        .setJavaCompilerEnviornment(javacOptions.getJavaCompilerEnvironment())
        .build();
  }

  public Result createBuildableForAndroidResources(
      BuildRuleResolver ruleResolver,
      boolean createBuildableIfEmptyDeps) {
    ImmutableSortedSet<BuildRule> originalDeps = originalBuildRuleParams.getDeps();
    ImmutableSet<HasAndroidResourceDeps> androidResourceDeps =
        UnsortedAndroidResourceDeps.createFrom(originalDeps, Optional.<Callback>absent())
            .getResourceDeps();

    if (androidResourceDeps.isEmpty() && !createBuildableIfEmptyDeps) {
      return new Result(originalBuildRuleParams, Optional.<DummyRDotJava>absent());
    }

    // The androidResourceDeps may contain Buildables, but we need the actual BuildRules. Since this
    // is going to be used to modify the build graph, we can't just wrap the buildables. Fortunately
    // we know that the buildables come from the originalDeps.
    ImmutableSortedSet.Builder<BuildRule> actualDeps = ImmutableSortedSet.naturalOrder();
    for (HasAndroidResourceDeps dep : androidResourceDeps) {
      // If this ever returns null, something has gone horrifically awry.
      actualDeps.add(ruleResolver.get(dep.getBuildTarget()));
    }

    DummyRDotJava dummyRDotJava = new DummyRDotJava(
        androidResourceDeps,
        dummyRDotJavaBuildTarget,
        javacOptions);
    BuildRule dummyRDotJavaBuildRule = Buildables.createRuleFromBuildable(
        dummyRDotJava,
        BuildRuleType.DUMMY_R_DOT_JAVA,
        dummyRDotJavaBuildTarget,
        actualDeps.build(),
        originalBuildRuleParams);
    ruleResolver.addToIndex(dummyRDotJavaBuildTarget, dummyRDotJavaBuildRule);

    ImmutableSortedSet<BuildRule> totalDeps = ImmutableSortedSet.<BuildRule>naturalOrder()
        .addAll(originalDeps)
        .add(dummyRDotJavaBuildRule)
        .build();

    BuildRuleParams newBuildRuleParams = originalBuildRuleParams.copyWithChangedDeps(totalDeps);

    return new Result(newBuildRuleParams, Optional.of(dummyRDotJava));
  }

  public static class Result {
    private final BuildRuleParams buildRuleParams;
    private final Optional<DummyRDotJava> dummyRDotJava;

    private Result(BuildRuleParams buildRuleParams, Optional<DummyRDotJava> dummyRDotJava) {
      this.buildRuleParams = Preconditions.checkNotNull(buildRuleParams);
      this.dummyRDotJava = Preconditions.checkNotNull(dummyRDotJava);
    }

    public BuildRuleParams getBuildRuleParams() {
      return buildRuleParams;
    }

    public Optional<DummyRDotJava> getOptionalDummyRDotJava() {
      return dummyRDotJava;
    }

  }
}
