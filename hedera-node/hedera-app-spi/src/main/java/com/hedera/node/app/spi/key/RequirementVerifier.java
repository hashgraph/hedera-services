// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.spi.key;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Verifies a complete authorization requirement, including its identity and any alternatives.
 * Unlike a primitive-key predicate, this callback is invoked once for the whole requirement.
 */
@FunctionalInterface
public interface RequirementVerifier {
    boolean test(@NonNull KeyRequirement requirement);
}
