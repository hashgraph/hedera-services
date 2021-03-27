package com.hedera.services.sysfiles.validation;

import com.hederahashgraph.api.proto.java.HederaFunctionality;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.*;

import java.util.EnumSet;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;

public class ExpectedCustomThrottles {
	public static final EnumSet<HederaFunctionality> OPS_FOR_RELEASE_0130 = EnumSet.of(
			CryptoCreate,
			CryptoTransfer,
			CryptoUpdate,
			CryptoDelete,
			FileCreate,
			FileUpdate,
			FileDelete,
			FileAppend,
			ContractCreate,
			ContractUpdate,
			ContractCall,
			ContractDelete,
			ConsensusCreateTopic,
			ConsensusUpdateTopic,
			ConsensusDeleteTopic,
			ConsensusSubmitMessage,
			TokenCreate,
			TokenFreezeAccount,
			TokenUnfreezeAccount,
			TokenGrantKycToAccount,
			TokenRevokeKycFromAccount,
			TokenDelete,
			TokenMint,
			TokenBurn,
			TokenAccountWipe,
			TokenUpdate,
			TokenAssociateToAccount,
			TokenDissociateFromAccount,
			ScheduleCreate,
			ScheduleSign,
			ScheduleDelete,
			ConsensusGetTopicInfo,
			ContractCallLocal,
			ContractGetInfo,
			ContractGetBytecode,
			ContractGetRecords,
			CryptoGetAccountBalance,
			CryptoGetAccountRecords,
			CryptoGetInfo,
			FileGetContents,
			FileGetInfo,
			TransactionGetReceipt,
			TransactionGetRecord,
			GetVersionInfo,
			TokenGetInfo,
			ScheduleGetInfo
	);
}
