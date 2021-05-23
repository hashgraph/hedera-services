package com.hedera.services.context;

import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.common.AddressBook;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

import static com.hedera.services.utils.EntityIdUtils.parseAccount;

/**
 * Summarizes useful information about the nodes in the {@link AddressBook}
 * from the Platform. In the future, there may be events that require
 * re-reading the book; but at present nodes may treat the initializing
 * book as static.
 */
public class NodeInfo {
	private static final Logger log = LogManager.getLogger(NodeInfo.class);

	private boolean bookIsRead = false;

	private int n;
	private boolean[] isZeroStake;
	private AccountID[] accounts;

	private final long selfId;
	private final Supplier<AddressBook> book;

	public NodeInfo(long selfId, Supplier<AddressBook> book) {
		this.book = book;
		this.selfId = selfId;
	}

	/**
	 * Returns true if the given id refers to a missing node, or a node
	 * in the address book with explicit zero stake.
	 *
	 * @param nodeId the id of interest
	 */
	public boolean isZeroStake(long nodeId) {
		if (!bookIsRead) {
			readBook();
		}

		final int index = (int)nodeId;
		if (index < 0 || index >= n) {
			return true;
		}
		return isZeroStake[index];
	}

	/**
	 * Convenience method to check if this node is zero-stake.
	 */
	public boolean isSelfZeroStake() {
		return isZeroStake(selfId);
	}

	/**
	 * Returns the account parsed from the address book memo corresponding
	 * to the given node id.
	 *
	 * @param nodeId the id of interest
	 * @throws IllegalArgumentException if the book did not contain the id, or was missing an account for the id
	 */
	public AccountID accountOf(long nodeId) {
		if (!bookIsRead) {
			readBook();
		}

		final int index = (int)nodeId;
		if (index < 0 || index >= n) {
			throw new IllegalArgumentException("No node with id " + nodeId + " was in the address book!");
		}
		final var account = accounts[index];
		if (account == null) {
			throw new IllegalArgumentException("The  address book did not have an account for node id " + nodeId + "!");
		}
		return account;
	}

	/**
	 * Convenience method to check if this node has an account in the address book.
	 */
	public boolean hasSelfAccount() {
		try {
			accountOf(selfId);
			return true;
		} catch (IllegalArgumentException ignore) {
			return false;
		}
	}

	/**
	 * Convenience method to get this node's account from the address book.
	 *
	 * @throws IllegalArgumentException if the node did not have an account
	 */
	public AccountID selfAccount() {
		return accountOf(selfId);
	}

	void readBook() {
		final var staticBook = book.get();

		n = staticBook.getSize();
		accounts = new AccountID[n];
		isZeroStake = new boolean[n];

		for (int i = 0; i < n; i++) {
			final var address = staticBook.getAddress(i);
			isZeroStake[i] = address.getStake() <= 0;
			try {
				accounts[i] = parseAccount(address.getMemo());
			} catch (IllegalArgumentException e) {
				if (!isZeroStake[i]) {
					log.error("Cannot parse account for staked node id {}, potentially fatal!", i, e);
				}
			}
		}

		bookIsRead = true;
	}
}
