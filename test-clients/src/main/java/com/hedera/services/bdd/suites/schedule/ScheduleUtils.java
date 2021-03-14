package com.hedera.services.bdd.suites.schedule;

import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;

public class ScheduleUtils {
	public static SchedulableTransactionBody fromOrdinary(TransactionBody txn) {
		var scheduleBuilder = SchedulableTransactionBody.newBuilder();

		scheduleBuilder.setTransactionFee(txn.getTransactionFee());
		scheduleBuilder.setMemo(txn.getMemo());

		if (txn.hasContractCall()) {
			scheduleBuilder.setContractCall(txn.getContractCall());
		} else if (txn.hasContractCreateInstance()) {
			scheduleBuilder.setContractCreateInstance(txn.getContractCreateInstance());
		} else if (txn.hasContractUpdateInstance()) {
			scheduleBuilder.setContractUpdateInstance(txn.getContractUpdateInstance());
		} else if (txn.hasContractDeleteInstance()) {
			scheduleBuilder.setContractDeleteInstance(txn.getContractDeleteInstance());
		} else if (txn.hasCryptoCreateAccount()) {
			scheduleBuilder.setCryptoCreateAccount(txn.getCryptoCreateAccount());
		} else if (txn.hasCryptoDelete()) {
			scheduleBuilder.setCryptoDelete(txn.getCryptoDelete());
		} else if (txn.hasCryptoTransfer()) {
			scheduleBuilder.setCryptoTransfer(txn.getCryptoTransfer());
		} else if (txn.hasCryptoUpdateAccount()) {
			scheduleBuilder.setCryptoUpdateAccount(txn.getCryptoUpdateAccount());
		} else if (txn.hasFileAppend()) {
			scheduleBuilder.setFileAppend(txn.getFileAppend());
		} else if (txn.hasFileCreate()) {
			scheduleBuilder.setFileCreate(txn.getFileCreate());
		} else if (txn.hasFileDelete()) {
			scheduleBuilder.setFileDelete(txn.getFileDelete());
		} else if (txn.hasFileUpdate()) {
			scheduleBuilder.setFileUpdate(txn.getFileUpdate());
		} else if (txn.hasSystemDelete()) {
			scheduleBuilder.setSystemDelete(txn.getSystemDelete());
		} else if (txn.hasSystemUndelete()) {
			scheduleBuilder.setSystemUndelete(txn.getSystemUndelete());
		} else if (txn.hasFreeze()) {
			scheduleBuilder.setFreeze(txn.getFreeze());
		} else if (txn.hasConsensusCreateTopic()) {
			scheduleBuilder.setConsensusCreateTopic(txn.getConsensusCreateTopic());
		} else if (txn.hasConsensusUpdateTopic()) {
			scheduleBuilder.setConsensusUpdateTopic(txn.getConsensusUpdateTopic());
		} else if (txn.hasConsensusDeleteTopic()) {
			scheduleBuilder.setConsensusDeleteTopic(txn.getConsensusDeleteTopic());
		} else if (txn.hasConsensusSubmitMessage()) {
			scheduleBuilder.setConsensusSubmitMessage(txn.getConsensusSubmitMessage());
		} else if (txn.hasTokenCreation()) {
			scheduleBuilder.setTokenCreation(txn.getTokenCreation());
		} else if (txn.hasTokenFreeze()) {
			scheduleBuilder.setTokenFreeze(txn.getTokenFreeze());
		} else if (txn.hasTokenUnfreeze()) {
			scheduleBuilder.setTokenUnfreeze(txn.getTokenUnfreeze());
		} else if (txn.hasTokenGrantKyc()) {
			scheduleBuilder.setTokenGrantKyc(txn.getTokenGrantKyc());
		} else if (txn.hasTokenRevokeKyc()) {
			scheduleBuilder.setTokenRevokeKyc(txn.getTokenRevokeKyc());
		} else if (txn.hasTokenDeletion()) {
			scheduleBuilder.setTokenDeletion(txn.getTokenDeletion());
		} else if (txn.hasTokenUpdate()) {
			scheduleBuilder.setTokenUpdate(txn.getTokenUpdate());
		} else if (txn.hasTokenMint()) {
			scheduleBuilder.setTokenMint(txn.getTokenMint());
		} else if (txn.hasTokenBurn()) {
			scheduleBuilder.setTokenBurn(txn.getTokenBurn());
		} else if (txn.hasTokenWipe()) {
			scheduleBuilder.setTokenWipe(txn.getTokenWipe());
		} else if (txn.hasTokenAssociate()) {
			scheduleBuilder.setTokenAssociate(txn.getTokenAssociate());
		} else if (txn.hasTokenDissociate()) {
			scheduleBuilder.setTokenDissociate(txn.getTokenDissociate());
		} else if (txn.hasScheduleDelete()) {
			scheduleBuilder.setScheduleDelete(txn.getScheduleDelete());
		}

		return scheduleBuilder.build();
	}

