package com.hedera.services.state.exports;

import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.swirlds.fcmap.FCMap;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static java.util.Comparator.comparing;

public class ToStringAccountsExporter implements AccountsExporter {
	@Override
	public void toFile(String path, FCMap<MerkleEntityId, MerkleAccount> accounts) throws Exception {
		try (var writer = Files.newBufferedWriter(Paths.get(path))) {
			List<MerkleEntityId> keys = new ArrayList<>(accounts.keySet());
			keys.sort(comparing(MerkleEntityId::toAccountId, HederaLedger.ACCOUNT_ID_COMPARATOR));
			var first = true;
			for (var key : keys) {
				if (!first) {
					writer.write("\n");
				}
				first = false;
				writer.write(key.toAbbrevString() + "\n");
				writer.write("---\n");
				writer.write(accounts.get(key).toString() + "\n");
			}
		}
	}
}
