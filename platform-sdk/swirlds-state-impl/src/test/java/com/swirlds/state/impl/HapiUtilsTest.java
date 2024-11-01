/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.state.impl;

import static com.swirlds.state.merkle.HapiUtils.SEMANTIC_VERSION_COMPARATOR;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.base.SemanticVersion;
import org.junit.jupiter.api.Test;

class HapiUtilsTest {
    @Test
    void nonAlphaPreReleasePartsComeAfterAnyAlpha() {
        final var alphaVersion = SemanticVersion.newBuilder()
                .major(1)
                .minor(2)
                .patch(3)
                .pre("alpha.4")
                .build();
        final var nonAlphaVersion = SemanticVersion.newBuilder()
                .major(1)
                .minor(2)
                .patch(3)
                .pre("abcdefg")
                .build();
        assertThat(SEMANTIC_VERSION_COMPARATOR.compare(alphaVersion, nonAlphaVersion))
                .isLessThan(0);
    }

    @Test
    void nonNumericBuildPartsHaveNoEffectOnOrdering() {
        final var abcBuild = SemanticVersion.newBuilder()
                .major(1)
                .minor(2)
                .patch(3)
                .pre("")
                .build("abc")
                .build();
        final var defBuild = SemanticVersion.newBuilder()
                .major(1)
                .minor(2)
                .patch(3)
                .pre("")
                .build("def")
                .build();
        assertThat(SEMANTIC_VERSION_COMPARATOR.compare(abcBuild, defBuild)).isEqualTo(0);
    }

    @Test
    void numericBuildPartsHaveEffectOnOrdering() {
        final var zeroBuild = SemanticVersion.newBuilder()
                .major(1)
                .minor(2)
                .patch(3)
                .pre("")
                .build("0")
                .build();
        final var oneBuild = SemanticVersion.newBuilder()
                .major(1)
                .minor(2)
                .patch(3)
                .pre("")
                .build("1")
                .build();
        assertThat(SEMANTIC_VERSION_COMPARATOR.compare(zeroBuild, oneBuild)).isLessThan(0);
    }
}
