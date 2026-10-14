// SPDX-License-Identifier: Apache-2.0
package com.hedera.services.bdd.junit.support;

import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_ACCOUNTS;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.output.BlockHeader;
import com.hedera.hapi.block.stream.output.MapChangeKey;
import com.hedera.hapi.block.stream.output.MapChangeValue;
import com.hedera.hapi.block.stream.output.MapUpdateChange;
import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.hapi.utils.blocks.StateChangeDeltas;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FullStateValuesTest {
    private static final int ACCOUNTS = STATE_ID_ACCOUNTS.protoOrdinal();
    private static final AccountID KEY = AccountID.newBuilder().accountNum(2).build();
    private static final Account FIRST =
            Account.newBuilder().memo("m".repeat(100)).tinybarBalance(1).build();
    private static final Account SECOND = FIRST.copyBuilder().tinybarBalance(2).build();
    private static final Account THIRD = SECOND.copyBuilder().tinybarBalance(3).build();

    @TempDir
    Path dir;

    @Test
    void expandsPartialsAfterReplayingEarlierBlocksFromDisk() throws Exception {
        final var genesis = written(block(0, full(FIRST)));
        final var unchanged = written(block(1, null));
        final var partial = written(block(2, partial(FIRST, SECOND)));
        final var later = written(block(3, partial(SECOND, THIRD)));
        final var subject = new FullStateValues();

        assertThat(subject.expand(dir, genesis)).isSameAs(genesis);
        assertThat(subject.expand(dir, unchanged)).isSameAs(unchanged);
        assertThat(accountIn(subject.expand(dir, partial))).isEqualTo(full(SECOND));
        assertThat(accountIn(subject.expand(dir, later))).isEqualTo(full(THIRD));
        // A block it has already passed can no longer be expanded
        assertThat(subject.expand(dir, partial)).isSameAs(partial);
    }

    @Test
    void passesBlocksOnAsWrittenOnceExpansionFails() throws Exception {
        // Without block 0 on disk there is no baseline for the partial in block 1
        final var partial = written(block(1, partial(FIRST, SECOND)));
        final var later = written(block(2, partial(SECOND, THIRD)));
        final var subject = new FullStateValues();

        assertThat(subject.expand(dir, partial)).isSameAs(partial);
        assertThat(subject.expand(dir, later)).isSameAs(later);
    }

    private Block written(final Block block) throws Exception {
        final var name = String.format(
                "%036d", block.items().getFirst().blockHeaderOrThrow().number());
        Files.write(dir.resolve(name + ".blk"), Block.PROTOBUF.toBytes(block).toByteArray());
        Files.createFile(dir.resolve(name + ".mf"));
        return block;
    }

    private static Block block(final long number, final MapUpdateChange update) {
        final var header = BlockItem.newBuilder()
                .blockHeader(BlockHeader.newBuilder().number(number))
                .build();
        if (update == null) {
            return Block.newBuilder().items(header).build();
        }
        final var changes = BlockItem.newBuilder()
                .stateChanges(StateChanges.newBuilder()
                        .stateChanges(StateChange.newBuilder()
                                .stateId(ACCOUNTS)
                                .mapUpdate(update)
                                .build()))
                .build();
        return Block.newBuilder().items(header, changes).build();
    }

    private static MapUpdateChange full(final Account account) {
        return MapUpdateChange.newBuilder()
                .key(MapChangeKey.newBuilder().accountIdKey(KEY))
                .value(MapChangeValue.newBuilder().accountValue(account))
                .build();
    }

    private static MapUpdateChange partial(final Account prior, final Account next) {
        final var update = StateChangeDeltas.encodeMap(ACCOUNTS, prior, full(next));
        assertThat(update.partial()).isTrue();
        return update;
    }

    private static MapUpdateChange accountIn(final Block block) {
        return block.items()
                .getLast()
                .stateChangesOrThrow()
                .stateChanges()
                .getFirst()
                .mapUpdateOrThrow();
    }
}
