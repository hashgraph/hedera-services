//package com.hedera.evm.store.contracts.precompile.impl;
//
//import com.hedera.evm.store.contracts.precompile.Precompile;
//import com.hedera.evm.store.contracts.precompile.utils.PrecompilePricingUtils;
//import com.hedera.evm.utils.accessors.TxnAccessor;
//import com.hederahashgraph.api.proto.java.TransactionBody;
//
//public abstract class AbstractWritePrecompile implements Precompile {
//    protected static final String FAILURE_MESSAGE = "Invalid full prefix for %s precompile!";
//    protected final DecodingFacade decoder;
//    protected final SideEffectsTracker sideEffects;
//    protected final SyntheticTxnFactory syntheticTxnFactory;
//    protected final InfrastructureFactory infrastructureFactory;
//    protected final PrecompilePricingUtils pricingUtils;
//    protected TransactionBody.Builder transactionBody;
//
//    protected AbstractWritePrecompile(
//            final DecodingFacade decoder,
//            final SideEffectsTracker sideEffects,
//            final SyntheticTxnFactory syntheticTxnFactory,
//            final InfrastructureFactory infrastructureFactory,
//            final PrecompilePricingUtils pricingUtils) {
//        this.decoder = decoder;
//        this.sideEffects = sideEffects;
//        this.syntheticTxnFactory = syntheticTxnFactory;
//        this.infrastructureFactory = infrastructureFactory;
//        this.pricingUtils = pricingUtils;
//    }
//
//    @Override
//    public long getGasRequirement(long blockTimestamp) {
//        return pricingUtils.computeGasRequirement(blockTimestamp, this, transactionBody);
//    }
//}