	public static TransactionBody fromScheduled(SchedulableTransactionBody scheduledTxn) {
		var builder = TransactionBody.newBuilder();

		builder.setTransactionFee(scheduledTxn.getTransactionFee());
		builder.setMemo(scheduledTxn.getMemo());

		if (scheduledTxn.hasContractCall()) {
			builder.setContractCall(scheduledTxn.getContractCall());
		} else if (scheduledTxn.hasContractCreateInstance()) {
			builder.setContractCreateInstance(scheduledTxn.getContractCreateInstance());
		} else if (scheduledTxn.hasContractUpdateInstance()) {
			builder.setContractUpdateInstance(scheduledTxn.getContractUpdateInstance());
		} else if (scheduledTxn.hasContractDeleteInstance()) {
			builder.setContractDeleteInstance(scheduledTxn.getContractDeleteInstance());
		} else if (scheduledTxn.hasCryptoCreateAccount()) {
			builder.setCryptoCreateAccount(scheduledTxn.getCryptoCreateAccount());
		} else if (scheduledTxn.hasCryptoDelete()) {
			builder.setCryptoDelete(scheduledTxn.getCryptoDelete());
		} else if (scheduledTxn.hasCryptoTransfer()) {
			builder.setCryptoTransfer(scheduledTxn.getCryptoTransfer());
		} else if (scheduledTxn.hasCryptoUpdateAccount()) {
			builder.setCryptoUpdateAccount(scheduledTxn.getCryptoUpdateAccount());
		} else if (scheduledTxn.hasFileAppend()) {
			builder.setFileAppend(scheduledTxn.getFileAppend());
		} else if (scheduledTxn.hasFileCreate()) {
			builder.setFileCreate(scheduledTxn.getFileCreate());
		} else if (scheduledTxn.hasFileDelete()) {
			builder.setFileDelete(scheduledTxn.getFileDelete());
		} else if (scheduledTxn.hasFileUpdate()) {
			builder.setFileUpdate(scheduledTxn.getFileUpdate());
		} else if (scheduledTxn.hasSystemDelete()) {
			builder.setSystemDelete(scheduledTxn.getSystemDelete());
		} else if (scheduledTxn.hasSystemUndelete()) {
			builder.setSystemUndelete(scheduledTxn.getSystemUndelete());
		} else if (scheduledTxn.hasFreeze()) {
			builder.setFreeze(scheduledTxn.getFreeze());
		} else if (scheduledTxn.hasConsensusCreateTopic()) {
			builder.setConsensusCreateTopic(scheduledTxn.getConsensusCreateTopic());
		} else if (scheduledTxn.hasConsensusUpdateTopic()) {
			builder.setConsensusUpdateTopic(scheduledTxn.getConsensusUpdateTopic());
		} else if (scheduledTxn.hasConsensusDeleteTopic()) {
			builder.setConsensusDeleteTopic(scheduledTxn.getConsensusDeleteTopic());
		} else if (scheduledTxn.hasConsensusSubmitMessage()) {
			builder.setConsensusSubmitMessage(scheduledTxn.getConsensusSubmitMessage());
		} else if (scheduledTxn.hasTokenCreation()) {
			builder.setTokenCreation(scheduledTxn.getTokenCreation());
		} else if (scheduledTxn.hasTokenFreeze()) {
			builder.setTokenFreeze(scheduledTxn.getTokenFreeze());
		} else if (scheduledTxn.hasTokenUnfreeze()) {
			builder.setTokenUnfreeze(scheduledTxn.getTokenUnfreeze());
		} else if (scheduledTxn.hasTokenGrantKyc()) {
			builder.setTokenGrantKyc(scheduledTxn.getTokenGrantKyc());
		} else if (scheduledTxn.hasTokenRevokeKyc()) {
			builder.setTokenRevokeKyc(scheduledTxn.getTokenRevokeKyc());
		} else if (scheduledTxn.hasTokenDeletion()) {
			builder.setTokenDeletion(scheduledTxn.getTokenDeletion());
		} else if (scheduledTxn.hasTokenUpdate()) {
			builder.setTokenUpdate(scheduledTxn.getTokenUpdate());
		} else if (scheduledTxn.hasTokenMint()) {
			builder.setTokenMint(scheduledTxn.getTokenMint());
		} else if (scheduledTxn.hasTokenBurn()) {
			builder.setTokenBurn(scheduledTxn.getTokenBurn());
		} else if (scheduledTxn.hasTokenWipe()) {
			builder.setTokenWipe(scheduledTxn.getTokenWipe());
		} else if (scheduledTxn.hasTokenAssociate()) {
			builder.setTokenAssociate(scheduledTxn.getTokenAssociate());
		} else if (scheduledTxn.hasTokenDissociate()) {
			builder.setTokenDissociate(scheduledTxn.getTokenDissociate());
		} else if (scheduledTxn.hasScheduleDelete()) {
			builder.setScheduleDelete(scheduledTxn.getScheduleDelete());
		}

		return builder.build();
	}
}
