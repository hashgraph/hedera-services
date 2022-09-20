//package com.hedera.evm.store.contracts.precompile.impl;
//
//import com.hedera.evm.store.contracts.precompile.utils.PrecompilePricingUtils;
//import com.hedera.evm.utils.accessors.TxnAccessor;
//import org.hyperledger.besu.datatypes.Address;
//
//public class TransferPrecompile extends AbstractWritePrecompile{
//    public TransferPrecompile(
//            final DecodingFacade decoder,
//            final HederaStackedWorldStateUpdater updater,
//            final EvmSigsVerifier sigsVerifier,
//            final SideEffectsTracker sideEffects,
//            final SyntheticTxnFactory syntheticTxnFactory,
//            final InfrastructureFactory infrastructureFactory,
//            final PrecompilePricingUtils pricingUtils,
//            final int functionId,
//            final Address senderAddress,
//            final ImpliedTransfersMarshal impliedTransfersMarshal) {
//        super(
//                decoder,
//                sideEffects,
//                syntheticTxnFactory,
//                infrastructureFactory,
//                pricingUtils);
//        this.updater = updater;
//        this.sigsVerifier = sigsVerifier;
//        this.functionId = functionId;
//        this.senderAddress = senderAddress;
//        this.impliedTransfersMarshal = impliedTransfersMarshal;
//    }
//
//    @Override
//    public void addImplicitCostsIn(final TxnAccessor accessor) {
//        if (impliedTransfers != null) {
//            reCalculateXferMeta(accessor, impliedTransfers);
//        }
//    }
//}
