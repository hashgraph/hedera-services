package com.hedera.services.sigs.order;

import com.hederahashgraph.api.proto.java.TransactionBody;

/**
 * Defines a type able to decide if certain keys' signing requirements are waived for a given transaction.
 */
public interface SignatureWaivers {
	/**
	 * Advises if the target file's WACL must sign a given file append.
	 *
	 * @param fileAppendTxn a file append transaction
	 * @return whether the target file's WACL must sign
	 */
	boolean isAppendFileWaclWaived(TransactionBody fileAppendTxn);

	/**
	 * Advises if the target file's WACL must sign a given file update.
	 *
	 * @param fileUpdateTxn a file update transaction
	 * @return whether the target file's WACL must sign
	 */
	boolean isTargetFileWaclWaived(TransactionBody fileUpdateTxn);

	/**
	 * Advises if the new WACL in a given file update transaction must sign.
	 *
	 * @param fileUpdateTxn a file update transaction
	 * @return whether the new WACL from the transaction must sign
	 */
	boolean isNewFileWaclWaived(TransactionBody fileUpdateTxn);

	/**
	 * Advises if the target account's key must sign a given crypto update.
	 *
	 * @param cryptoUpdateTxn a crypto update transaction
	 * @return whether the target account's key must sign
	 */
	boolean isTargetAccountKeyWaived(TransactionBody cryptoUpdateTxn);

	/**
	 * Advises if the new key for an account must sign a given crypto update.
	 *
	 * @param cryptoUpdateTxn a crypto update transaction
	 * @return whether the new key from the transaction must sign
	 */
	boolean isNewAccountKeyWaived(TransactionBody cryptoUpdateTxn);
}
