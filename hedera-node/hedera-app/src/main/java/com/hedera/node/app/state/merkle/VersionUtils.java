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

package com.hedera.node.app.state.merkle;

import static com.hedera.hapi.util.HapiUtils.SEMANTIC_VERSION_COMPARATOR;

import com.hedera.hapi.node.base.SemanticVersion;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * A utility class for version comparisons.
 */
public class VersionUtils {
    private VersionUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    /**
     * Determines whether these two version are equal to each other. Both are equal if they are both
     * null, or have the same version number.
     *
     * @param a The first arg
     * @param b The second arg
     * @return true if both are null, or if both have the same version number
     */
    public static boolean isSameVersion(@Nullable final SemanticVersion a, @Nullable final SemanticVersion b) {
        return (a == null && b == null) || (a != null && b != null && SEMANTIC_VERSION_COMPARATOR.compare(a, b) == 0);
    }

    /**
     * Determines if the two arguments are in the proper order, such that the first argument is
     * strictly lower than the second argument. If they are the same, we return false.
     *
     * @param maybeBefore The version we hope comes before {@code maybeAfter}
     * @param maybeAfter The version we hope comes after {@code maybeBefore}
     * @return True if, and only if, {@code maybeBefore} is a lower version number than {@code
     * maybeAfter}.
     */
    public static boolean isSoOrdered(
            @Nullable final SemanticVersion maybeBefore, @Nullable final SemanticVersion maybeAfter) {
        if (maybeAfter == null) {
            return false;
        }

        // If they are the same version, then we must fail.
        if (isSameVersion(maybeBefore, maybeAfter)) {
            return false;
        }

        // If the first argument is null, then the second argument always
        // comes later (since it must be non-null, or else isSameVersion
        // would have caught it).
        if (maybeBefore == null) {
            return true;
        }

        // If the comparison yields the first argument as being before
        // or matching the second argument, then we return true because
        // the condition we're testing for holds.
        return SEMANTIC_VERSION_COMPARATOR.compare(maybeBefore, maybeAfter) < 0;
    }
}
