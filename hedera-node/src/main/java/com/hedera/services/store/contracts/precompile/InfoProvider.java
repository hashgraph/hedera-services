package com.hedera.services.store.contracts.precompile;

import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.models.NftId;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.util.Optional;

import static com.hedera.services.store.contracts.WorldStateTokenAccount.TOKEN_PROXY_ACCOUNT_NONCE;

public class InfoProvider {


    private WorldLedgers ledgers;
    private Optional<MessageFrame> messageFrame;
    private Optional<Address> senderAddress;
    private HederaStackedWorldStateUpdater updater;

    public InfoProvider(WorldLedgers ledgers, Optional<Address> senderAddress, HederaStackedWorldStateUpdater updater) {
        this.ledgers = ledgers;
        this.senderAddress = senderAddress;
        this.updater = updater;
    }

    public InfoProvider(Optional<MessageFrame> messageFrame, WorldLedgers ledgers, HederaStackedWorldStateUpdater updater) {
        this(ledgers, Optional.empty(), updater);
        this.messageFrame = messageFrame;
    }


    public MessageFrame getMessageFrame() {
        return messageFrame.orElse(null);
    }

    public Bytes getRedirectBytes(Bytes input, long tokenId) {
        final String TOKEN_CALL_REDIRECT_HEX = "0x618dc65e0000000000000000000000000000000000000";
        var redirectBytes = Bytes.fromHexString(
                TOKEN_CALL_REDIRECT_HEX
                        .concat(Long.toHexString(tokenId))
                        .concat(input.toHexString()
                                .replace("0x", "")));
        return redirectBytes;
    }

    /* the methods from nameOf to balanceOf  need to be removed
    since the ledger object comes from HTSPrecompileContract
     * */
    public String nameOf(TokenID tokenId) {
        return ledgers.nameOf(tokenId);
    }

    public String symbolOf(TokenID tokenId) {
        return ledgers.symbolOf(tokenId);
    }

    public long totalSupplyOf(final TokenID tokenId) {
        return ledgers.totalSupplyOf(tokenId);
    }

    public int decimalsOf(final TokenID tokenId) {
        return ledgers.decimalsOf(tokenId);
    }

    public String metadataOd(NftId nftId) {
        return ledgers.metadataOf(nftId);
    }

    public long balanceOf(final AccountID accountId, final TokenID tokenId) {
        return ledgers.balanceOf(accountId, tokenId);
    }

    public boolean validateKey(final Address target, final HTSPrecompiledContract.ContractActivationTest activationTest) {
        if (isDirectTokenCall())
            return activationTest.apply(false, target, senderAddress.get(), ledgers);

        final var frame = messageFrame.get();
        final var aliases = updater.aliases();
        final var recipient = aliases.resolveForEvm(frame
                .getRecipientAddress());
        final var sender = aliases.resolveForEvm(frame.getSenderAddress());
        if (messageFrame == null) {
            return activationTest.apply(false, target, sender, ledgers);
        } else if (isDelegateCall(frame) && !isToken(frame, recipient)) {
            return activationTest.apply(true, target, recipient, ledgers);
        } else {
            final var parentFrame = getParentFrame(frame);
            return activationTest.apply(parentFrame.isPresent() && isDelegateCall(parentFrame.get()), target, sender,
                    ledgers);
        }
    }

    private Optional<MessageFrame> getParentFrame(final MessageFrame currentFrame) {
        final var it = currentFrame.getMessageFrameStack().descendingIterator();

        if (it.hasNext()) {
            it.next();
        } else {
            return Optional.empty();
        }

        MessageFrame parentFrame;
        if (it.hasNext()) {
            parentFrame = it.next();
        } else {
            return Optional.empty();
        }

        return Optional.of(parentFrame);
    }

    private boolean isDelegateCall(final MessageFrame frame) {
        final var contract = frame.getContractAddress();
        final var recipient = frame.getRecipientAddress();
        return !contract.equals(recipient);
    }

    private boolean isDirectTokenCall() {
        return senderAddress.isPresent() && messageFrame == null;
    }


    private boolean isToken(final MessageFrame frame, final Address address) {
        final var account = frame.getWorldUpdater().get(address);
        if (account != null) {
            return account.getNonce() == TOKEN_PROXY_ACCOUNT_NONCE;
        }
        return false;
    }
}
