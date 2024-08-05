/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PENDING_NFT_AIRDROP_ALREADY_EXISTS;
import static com.hedera.node.app.service.token.impl.handlers.transfer.AssociateTokenRecipientsStep.PLACEHOLDER_SYNTHETIC_ASSOCIATION;
import static com.hedera.node.app.service.token.impl.handlers.transfer.AssociateTokenRecipientsStep.associationFeeFor;
import static com.hedera.node.app.service.token.impl.util.AirdropHandlerHelper.createAccountPendingAirdrop;
import static com.hedera.node.app.service.token.impl.util.AirdropHandlerHelper.createFirstAccountPendingAirdrop;
import static com.hedera.node.app.service.token.impl.util.AirdropHandlerHelper.createFungibleTokenPendingAirdropId;
import static com.hedera.node.app.service.token.impl.util.AirdropHandlerHelper.createNftPendingAirdropId;
import static com.hedera.node.app.service.token.impl.util.AirdropHandlerHelper.createPendingAirdropRecord;
import static com.hedera.node.app.service.token.impl.util.AirdropHandlerHelper.separateFungibleTransfers;
import static com.hedera.node.app.service.token.impl.util.AirdropHandlerHelper.separateNftTransfers;
import static com.hedera.node.app.service.token.impl.util.CryptoTransferHelper.createAccountAmount;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.PendingAirdropId;
import com.hedera.hapi.node.base.PendingAirdropValue;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountPendingAirdrop;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableNftStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableAirdropStore;
import com.hedera.node.app.service.token.impl.handlers.transfer.TransferContextImpl;
import com.hedera.node.app.service.token.impl.handlers.transfer.TransferExecutor;
import com.hedera.node.app.service.token.impl.validators.CryptoTransferValidator;
import com.hedera.node.app.service.token.impl.validators.TokenAirdropValidator;
import com.hedera.node.app.service.token.records.TokenAirdropStreamBuilder;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.TokensConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#TOKEN_AIRDROP}.
 */
@Singleton
public class TokenAirdropHandler extends TransferExecutor implements TransactionHandler {
    private static final Logger log = LogManager.getLogger(TokenAirdropHandler.class);
    private final TokenAirdropValidator validator;
    /**
     * Default constructor for injection.
     */
    @Inject
    public TokenAirdropHandler(
            @NonNull final TokenAirdropValidator validator,
            @NonNull final CryptoTransferValidator cryptoTransferValidator) {
        super(cryptoTransferValidator);
        this.validator = validator;
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().tokenAirdropOrThrow();
        var convertedOp = CryptoTransferTransactionBody.newBuilder()
                .tokenTransfers(op.tokenTransfers())
                .build();
        preHandle(context, convertedOp);
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        requireNonNull(txn);
        final var op = txn.tokenAirdropOrThrow();
        validator.pureChecks(op);
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        requireNonNull(context);
        final var txn = context.body();
        final var op = txn.tokenAirdropOrThrow();
        final var pendingStore = context.storeFactory().writableStore(WritableAirdropStore.class);
        final var accountStore = context.storeFactory().writableStore(WritableAccountStore.class);
        final var nftStore = context.storeFactory().readableStore(ReadableNftStore.class);
        final var tokenStore = context.storeFactory().readableStore(ReadableTokenStore.class);
        final var tokenRelStore = context.storeFactory().readableStore(ReadableTokenRelationStore.class);
        var recordBuilder = context.savepointStack().getBaseBuilder(TokenAirdropStreamBuilder.class);
        List<TokenTransferList> tokenTransferList = new ArrayList<>();

        // validate the transaction body token transfers and NFT transfers
        validator.validateSemantics(context, op, accountStore, tokenStore, tokenRelStore, nftStore);

        // If the transaction is valid, charge custom fees in advance
        var convertedOp = CryptoTransferTransactionBody.newBuilder()
                .tokenTransfers(op.tokenTransfers())
                .build();
        assessAndChargeCustomFee(context, convertedOp);

        for (final var xfers : op.tokenTransfers()) {
            final var tokenId = xfers.tokenOrThrow();
            boolean shouldExecuteCryptoTransfer = false;
            final var transferListBuilder = TokenTransferList.newBuilder().token(tokenId);

            // process fungible token transfers if any. pureChecks validates there is only one debit, so findFirst
            // should return one item
            if (!xfers.transfers().isEmpty()) {
                // 1. separate transfers in to two lists
                // - one list for executing the transfer and one list for adding to pending state
                final var fungibleLists = separateFungibleTransfers(context, tokenId, xfers.transfers());
                chargeAirdropFee(context, fungibleLists.pendingFungibleAmounts().size(), fungibleLists.transfersNeedingAutoAssociation());

                final var senderAccountAmount = xfers.transfers().stream()
                        .filter(item -> item.amount() < 0)
                        .findFirst();
                final var senderId = senderAccountAmount.orElseThrow().accountIDOrThrow();
                // 2. create and save pending airdrops in to state
                createPendingAirdropsForFungible(
                        fungibleLists.pendingFungibleAmounts(),
                        tokenId,
                        senderId,
                        accountStore,
                        pendingStore,
                        recordBuilder);

                // 3. create account amounts and add them to the transfer list
                if (!fungibleLists.transferFungibleAmounts().isEmpty()) {
                    shouldExecuteCryptoTransfer = true;
                    addTransfersToTransferList(
                            fungibleLists.transferFungibleAmounts(),
                            senderId,
                            senderAccountAmount.get().isApproval(),
                            transferListBuilder);
                }
            }

            // process non-fungible tokens transfers if any
            if (!xfers.nftTransfers().isEmpty()) {
                final var nftTransfer = xfers.nftTransfers().stream().findFirst();
                final var senderId = nftTransfer.orElseThrow().senderAccountIDOrThrow();
                // 2. separate NFT transfers in to two lists
                // - one list for executing the transfer and one list for adding to pending state
                final var nftLists = separateNftTransfers(context, tokenId, xfers.nftTransfers());
                chargeAirdropFee(
                        context, nftLists.pendingNftList().size(), nftLists.transfersNeedingAutoAssociation());

                // 3. create and save NFT pending airdrops in to state
                createPendingAirdropsForNFTs(
                        nftLists.pendingNftList(), tokenId, pendingStore, senderId, accountStore, recordBuilder);

                // 3. add to transfer list
                if (!nftLists.transferNftList().isEmpty()) {
                    shouldExecuteCryptoTransfer = true;
                    transferListBuilder.nftTransfers(nftLists.transferNftList());
                }
            }

            // build transfer list and add it to tokenTransferList
            if (shouldExecuteCryptoTransfer) {
                tokenTransferList.add(transferListBuilder.build());
            }
        }

        // transfer tokens, that are not in pending state, if any...
        if (!tokenTransferList.isEmpty()) {
            executeAirdropCryptoTransfer(context, tokenTransferList, recordBuilder);
        }
    }

