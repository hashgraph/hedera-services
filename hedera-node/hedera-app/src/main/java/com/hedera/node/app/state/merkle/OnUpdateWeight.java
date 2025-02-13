// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.state.merkle;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.state.merkle.MerkleStateRoot;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A callback that is invoked when platform calls updateWeight during upgrade.
 */
public interface OnUpdateWeight {
    void updateWeight(
            @NonNull final MerkleStateRoot state,
            @NonNull AddressBook configAddressBook,
            @NonNull final PlatformContext context);
}
