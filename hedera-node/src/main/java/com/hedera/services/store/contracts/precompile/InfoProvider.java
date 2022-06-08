package com.hedera.services.store.contracts.precompile;

import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.util.Optional;

import static com.hedera.services.store.contracts.WorldStateTokenAccount.TOKEN_PROXY_ACCOUNT_NONCE;

public class InfoProvider {
	private MessageFrame messageFrame;
	private boolean isDirectTokenCall;
	private Address senderAddress;
	private WorldLedgers ledgers;
	private HederaStackedWorldStateUpdater updater;

	public InfoProvider(boolean isDirectTokenCall, Address senderAddress, WorldLedgers ledgers) {
		this.isDirectTokenCall = isDirectTokenCall;
		this.senderAddress = senderAddress;
		this.ledgers = ledgers;
	}

	public InfoProvider(MessageFrame messageFrame, WorldLedgers ledgers, HederaStackedWorldStateUpdater updater) {
		this(false, null, ledgers);
		this.messageFrame = messageFrame;
		this.updater = updater;
	}

	public MessageFrame getMessageFrame() {
		return messageFrame;
	}

	public boolean validateKey(final Address target, final HTSPrecompiledContract.ContractActivationTest activationTest) {
		if (isDirectTokenCall)
			return activationTest.apply(false, target, senderAddress, ledgers);

		final var aliases = updater.aliases();
		final var recipient = aliases.resolveForEvm(messageFrame
				.getRecipientAddress());
		final var sender = aliases.resolveForEvm(messageFrame.getSenderAddress());
		if (messageFrame == null) {
			return activationTest.apply(false, target, sender, ledgers);
		} else if (isDelegateCall(messageFrame) && !isToken(messageFrame, recipient)) {
			return activationTest.apply(true, target, recipient, ledgers);
		} else {
			final var parentFrame = getParentFrame(messageFrame);
			return activationTest.apply(parentFrame.isPresent() && isDelegateCall(parentFrame.get()), target, sender,
					ledgers);
		}
	}

	private boolean isDelegateCall(final MessageFrame frame) {
		final var contract = frame.getContractAddress();
		final var recipient = frame.getRecipientAddress();
		return !contract.equals(recipient);
	}

	private boolean isToken(final MessageFrame frame, final Address address) {
		final var account = frame.getWorldUpdater().get(address);
		if (account != null) {
			return account.getNonce() == TOKEN_PROXY_ACCOUNT_NONCE;
		}
		return false;
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
}