    /**
     * Charges the airdrop fee for the pending airdrops.The fee will be charged from the payer account and will vary
     * based on the number of pending airdrops created. This will be charged in handle method once we assess
     * number of pending airdrops created.No extra association fee is charged because the airdrop fee includes
     * token association fee.
     *
     * @param context             the {@link HandleContext} for the transaction
     * @param pendingAirdropsSize the number of pending airdrops created
     * @param numUnlimitedAssociationTransfers the number of unlimited association transfers
     */
    private void chargeAirdropFee(
            final @NonNull HandleContext context,
            final int pendingAirdropsSize,
            final int numUnlimitedAssociationTransfers) {
        final var pendingAirdropFee = airdropFeeForPendingAirdrop(context) * pendingAirdropsSize;
        final var airdropFeeForUnlimitedAssociations = airdropFee(context) * numUnlimitedAssociationTransfers;
        final var totalFee = pendingAirdropFee + airdropFeeForUnlimitedAssociations;
        // There are three cases for the fee charged in an airdrop transaction
        // 1. If there are no pending airdrops created and token is explicitly associated, only the CryptoTransferFee is
        // charged
        // 2. If there are no pending airdrops created but the token is not explicitly associated. But it has unlimited
        // max auto-associations set . Then we charge airdrop fee of $0.05 here and the triggered CryptoTransfer will
        // charge $0.05. So the total of $0.1
        // 3. If there are pending airdrops created, then we charge the airdrop fee of $0.1 per pending airdrop
        if (!context.tryToChargePayer(totalFee)) {
            throw new HandleException(INSUFFICIENT_PAYER_BALANCE);
        }
    }

