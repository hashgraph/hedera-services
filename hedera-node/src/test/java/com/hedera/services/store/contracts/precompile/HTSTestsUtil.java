package com.hedera.services.store.contracts.precompile;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.google.protobuf.ByteString;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.models.Id;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class HTSTestsUtil {

	public static final long AMOUNT = 1_234_567L;
	public static final long DEFAULT_GAS_PRICE = 10_000L;
	public static final TokenID token = IdUtils.asToken("0.0.1");
	public static final AccountID sender = IdUtils.asAccount("0.0.2");
	public static final AccountID receiver = IdUtils.asAccount("0.0.3");
	public static final AccountID feeCollector = IdUtils.asAccount("0.0.4");
	public static final AccountID account = IdUtils.asAccount("0.0.3");
	public static final AccountID accountMerkleId = IdUtils.asAccount("0.0.999");
	public static final TokenID nonFungible = IdUtils.asToken("0.0.777");
	public static final TokenID tokenMerkleId = IdUtils.asToken("0.0.777");
	public static final Id accountId = Id.fromGrpcAccount(account);
	public static final Address recipientAddr = Address.ALTBN128_ADD;
	public static final Address contractAddr = Address.ALTBN128_MUL;
	public static final Address senderAddress = Address.ALTBN128_PAIRING;
	public static final Address parentContractAddress = Address.BLAKE2B_F_COMPRESSION;
	public static final Address parentRecipientAddress = Address.BLS12_G1ADD;
	public static final Dissociation dissociateToken =
			Dissociation.singleDissociation(account, nonFungible);
	public static final Dissociation multiDissociateOp =
			Dissociation.singleDissociation(account, nonFungible);
	public static final Bytes successResult = UInt256.valueOf(ResponseCodeEnum.SUCCESS_VALUE);
	public static final Bytes invalidSigResult = UInt256.valueOf(ResponseCodeEnum.INVALID_SIGNATURE_VALUE);
	public static final Association associateOp =
			Association.singleAssociation(accountMerkleId, tokenMerkleId);
	public static final TokenID fungible = IdUtils.asToken("0.0.888");
	public static final Id nonFungibleId = Id.fromGrpcToken(nonFungible);
	public static final Id fungibleId = Id.fromGrpcToken(fungible);
	public static final List<Long> targetSerialNos = List.of(1L, 2L, 3L);
	public static final BurnWrapper fungibleBurn =
			BurnWrapper.forFungible(fungible, AMOUNT);
	public static final MintWrapper fungibleMint =
			MintWrapper.forFungible(fungible, AMOUNT);

	public static final Association multiAssociateOp =
			Association.singleAssociation(accountMerkleId, tokenMerkleId);
	public static final Address recipientAddress = Address.ALTBN128_ADD;
	public static final Address contractAddress = Address.ALTBN128_MUL;

	public static final BurnWrapper nonFungibleBurn =
			BurnWrapper.forNonFungible(nonFungible, targetSerialNos);
	public static final Bytes burnSuccessResultWith49Supply = Bytes.fromHexString(
			"0x00000000000000000000000000000000000000000000000000000000000000160000000000000000000000000000000000000000000000000000000000000031");
	public static final TxnReceipt.Builder receiptBuilder =
			TxnReceipt.newBuilder().setNewTotalSupply(49).setStatus(ResponseCodeEnum.SUCCESS.name());
	public static final ExpirableTxnRecord.Builder expirableTxnRecordBuilder = ExpirableTxnRecord.newBuilder()
			.setReceiptBuilder(receiptBuilder);

	public static final List<ByteString> newMetadata = List.of(
			ByteString.copyFromUtf8("AAA"), ByteString.copyFromUtf8("BBB"), ByteString.copyFromUtf8("CCC"));
	public static final MintWrapper nftMint =
			MintWrapper.forNonFungible(nonFungible, newMetadata);
	public static final Bytes fungibleSuccessResultWith10Supply = Bytes.fromHexString(
			"0x0000000000000000000000000000000000000000000000000000000000000016000000000000000000000000000000000000000000000000000000000000000a00000000000000000000000000000000000000000000000000000000000000600000000000000000000000000000000000000000000000000000000000000000");
	public static final Bytes failInvalidResult = UInt256.valueOf(ResponseCodeEnum.FAIL_INVALID_VALUE);
	public static final Instant pendingChildConsTime = Instant.ofEpochSecond(1_234_567L, 890);
	public static final Address nonFungibleTokenAddr = nonFungibleId.asEvmAddress();
	public static final Address fungibleTokenAddr = fungibleId.asEvmAddress();
	public static final Address senderAddr = Address.ALTBN128_PAIRING;
	public static final Address accountAddr = accountId.asEvmAddress();

	public static final TokenTransferWrapper nftTransferList =
			new TokenTransferWrapper(
					List.of(new SyntheticTxnFactory.NftExchange(1, token, sender, receiver)),
					new ArrayList<>() {
					}
			);

	public static final SyntheticTxnFactory.FungibleTokenTransfer transfer =
			new SyntheticTxnFactory.FungibleTokenTransfer(
					AMOUNT,
					token,
					sender,
					receiver
			);
	public static final SyntheticTxnFactory.FungibleTokenTransfer transferSenderOnly =
			new SyntheticTxnFactory.FungibleTokenTransfer(
					AMOUNT,
					token,
					sender,
					null
			);
	public static final SyntheticTxnFactory.FungibleTokenTransfer transferReceiverOnly =
			new SyntheticTxnFactory.FungibleTokenTransfer(
					AMOUNT,
					token,
					null,
					receiver
			);
	public static final TokenTransferWrapper TOKEN_TRANSFER_WRAPPER = new TokenTransferWrapper(
			new ArrayList<>() {
			},
			List.of(transfer)
	);
	public static final TokenTransferWrapper tokensTransferList =
			new TokenTransferWrapper(
					new ArrayList<>() {
					},
					List.of(transfer, transfer)
			);
	public static final TokenTransferWrapper tokensTransferListSenderOnly =
			new TokenTransferWrapper(
					new ArrayList<>() {
					},
					List.of(transferSenderOnly, transferSenderOnly)
			);
	public static final TokenTransferWrapper tokensTransferListReceiverOnly =
			new TokenTransferWrapper(
					new ArrayList<>() {
					},
					List.of(transferReceiverOnly, transferReceiverOnly)
			);
	public static final TokenTransferWrapper nftsTransferList =
			new TokenTransferWrapper(
					List.of(
							new SyntheticTxnFactory.NftExchange(1, token, sender, receiver),
							new SyntheticTxnFactory.NftExchange(2, token, sender, receiver)
					),
					new ArrayList<>() {
					}
			);
	public static final List<BalanceChange> tokenTransferChanges = List.of(
			BalanceChange.changingFtUnits(
					Id.fromGrpcToken(token),
					token,
					AccountAmount.newBuilder().setAccountID(sender).setAmount(-AMOUNT).build()
			),
			BalanceChange.changingFtUnits(
					Id.fromGrpcToken(token),
					token,
					AccountAmount.newBuilder().setAccountID(receiver).setAmount(AMOUNT).build()
			)
	);
	public static final List<BalanceChange> tokensTransferChanges = List.of(
			BalanceChange.changingFtUnits(
					Id.fromGrpcToken(token),
					token,
					AccountAmount.newBuilder().setAccountID(sender).setAmount(-AMOUNT).build()
			),
			BalanceChange.changingFtUnits(
					Id.fromGrpcToken(token),
					token,
					AccountAmount.newBuilder().setAccountID(receiver).setAmount(+AMOUNT).build()
			),
			BalanceChange.changingFtUnits(
					Id.fromGrpcToken(token),
					token,
					AccountAmount.newBuilder().setAccountID(sender).setAmount(-AMOUNT).build()
			),
			BalanceChange.changingFtUnits(
					Id.fromGrpcToken(token),
					token,
					AccountAmount.newBuilder().setAccountID(receiver).setAmount(+AMOUNT).build()
			)
	);

	public static final List<BalanceChange> tokensTransferChangesSenderOnly = List.of(
			BalanceChange.changingFtUnits(
					Id.fromGrpcToken(token),
					token,
					AccountAmount.newBuilder().setAccountID(sender).setAmount(AMOUNT).build()
			)
	);

	public static final List<BalanceChange> nftTransferChanges = List.of(
			BalanceChange.changingNftOwnership(
					Id.fromGrpcToken(token),
					token,
					NftTransfer.newBuilder()
							.setSenderAccountID(sender).setReceiverAccountID(receiver).setSerialNumber(1L)
							.build()
			),
			/* Simulate an assessed fallback fee */
			BalanceChange.changingHbar(
					AccountAmount.newBuilder().setAccountID(receiver).setAmount(-AMOUNT).build()),
			BalanceChange.changingHbar(
					AccountAmount.newBuilder().setAccountID(feeCollector).setAmount(+AMOUNT).build())
	);

	public static final List<BalanceChange> nftsTransferChanges = List.of(
			BalanceChange.changingNftOwnership(
					Id.fromGrpcToken(token),
					token,
					NftTransfer.newBuilder()
							.setSenderAccountID(sender).setReceiverAccountID(receiver).setSerialNumber(1L)
							.build()
			),
			BalanceChange.changingNftOwnership(
					Id.fromGrpcToken(token),
					token,
					NftTransfer.newBuilder()
							.setSenderAccountID(sender).setReceiverAccountID(receiver).setSerialNumber(2L)
							.build()
			)
	);
}
