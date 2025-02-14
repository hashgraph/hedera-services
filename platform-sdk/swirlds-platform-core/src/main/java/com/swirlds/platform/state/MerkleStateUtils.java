// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.state;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.formatting.TextTable;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.utility.MerkleTreeVisualizer;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * A utility class for the Merkle state.
 */
public class MerkleStateUtils {
    /**
     * Generate a string that describes this state.
     *
     * @param hashDepth the depth of the tree to visit and print
     * @param platformState current platform state
     * @param state current root node state
     *
     */
    @NonNull
    public static String createInfoString(
            int hashDepth,
            @NonNull final PlatformStateAccessor platformState,
            @NonNull final Hash rootHash,
            @NonNull final MerkleNode state) {
        final Hash hashEventsCons = platformState.getLegacyRunningEventHash();

        final ConsensusSnapshot snapshot = platformState.getSnapshot();
        final List<MinimumJudgeInfo> minimumJudgeInfo = snapshot == null ? null : snapshot.getMinimumJudgeInfoList();

        final StringBuilder sb = new StringBuilder();

        new TextTable()
                .setBordersEnabled(false)
                .addRow("Round:", platformState.getRound())
                .addRow("Timestamp:", platformState.getConsensusTimestamp())
                .addRow("Next consensus number:", snapshot == null ? "null" : snapshot.nextConsensusNumber())
                .addRow("Legacy running event hash:", hashEventsCons)
                .addRow("Legacy running event mnemonic:", hashEventsCons == null ? "null" : hashEventsCons.toMnemonic())
                .addRow("Rounds non-ancient:", platformState.getRoundsNonAncient())
                .addRow("Creation version:", platformState.getCreationSoftwareVersion())
                .addRow("Minimum judge hash code:", minimumJudgeInfo == null ? "null" : minimumJudgeInfo.hashCode())
                .addRow("Root hash:", rootHash)
                .addRow("First BR Version:", platformState.getFirstVersionInBirthRoundMode())
                .addRow("Last round before BR:", platformState.getLastRoundBeforeBirthRoundMode())
                .addRow("Lowest Judge Gen before BR", platformState.getLowestJudgeGenerationBeforeBirthRoundMode())
                .render(sb);

        sb.append("\n");
        new MerkleTreeVisualizer(state).setDepth(hashDepth).render(sb);
        return sb.toString();
    }
}
