package com.hedera.services.utils;

import com.hedera.services.ledger.accounts.AliasManager;
import org.hyperledger.besu.datatypes.Address;

import javax.inject.Inject;
import java.util.Comparator;


public class AddressComparator implements Comparator<Address> {

	public static final AddressComparator INSTANCE = new AddressComparator();

	@Inject
	protected AliasManager aliasManager;

	private AddressComparator() {
		// private to force singleton usage.
	}

	@Override
	public int compare(Address b1, Address b2) {
		final int result = BytesComparator.INSTANCE.nullCheck(b1, b2);
		if (result == 2) {
			if (!aliasManager.isInUse(b1) && aliasManager.isInUse(b2)) {
				return -1;
			} else if (aliasManager.isInUse(b1) && !aliasManager.isInUse(b2)) {
				return 1;
			}
			return BytesComparator.INSTANCE.bytesCompare(b1, b2);
		}
		return result;
	}
}
