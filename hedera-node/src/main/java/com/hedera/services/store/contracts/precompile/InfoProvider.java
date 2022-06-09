package com.hedera.services.store.contracts.precompile;

import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.util.Optional;

import static com.hedera.services.store.contracts.WorldStateTokenAccount.TOKEN_PROXY_ACCOUNT_NONCE;

public class InfoProvider {
	private MessageFrame messageFrame;
	private PrecompileMessage precompileMessage;
	private boolean isDirectTokenCall;
	private Address senderAddress;
	private WorldLedgers ledgers;
	private HederaStackedWorldStateUpdater updater;

	public InfoProvider(boolean isDirectTokenCall, PrecompileMessage precompileMessage, WorldLedgers ledgers) {
		this.isDirectTokenCall = isDirectTokenCall;
		this.precompileMessage = precompileMessage;
		this.senderAddress = precompileMessage.getSenderAddress();
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

	public PrecompileMessage getPrecompileMessage() {
		return precompileMessage;
	}

	public Wei getValue() {
		return isDirectTokenCall ? precompileMessage.getValue()
				: messageFrame.getValue();
	}

	public long getRemainingGas() {
		return isDirectTokenCall ? precompileMessage.getRemainingGas()
				: messageFrame.getRemainingGas();
	}

	public boolean isDirectTokenCall() {
		return isDirectTokenCall;
	}

	public long getTimestamp() {
		return isDirectTokenCall ? precompileMessage.getConsensusTime()
				: messageFrame.getBlockValues().getTimestamp();
	}

	public Bytes getInputData() {
		return isDirectTokenCall ? precompileMessage.getInputData()
				: messageFrame.getInputData();
	}

	public void setState(MessageFrame.State state) {
		if (isDirectTokenCall) {
			//TODO
		} else {
			messageFrame.setState(state);
		}
	}

	public void setRevertReason(Bytes revertReason) {
		//TODO
		if (messageFrame != null)
			messageFrame.setRevertReason(revertReason);
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
