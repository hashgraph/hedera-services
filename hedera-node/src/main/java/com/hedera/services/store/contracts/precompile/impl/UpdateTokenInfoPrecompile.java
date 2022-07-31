package com.hedera.services.store.contracts.precompile.impl;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.contracts.sources.EvmSigsVerifier;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.InfrastructureFactory;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.util.function.UnaryOperator;

public class UpdateTokenInfoPrecompile extends AbstractWritePrecompile {
    private HederaTokenStore hederaTokenStore;

    public UpdateTokenInfoPrecompile(
            WorldLedgers ledgers,
            DecodingFacade decoder,
            EvmSigsVerifier sigsVerifier,
            SideEffectsTracker sideEffectsTracker,
            SyntheticTxnFactory syntheticTxnFactory,
            InfrastructureFactory infrastructureFactory,
            PrecompilePricingUtils precompilePricingUtils) {
        super(
                ledgers,
                decoder,
                sideEffectsTracker,
                syntheticTxnFactory,
                infrastructureFactory,
                precompilePricingUtils);
    }

    private void initializeHederaTokenStore() {
        hederaTokenStore =
                infrastructureFactory.newHederaTokenStore(
                        sideEffects, ledgers.tokens(), ledgers.nfts(), ledgers.tokenRels());
    }

    @Override
    public TransactionBody.Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver) {
        final var updateOp = decoder.decodeUpdateTokenInfo(input, aliasResolver);
        transactionBody = syntheticTxnFactory.createUpdateTokenInfo(updateOp);
        initializeHederaTokenStore();
        return transactionBody;
    }

    @Override
    public long getMinimumFeeInTinybars(Timestamp consensusTime) {
        return 0;
    }

    @Override
    public void run(MessageFrame frame) {}
}