    /**
     * Creates pending airdrops for all the assessed pending nft pending airdrops. This also adds
     * the pending airdrop created to the record.
     * @param nftLists the list of nft transfers
     * @param tokenId the token id
     * @param pendingStore the pending airdrop store
     * @param senderId the sender account id
     * @param accountStore the account store
     * @param recordBuilder the token airdrop record builder
     */
    private void createPendingAirdropsForNFTs(
            @NonNull final List<NftTransfer> nftLists,
            @NonNull final TokenID tokenId,
            @NonNull final WritableAirdropStore pendingStore,
            @NonNull final AccountID senderId,
            @NonNull final WritableAccountStore accountStore,
            @NonNull final TokenAirdropStreamBuilder recordBuilder) {
        nftLists.forEach(item -> {
            // Each time it is important to get the latest sender account, as we are updating the account
            // with new pending airdrop
            final var senderAccount = requireNonNull(accountStore.getForModify(senderId));
            final var pendingId = createNftPendingAirdropId(
                    tokenId, item.serialNumber(), item.senderAccountID(), item.receiverAccountID());
            // check for existence
            validateTrue(!pendingStore.exists(pendingId), PENDING_NFT_AIRDROP_ALREADY_EXISTS);
            updateNewPendingAirdrop(senderAccount, pendingId, null, accountStore, pendingStore);
            final var record = createPendingAirdropRecord(pendingId, null);
            recordBuilder.addPendingAirdrop(record);
        });
    }

    /**
     * Adds the transfers that are not in pending state to the transfer list. Constructs the debit for the sender and
     * the credits for the receivers.
     * @param fungibleAmounts the fungible airdrop amounts
     * @param sender the sender account id
     * @param isApproval if the airdrop transfer is sent with an approval
     * @param transferListBuilder the transfer list builder
     */
    private void addTransfersToTransferList(
            @NonNull final List<AccountAmount> fungibleAmounts,
            @NonNull final AccountID sender,
            final boolean isApproval,
            @NonNull final TokenTransferList.Builder transferListBuilder) {
        List<AccountAmount> amounts = new LinkedList<>();
        final var receiversAmountList =
                fungibleAmounts.stream().filter(item -> item.amount() > 0).toList();
        var senderAmount =
                receiversAmountList.stream().mapToLong(AccountAmount::amount).sum();
        var newSenderAccountAmount = createAccountAmount(sender, -senderAmount, isApproval);
        amounts.add(newSenderAccountAmount);
        amounts.addAll(receiversAmountList);
        transferListBuilder.transfers(amounts);
    }

    /**
     * Creates pending airdrops from the assessed airdrop amounts.
     * @param fungibleAmounts the fungible airdrop amounts
     * @param tokenId the token id
     * @param senderId the sender account id
     * @param accountStore the account store
     * @param pendingStore the pending airdrop store
     * @param recordBuilder the token airdrop record builder
     */
    private void createPendingAirdropsForFungible(
            @NonNull final List<AccountAmount> fungibleAmounts,
            @NonNull final TokenID tokenId,
            @NonNull final AccountID senderId,
            @NonNull final WritableAccountStore accountStore,
            @NonNull final WritableAirdropStore pendingStore,
            @NonNull final TokenAirdropStreamBuilder recordBuilder) {
        fungibleAmounts.forEach(accountAmount -> {
            // Each time it is important to get the latest sender account , as we are updating the account
            // with new pending airdrop
            final var senderAccount = requireNonNull(accountStore.getForModify(senderId));
            final var pendingId =
                    createFungibleTokenPendingAirdropId(tokenId, senderAccount.accountId(), accountAmount.accountID());
            final var pendingValue = PendingAirdropValue.newBuilder()
                    .amount(accountAmount.amount())
                    .build();
            updateNewPendingAirdrop(senderAccount, pendingId, pendingValue, accountStore, pendingStore);
            // use the value from the store, in case we already have a pending airdrop with the same id
            final var record = createPendingAirdropRecord(
                    pendingId, requireNonNull(pendingStore.get(pendingId)).pendingAirdropValue());
            recordBuilder.addPendingAirdrop(record);
        });
    }

    /**
     * Assesses and charge the custom fee for the token airdrop transaction.
     * @param context the handle context
     * @param body the crypto transfer transaction body
     */
    private void assessAndChargeCustomFee(
            @NonNull final HandleContext context, @NonNull final CryptoTransferTransactionBody body) {
        final var syntheticCryptoTransferTxn =
                TransactionBody.newBuilder().cryptoTransfer(body).build();
        final var transferContext = new TransferContextImpl(context, body, true);
        chargeCustomFee(syntheticCryptoTransferTxn, transferContext);
    }

