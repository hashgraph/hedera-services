package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbiConstants;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.LogBuilder;
import com.hedera.node.app.service.token.ReadableAccountStore;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.log.Log;

import java.math.BigInteger;
import java.util.List;

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asLongZeroAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.priorityAddressOf;
import static java.util.Objects.requireNonNull;

/**
 * Helper for logging ERC transfer events for fungible and non-fungible transfers.
 */
public class TransferEventLoggingUtils {
    private TransferEventLoggingUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    /**
     * Logs a successful ERC-20 transfer event based on the Hedera-style representation of the fungible
     * balance adjustments.
     *
     * @param tokenId the token ID
     * @param adjusts the Hedera-style representation of the fungible balance adjustments
     * @param accountStore the account store to get account addresses from
     * @param frame the frame to log to
     */
    public static void logSuccessfulFungibleTransfer(
            @NonNull final TokenID tokenId,
            @NonNull final List<AccountAmount> adjusts,
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final MessageFrame frame) {
        requireNonNull(tokenId);
        requireNonNull(frame);
        requireNonNull(adjusts);
        requireNonNull(accountStore);
        AccountID senderId = null;
        AccountID receiverId = null;
        long amount = 0L;
        for (final var adjust : adjusts) {
            amount = Math.abs(adjust.amount());
            if (adjust.amount() > 0) {
                receiverId = adjust.accountIDOrThrow();
            } else {
                senderId = adjust.accountIDOrThrow();
            }
        }
        frame.addLog(builderFor(tokenId, requireNonNull(senderId), requireNonNull(receiverId), accountStore)
                .forDataItem(amount)
                .build());
    }

    /**
     * Logs a successful ERC-721 transfer event based on the Hedera-style representation of the NFT ownership change.
     *
     * @param tokenId the token ID
     * @param nftTransfer the Hedera-style representation of the NFT ownership change
     * @param accountStore the account store to get account addresses from
     * @param frame the frame to log to
     */
    public static void logSuccessfulNftTransfer(
            @NonNull final TokenID tokenId,
            @NonNull final NftTransfer nftTransfer,
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final MessageFrame frame) {
        requireNonNull(tokenId);
        requireNonNull(frame);
        requireNonNull(nftTransfer);
        requireNonNull(accountStore);
        frame.addLog(builderFor(
                tokenId,
                nftTransfer.senderAccountIDOrThrow(),
                nftTransfer.receiverAccountIDOrThrow(), accountStore)
                .forIndexedArgument(BigInteger.valueOf(nftTransfer.serialNumber()))
                .build());
    }

    private static LogBuilder builderFor(
            @NonNull final TokenID tokenId,
            @NonNull final AccountID senderId,
            @NonNull final AccountID receiverId,
            @NonNull final ReadableAccountStore accountStore) {
        final var tokenAddress = asLongZeroAddress(tokenId.tokenNum());
        final var senderAddress = priorityAddressOf(requireNonNull(accountStore.getAccountById(senderId)));
        final var receiverAddress = priorityAddressOf(requireNonNull(accountStore.getAccountById(receiverId)));
        return LogBuilder.logBuilder()
                .forLogger(tokenAddress)
                .forEventSignature(AbiConstants.TRANSFER_EVENT)
                .forIndexedArgument(senderAddress)
                .forIndexedArgument(receiverAddress);
    }
}
