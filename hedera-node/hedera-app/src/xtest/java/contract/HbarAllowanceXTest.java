/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
 *
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
 */

package contract;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static contract.HtsErc721TransferXTestConstants.APPROVED_ADDRESS;
import static contract.HtsErc721TransferXTestConstants.APPROVED_BESU_ADDRESS;
import static contract.HtsErc721TransferXTestConstants.APPROVED_HEADLONG_ADDRESS;
import static contract.HtsErc721TransferXTestConstants.APPROVED_ID;
import static contract.XTestConstants.INVALID_ID;
import static contract.XTestConstants.OWNER_ADDRESS;
import static contract.XTestConstants.OWNER_HEADLONG_ADDRESS;
import static contract.XTestConstants.OWNER_ID;
import static contract.XTestConstants.SENDER_CONTRACT_ID_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountCryptoAllowance;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.hbarallowance.HbarAllowanceTranslator;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;

/**
 * Exercises hbar allowance for a token via the following steps relative to an {@code OWNER} and {@code SENDER} accounts:
 * <ol>
 *     <li>Get Hbar Allowance of OWNER for SPENDER via {@link HbarAllowanceTranslator#HBAR_ALLOWANCE_PROXY}.</li>
 *     <li>Get Hbar Allowance of OWNER for SPENDER via {@link HbarAllowanceTranslator#HBAR_ALLOWANCE}.</li>
 *     <li>Fail Hbar Allowance if OWNER is not found.</li>
 * </ol>
 */
public class HbarAllowanceXTest extends AbstractContractXTest {
    @Override
    protected void doScenarioOperations() {
        runHasCallAndExpectOnSuccess(
                APPROVED_BESU_ADDRESS,
                bytesForRedirectForAccount(
                        HbarAllowanceTranslator.HBAR_ALLOWANCE_PROXY.encodeCallWithArgs(APPROVED_HEADLONG_ADDRESS),
                        OWNER_ID),
                output -> assertEquals(
                        asBytesResult(HbarAllowanceTranslator.HBAR_ALLOWANCE_PROXY
                                .getOutputs()
                                .encodeElements((long) SUCCESS.getNumber(), BigInteger.valueOf(1_000L))),
                        output),
                "Successful execution of hbarAllowance for SENDER by OWNER.");
        runHasCallAndExpectOnSuccess(
                APPROVED_BESU_ADDRESS,
                Bytes.wrapByteBuffer(HbarAllowanceTranslator.HBAR_ALLOWANCE.encodeCall(
                        Tuple.of(OWNER_HEADLONG_ADDRESS, APPROVED_HEADLONG_ADDRESS))),
                output -> assertEquals(
                        asBytesResult(HbarAllowanceTranslator.HBAR_ALLOWANCE_PROXY
                                .getOutputs()
                                .encodeElements((long) SUCCESS.getNumber(), BigInteger.valueOf(1_000L))),
                        output),
                "Successful execution of hbarAllowance for SENDER by OWNER.");
        runHasCallAndExpectRevert(
                APPROVED_BESU_ADDRESS,
                bytesForRedirectForAccount(
                        HbarAllowanceTranslator.HBAR_ALLOWANCE_PROXY.encodeCallWithArgs(APPROVED_HEADLONG_ADDRESS),
                        INVALID_ID),
                INVALID_ALLOWANCE_OWNER_ID,
                "Failed execution of hbarAllowance when OWNER is not found.",
                false);
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        return new HashMap<>() {
            {
                put(ProtoBytes.newBuilder().value(OWNER_ADDRESS).build(), OWNER_ID);
                put(ProtoBytes.newBuilder().value(APPROVED_ADDRESS).build(), APPROVED_ID);
            }
        };
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        return new HashMap<>() {
            {
                put(
                        OWNER_ID,
                        Account.newBuilder()
                                .accountId(OWNER_ID)
                                .alias(OWNER_ADDRESS)
                                .key(SENDER_CONTRACT_ID_KEY)
                                .cryptoAllowances(AccountCryptoAllowance.newBuilder()
                                        .amount(1_000L)
                                        .spenderId(APPROVED_ID)
                                        .build())
                                .build());
                put(
                        APPROVED_ID,
                        Account.newBuilder()
                                .accountId(APPROVED_ID)
                                .alias(APPROVED_ADDRESS)
                                .build());
            }
        };
    }
}
