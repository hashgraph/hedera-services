package com.hedera.services.bdd.spec.utilops.embedded;

import com.hedera.hapi.node.state.tss.TssMessageMapKey;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.function.Consumer;

import static com.hedera.node.app.hapi.utils.CommonPbjConverters.toPbj;
import static java.util.Objects.requireNonNull;

public class MutateTssMsgState extends UtilOp {
    private final String sourceRoster;
    private final String targetRoster;
    private final Consumer<TssMessageTransactionBody.Builder> mutation;

    public MutateTssMsgState(final String sourceRoster,
                             final String targetRoster,
                             final Consumer<TssMessageTransactionBody.Builder> mutation) {
        this.sourceRoster = sourceRoster;
        this.targetRoster = targetRoster;
        this.mutation = mutation;
    }

    @Override
    protected boolean submitOp(@NonNull final HapiSpec spec) throws Throwable {
        final var tssMessages = spec.embeddedTssMsgStateOrThrow();
//        final var sourceRoster = spec.setup()
        final var targetRoster = toPbj(TxnUtils.asId("roster", spec));

        var sequenceNumber = 0;
        for(int i = 0; i < 2 ; i++){
            final var tsMessageMapKey = TssMessageMapKey.newBuilder()
//                    .rosterHash(targetRoster)
                    .sequenceNumber(sequenceNumber++)
                    .build();
            final var tssMsgBody = TssMessageTransactionBody.newBuilder()
//                    .sourceRosterHash(targetRoster)
//                    .targetRosterHash(targetRoster)
                    .shareIndex(sequenceNumber)
                    .tssMessage(Bytes.EMPTY)
                    .build();
            tssMessages.put(tsMessageMapKey, tssMsgBody);
        }

        spec.commitEmbeddedState();
        return false;
    }
}