    /**
     *  Create new {@link AccountPendingAirdrop} and if the sender has already existing pending airdrops
     *  link them together and update the account store and the pending airdrop store with the new values
     */
    private void updateNewPendingAirdrop(
            @NonNull final Account senderAccount,
            @NonNull final PendingAirdropId pendingId,
            @Nullable final PendingAirdropValue pendingValue,
            @NonNull final WritableAccountStore accountStore,
            @NonNull final WritableAirdropStore pendingStore) {
        if (pendingStore.contains(pendingId)) {
            // No need to update pointers to update the amount of fungible value
            pendingStore.update(pendingId, createFirstAccountPendingAirdrop(pendingValue));
        } else {
            final AccountPendingAirdrop newHeadAirdrop;
            if (senderAccount.hasHeadPendingAirdropId()) {
                // Get the previous head pending airdrop and update the previous airdrop ID
                final var currentHeadAirdropId = senderAccount.headPendingAirdropIdOrThrow();
                final var currentHeadAirdrop = pendingStore.getForModify(currentHeadAirdropId);
                if (currentHeadAirdrop == null) {
                    log.error(
                            "Head pending airdrop {} not found for account {}",
                            currentHeadAirdropId,
                            senderAccount.accountId());
                    newHeadAirdrop = createFirstAccountPendingAirdrop(pendingValue);
                } else {
                    final var updatedHeadAirdrop = currentHeadAirdrop
                            .copyBuilder()
                            .previousAirdrop(pendingId)
                            .build();
                    // since we already validated the headAirdropId exists, we can safely update the store
                    pendingStore.put(currentHeadAirdropId, updatedHeadAirdrop);
                    // Create new account airdrop with next airdrop ID the previous head airdrop
                    newHeadAirdrop = createAccountPendingAirdrop(pendingValue, currentHeadAirdropId);
                }
            } else {
                newHeadAirdrop = createFirstAccountPendingAirdrop(pendingValue);
            }
            // Update the sender account with new head pending airdrop
            final var numPendingAirdrops = senderAccount.numberPendingAirdrops();
            final var updatedSenderAccount = senderAccount
                    .copyBuilder()
                    .headPendingAirdropId(pendingId)
                    .numberPendingAirdrops(numPendingAirdrops + 1)
                    .build();
            accountStore.put(updatedSenderAccount);
            pendingStore.put(pendingId, newHeadAirdrop);
        }
    }

    /**
     * Gets the airdrop fee for the token airdrop transaction when a pending airdrop is created. It will be
     * the sum of the association fee and the airdrop fee.
     * @param feeContext the fee context
     * @return the airdrop fee
     */
    private long airdropFeeForPendingAirdrop(@NonNull final HandleContext feeContext) {
        final var associationFee = associationFeeFor(feeContext, PLACEHOLDER_SYNTHETIC_ASSOCIATION);
        final var airdropFee = airdropFee(feeContext);
        return associationFee + airdropFee;
    }

    /**
     * Gets the airdrop fee for the token airdrop transaction, when no pending airdrop is created.
     * This is charged when receiver is not yet associated with the token and has max auto-associations set to -1.
     * So, this will not create a pending airdrop and auto-association fee is automatically charged during the
     * CryptoTransfer auto-association step.
     * @param feeContext the fee context
     * @return the airdrop fee
     */
    private long airdropFee(final HandleContext feeContext) {
        return ((FeeContext) feeContext)
                .feeCalculatorFactory()
                .feeCalculator(SubType.DEFAULT)
                .calculate()
                .totalFee();
    }

    /**
     *  Calculate the fees for the token airdrop transaction. Initially only the CryptoTransferFees is charged
     *  for the token airdrop transaction. The fees are charged based on number of pending airdrops that
     *  are created in the transaction. If there are no pending airdrops created, no extra fees is charged and
     *  only the CryptoTransferFees is charged.
     */
    @NonNull
    @Override
    public Fees calculateFees(@NonNull final FeeContext feeContext) {
        final var op = feeContext.body().tokenAirdropOrThrow();
        final var tokensConfig = feeContext.configuration().getConfigData(TokensConfig.class);
        validateTrue(tokensConfig.airdropsEnabled(), NOT_SUPPORTED);
        // Always charge CryptoTransferFee as base price and then each time a pending airdrop is created
        // we charge airdrop fee in handle
        return calculateCryptoTransferFees(feeContext, op.tokenTransfers());
    }

    /**
     * Calculates the CryptoTransfer fees by dispatching a synthetic CryptoTransfer transaction.
     * @param feeContext the fee context
     * @param tokenTransfers the token transfers
     * @return the fees
     */
    private Fees calculateCryptoTransferFees(
            @NonNull FeeContext feeContext, @NonNull List<TokenTransferList> tokenTransfers) {
        final var cryptoTransferBody = CryptoTransferTransactionBody.newBuilder()
                .tokenTransfers(tokenTransfers)
                .build();

        final var syntheticCryptoTransferTxn = TransactionBody.newBuilder()
                .cryptoTransfer(cryptoTransferBody)
                .transactionID(feeContext.body().transactionID())
                .build();

        return feeContext.dispatchComputeFees(syntheticCryptoTransferTxn, feeContext.payer());
    }
}
