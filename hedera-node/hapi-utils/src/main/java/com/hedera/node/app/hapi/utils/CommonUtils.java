/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.hapi.utils;

import static com.hedera.node.app.hapi.utils.ByteStringUtils.unwrapUnsafelyIfPossible;
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
import static com.hederahashgraph.api.proto.java.HederaFunctionality.NodeStakeUpdate;
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

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.node.app.hapi.utils.exception.UnknownHederaFunctionality;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody.DataCase;
import com.hederahashgraph.api.proto.java.TransactionOrBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public final class CommonUtils {
    private CommonUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    private static String sha384HashTag = "SHA-384";

    public static String base64encode(final byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static ByteString extractTransactionBodyByteString(final TransactionOrBuilder transaction)
            throws InvalidProtocolBufferException {
        final var signedTransactionBytes = transaction.getSignedTransactionBytes();
        if (!signedTransactionBytes.isEmpty()) {
            return SignedTransaction.parseFrom(signedTransactionBytes).getBodyBytes();
        }
        return transaction.getBodyBytes();
    }

    public static byte[] extractTransactionBodyBytes(final TransactionOrBuilder transaction)
            throws InvalidProtocolBufferException {
        return unwrapUnsafelyIfPossible(extractTransactionBodyByteString(transaction));
    }

    public static TransactionBody extractTransactionBody(final TransactionOrBuilder transaction)
            throws InvalidProtocolBufferException {
        return TransactionBody.parseFrom(extractTransactionBodyByteString(transaction));
    }

    public static SignatureMap extractSignatureMap(final TransactionOrBuilder transaction)
            throws InvalidProtocolBufferException {
        final var signedTransactionBytes = transaction.getSignedTransactionBytes();
        if (!signedTransactionBytes.isEmpty()) {
            return SignedTransaction.parseFrom(signedTransactionBytes).getSigMap();
        }
        return transaction.getSigMap();
    }

    public static byte[] noThrowSha384HashOf(final byte[] byteArray) {
        try {
            return MessageDigest.getInstance(sha384HashTag).digest(byteArray);
        } catch (final NoSuchAlgorithmException fatal) {
            throw new IllegalStateException(fatal);
        }
    }

    public static boolean productWouldOverflow(final long multiplier, final long multiplicand) {
        if (multiplicand == 0) {
            return false;
        }
        final var maxMultiplier = Long.MAX_VALUE / multiplicand;
        return multiplier > maxMultiplier;
    }

    @VisibleForTesting
    static void setSha384HashTag(final String sha384HashTag) {
        CommonUtils.sha384HashTag = sha384HashTag;
    }

    /**
     * check TransactionBody and return the HederaFunctionality
     *
     * @param txn the {@code TransactionBody}
     * @return one of HederaFunctionality
     * @throws UnknownHederaFunctionality if all the check fails
     */
    @NonNull
    public static HederaFunctionality functionOf(@NonNull final TransactionBody txn) throws UnknownHederaFunctionality {
        DataCase dataCase = txn.getDataCase();

        return switch (dataCase) {
            case CONTRACTCALL -> ContractCall;
            case CONTRACTCREATEINSTANCE -> ContractCreate;
            case CONTRACTUPDATEINSTANCE -> ContractUpdate;
            case CONTRACTDELETEINSTANCE -> ContractDelete;
            case ETHEREUMTRANSACTION -> EthereumTransaction;
            case CRYPTOADDLIVEHASH -> CryptoAddLiveHash;
            case CRYPTOAPPROVEALLOWANCE -> CryptoApproveAllowance;
            case CRYPTODELETEALLOWANCE -> CryptoDeleteAllowance;
            case CRYPTOCREATEACCOUNT -> CryptoCreate;
            case CRYPTODELETE -> CryptoDelete;
            case CRYPTODELETELIVEHASH -> CryptoDeleteLiveHash;
            case CRYPTOTRANSFER -> CryptoTransfer;
            case CRYPTOUPDATEACCOUNT -> CryptoUpdate;
            case FILEAPPEND -> FileAppend;
            case FILECREATE -> FileCreate;
            case FILEDELETE -> FileDelete;
            case FILEUPDATE -> FileUpdate;
            case SYSTEMDELETE -> SystemDelete;
            case SYSTEMUNDELETE -> SystemUndelete;
            case FREEZE -> Freeze;
            case CONSENSUSCREATETOPIC -> ConsensusCreateTopic;
            case CONSENSUSUPDATETOPIC -> ConsensusUpdateTopic;
            case CONSENSUSDELETETOPIC -> ConsensusDeleteTopic;
            case CONSENSUSSUBMITMESSAGE -> ConsensusSubmitMessage;
            case UNCHECKEDSUBMIT -> UncheckedSubmit;
            case TOKENCREATION -> TokenCreate;
            case TOKENFREEZE -> TokenFreezeAccount;
            case TOKENUNFREEZE -> TokenUnfreezeAccount;
            case TOKENGRANTKYC -> TokenGrantKycToAccount;
            case TOKENREVOKEKYC -> TokenRevokeKycFromAccount;
            case TOKENDELETION -> TokenDelete;
            case TOKENUPDATE -> TokenUpdate;
            case TOKENMINT -> TokenMint;
            case TOKENBURN -> TokenBurn;
            case TOKENWIPE -> TokenAccountWipe;
            case TOKENASSOCIATE -> TokenAssociateToAccount;
            case TOKENDISSOCIATE -> TokenDissociateFromAccount;
            case TOKEN_FEE_SCHEDULE_UPDATE -> TokenFeeScheduleUpdate;
            case TOKEN_PAUSE -> TokenPause;
            case TOKEN_UNPAUSE -> TokenUnpause;
            case SCHEDULECREATE -> ScheduleCreate;
            case SCHEDULEDELETE -> ScheduleDelete;
            case SCHEDULESIGN -> ScheduleSign;
            case NODE_STAKE_UPDATE -> NodeStakeUpdate;
            case UTIL_PRNG -> UtilPrng;
            default -> throw new UnknownHederaFunctionality("Unknown HederaFunctionality for " + txn);
        };
    }
}
