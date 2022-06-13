package com.hedera.services.store.contracts.precompile;

import com.hedera.services.ledger.TransferLogic;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.util.Optional;

import static com.hedera.services.store.contracts.WorldStateTokenAccount.TOKEN_PROXY_ACCOUNT_NONCE;

public class EVMInfoProvider implements InfoProvider {
	private MessageFrame messageFrame;
	private WorldLedgers ledgers;
	private HederaStackedWorldStateUpdater updater;

	public EVMInfoProvider(MessageFrame messageFrame, WorldLedgers ledgers, HederaStackedWorldStateUpdater updater) {
		this.messageFrame = messageFrame;
		this.ledgers = ledgers;
		this.updater = updater;
	}

	@Override
	public Wei getValue() {
		return messageFrame.getValue();
	}

	@Override
	public MessageFrame getMessageFrame() {
		return messageFrame;
	}

	@Override
	public long getRemainingGas() {
		return messageFrame.getRemainingGas();
	}

	@Override
	public boolean isDirectTokenCall() {
		return false;
	}

	@Override
	public long getTimestamp() {
		return messageFrame.getBlockValues().getTimestamp();
	}

	@Override
	public Bytes getInputData() {
		return messageFrame.getInputData();
	}

	@Override
	public void setState(MessageFrame.State state) {
		messageFrame.setState(state);
	}

	@Override
	public void setRevertReason(Bytes revertReason) {
		messageFrame.setRevertReason(revertReason);
	}

	/**
	 * Checks if a key implicit in a target address is active in the current frame using a {@link
	 * HTSPrecompiledContract.ContractActivationTest}.
	 * <p>
	 * We massage the current frame a bit to ensure that a precompile being executed via delegate call is tested as
	 * such.
	 * There are three cases.
	 * <ol>
	 *     <li>The precompile is being executed via a delegate call, so the current frame's <b>recipient</b>
	 *     (not sender) is really the "active" contract that can match a {@code delegatable_contract_id} key; or,
	 *     <li>The precompile is being executed via a call, but the calling code was executed via
	 *     a delegate call, so although the current frame's sender <b>is</b> the "active" contract, it must
	 *     be evaluated using an activation test that restricts to {@code delegatable_contract_id} keys; or,</li>
	 *     <li>The precompile is being executed via a call, and the calling code is being executed as
	 *     part of a non-delegate call.</li>
	 * </ol>
	 * <p>
	 * Note that because the {@link DecodingFacade} converts every address to its "mirror" address form
	 * (as needed for e.g. the {@link TransferLogic} implementation), we can assume the target address
	 * is a mirror address. All other addresses we resolve to their mirror form before proceeding.
	 *
	 * @param target         the element to test for key activation, in standard form
	 * @param activationTest the function which should be invoked for key validation
	 * @return whether the implied key is active
	 */
	@Override
	public boolean validateKey(final Address target, final HTSPrecompiledContract.ContractActivationTest activationTest) {
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
