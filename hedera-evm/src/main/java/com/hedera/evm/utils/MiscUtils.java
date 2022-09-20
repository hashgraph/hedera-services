package com.hedera.evm.utils;

import com.hedera.evm.exception.UnknownHederaFunctionality;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Function;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusCreateTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusDeleteTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusUpdateTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoAddLiveHash;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoApproveAllowance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDeleteAllowance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDeleteLiveHash;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.EthereumTransaction;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileAppend;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.Freeze;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.NONE;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleSign;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.SystemDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.SystemUndelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAssociateToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDissociateFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFeeScheduleUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGrantKycToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenPause;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenRevokeKycFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnfreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnpause;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.UncheckedSubmit;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.UtilPrng;

public class MiscUtils {
    private static final Logger log = LogManager.getLogger(MiscUtils.class);
    private MiscUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static final Function<TransactionBody, HederaFunctionality> FUNCTION_EXTRACTOR =
            trans -> {
                try {
                    return functionOf(trans);
                } catch (UnknownHederaFunctionality ignore) {
                    return NONE;
                }
            };

    public static HederaFunctionality functionOf(final TransactionBody txn)
            throws UnknownHederaFunctionality {
        if (txn.hasSystemDelete()) {
            return SystemDelete;
        }
        if (txn.hasSystemUndelete()) {
            return SystemUndelete;
        }
        if (txn.hasContractCall()) {
            return ContractCall;
        }
        if (txn.hasContractCreateInstance()) {
            return ContractCreate;
        }
        if (txn.hasContractUpdateInstance()) {
            return ContractUpdate;
        }
        if (txn.hasCryptoAddLiveHash()) {
            return CryptoAddLiveHash;
        }
        if (txn.hasCryptoCreateAccount()) {
            return CryptoCreate;
        }
        if (txn.hasCryptoDelete()) {
            return CryptoDelete;
        }
        if (txn.hasCryptoDeleteLiveHash()) {
            return CryptoDeleteLiveHash;
        }
        if (txn.hasCryptoTransfer()) {
            return CryptoTransfer;
        }
        if (txn.hasCryptoUpdateAccount()) {
            return CryptoUpdate;
        }
        if (txn.hasFileAppend()) {
            return FileAppend;
        }
        if (txn.hasFileCreate()) {
            return FileCreate;
        }
        if (txn.hasFileDelete()) {
            return FileDelete;
        }
        if (txn.hasFileUpdate()) {
            return FileUpdate;
        }
        if (txn.hasContractDeleteInstance()) {
            return ContractDelete;
        }
        if (txn.hasFreeze()) {
            return Freeze;
        }
        if (txn.hasConsensusCreateTopic()) {
            return ConsensusCreateTopic;
        }
        if (txn.hasConsensusUpdateTopic()) {
            return ConsensusUpdateTopic;
        }
        if (txn.hasConsensusDeleteTopic()) {
            return ConsensusDeleteTopic;
        }
        if (txn.hasConsensusSubmitMessage()) {
            return ConsensusSubmitMessage;
        }
        if (txn.hasTokenCreation()) {
            return TokenCreate;
        }
        if (txn.hasTokenFreeze()) {
            return TokenFreezeAccount;
        }
        if (txn.hasTokenUnfreeze()) {
            return TokenUnfreezeAccount;
        }
        if (txn.hasTokenGrantKyc()) {
            return TokenGrantKycToAccount;
        }
        if (txn.hasTokenRevokeKyc()) {
            return TokenRevokeKycFromAccount;
        }
        if (txn.hasTokenDeletion()) {
            return TokenDelete;
        }
        if (txn.hasTokenUpdate()) {
            return TokenUpdate;
        }
        if (txn.hasTokenMint()) {
            return TokenMint;
        }
        if (txn.hasTokenBurn()) {
            return TokenBurn;
        }
        if (txn.hasTokenWipe()) {
            return TokenAccountWipe;
        }
        if (txn.hasTokenAssociate()) {
            return TokenAssociateToAccount;
        }
        if (txn.hasTokenDissociate()) {
            return TokenDissociateFromAccount;
        }
        if (txn.hasTokenFeeScheduleUpdate()) {
            return TokenFeeScheduleUpdate;
        }
        if (txn.hasTokenPause()) {
            return TokenPause;
        }
        if (txn.hasTokenUnpause()) {
            return TokenUnpause;
        }
        if (txn.hasScheduleCreate()) {
            return ScheduleCreate;
        }
        if (txn.hasScheduleSign()) {
            return ScheduleSign;
        }
        if (txn.hasScheduleDelete()) {
            return ScheduleDelete;
        }
        if (txn.hasUncheckedSubmit()) {
            return UncheckedSubmit;
        }
        if (txn.hasCryptoApproveAllowance()) {
            return CryptoApproveAllowance;
        }
        if (txn.hasCryptoDeleteAllowance()) {
            return CryptoDeleteAllowance;
        }
        if (txn.hasEthereumTransaction()) {
            return EthereumTransaction;
        }
        if (txn.hasUtilPrng()) {
            return UtilPrng;
        }
        throw new UnknownHederaFunctionality();
    }
}
