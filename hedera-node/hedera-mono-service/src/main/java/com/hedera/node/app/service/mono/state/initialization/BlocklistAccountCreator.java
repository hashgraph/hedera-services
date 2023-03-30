/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.initialization;

import static com.hedera.node.app.service.mono.utils.EntityNum.MISSING_NUM;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asFcKeyUnchecked;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asKeyUnchecked;
import static com.hedera.node.app.spi.config.PropertyNames.BLOCKLIST_FILE;
import static com.hedera.node.app.spi.config.PropertyNames.BOOTSTRAP_SYSTEM_ENTITY_EXPIRY;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.utils.EthSigsUtils;
import com.hedera.node.app.service.mono.config.AccountNumbers;
import com.hedera.node.app.service.mono.context.annotations.CompositeProps;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.service.mono.exceptions.NegativeAccountBalanceException;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.ledger.accounts.HederaAccountCustomizer;
import com.hedera.node.app.service.mono.ledger.backing.BackingStore;
import com.hedera.node.app.service.mono.ledger.ids.EntityIdSource;
import com.hedera.node.app.service.mono.legacy.core.jproto.JEd25519Key;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.util.encoders.DecoderException;
import org.bouncycastle.util.encoders.Hex;

/**
 * Encapsulates the logic for reading blocked accounts from file and creating them.
 */
@Singleton
public class BlocklistAccountCreator {
    private static final int ZERO_BALANCE = 0;
    private static final Logger log = LogManager.getLogger(BlocklistAccountCreator.class);
    private final String blocklistFileName;
    private final Supplier<HederaAccount> accountSupplier;
    private final EntityIdSource ids;
    private final BackingStore<AccountID, HederaAccount> accounts;
    private final Supplier<JEd25519Key> genesisKeySource;
    private final PropertySource properties;
    private final AliasManager aliasManager;
    private JKey genesisKey;
    private final List<HederaAccount> blockedAccountsCreated = new ArrayList<>();
    private AccountNumbers accountNumbers;

    /**
     * @param evmAddress the EVM address of the blocked account
     * @param memo      the memo of the blocked account
     */
    record BlockedInfo(ByteString evmAddress, String memo) {}

    @Inject
    public BlocklistAccountCreator(
            final Supplier<HederaAccount> accountSupplier,
            final EntityIdSource ids,
            final BackingStore<AccountID, HederaAccount> accounts,
            final Supplier<JEd25519Key> genesisKeySource,
            final @CompositeProps PropertySource properties,
            final AliasManager aliasManager,
            AccountNumbers accountNumbers) {
        this.blocklistFileName = properties.getStringProperty(BLOCKLIST_FILE);
        this.accountSupplier = accountSupplier;
        this.ids = ids;
        this.accounts = accounts;
        this.genesisKeySource = genesisKeySource;
        this.properties = properties;
        this.aliasManager = aliasManager;
        this.accountNumbers = accountNumbers;
    }

    /**
     * Makes sure that all blocked accounts contained in the blocklist file are present in state, and creates them if necessary.
     */
    public void ensureBlockedAccounts() {
        final List<BlockedInfo> blocklist;
        try {
            final var fileLines = readPrivateKeyBlocklist(blocklistFileName);
            final var columnCount = fileLines.get(0).split(",").length;
            blocklist = fileLines.stream()
                    .skip(1)
                    .map(line -> parseCSVLine(line, columnCount))
                    .toList();
        } catch (IllegalArgumentException iae) {
            log.error("Failed to parse blocklist, entry does not have required number of columns", iae);
            return;
        } catch (DecoderException de) {
            log.error("Failed to parse blocklist, entry not in hex format", de);
            return;
        } catch (Exception e) {
            log.error("Failed to read blocklist file {}", blocklistFileName, e);
            return;
        }

        final var blockedToCreate = blocklist.stream()
                .filter(blockedAccount ->
                        aliasManager.lookupIdBy(blockedAccount.evmAddress).equals(MISSING_NUM))
                .collect(Collectors.toSet());

        final var genesisAccountId = AccountID.newBuilder()
                .setRealmNum(0)
                .setShardNum(0)
                .setAccountNum(accountNumbers.treasury())
                .build();

        for (final var blockedInfo : blockedToCreate) {
            final var newId = ids.newAccountId(genesisAccountId);
            final var account = blockedAccountWith(blockedInfo);
            accounts.put(newId, account);
            blockedAccountsCreated.add(account);
            aliasManager.link(blockedInfo.evmAddress, EntityNum.fromAccountId(newId));
        }
    }

    private List<String> readPrivateKeyBlocklist(String fileName) {
        final var inputStream = getClass().getClassLoader().getResourceAsStream(fileName);
        final var reader = new BufferedReader(new InputStreamReader(inputStream));
        return reader.lines().toList();
    }

    /**
     * Parses a line from the blocklist file and returns blocked account info record.
     *
     * The line should have the following format:
     * <private key>,<memo>
     *     where <private key> is a hex-encoded private key
     *     and <memo> is a memo for the blocked account
     *     and both values are comma-separated.
     *
     * The resulting blocked account info record contains the EVM address derived from the private key, and the memo.
     *
     * @param line line from the blocklist file
     * @param columnCount number of comma-separated values in a line
     * @return blocked account info record
     */
    private BlockedInfo parseCSVLine(String line, int columnCount) {
        final var parts = line.split(",");
        if (parts.length != columnCount) {
            throw new IllegalArgumentException("Invalid line in blocklist file: " + line);
        }

        final var privateKeyBytes = Hex.decode(parts[0]);
        final var publicKeyBytes = ecdsaPrivateToPublicKey(privateKeyBytes);
        final var evmAddressBytes = EthSigsUtils.recoverAddressFromPubKey(publicKeyBytes);
        return new BlockedInfo(ByteString.copyFrom(evmAddressBytes), parts[1]);
    }

    /**
     * Creates a blocked Hedera account with the given memo and EVM address.
     * A blocked account has receiverSigRequired flag set to true, key set to the genesis key, and balance set to 0.
     *
     * @param blockedInfo record containing EVM address and memo for the blocked account
     * @return a Hedera account with the given memo and EVM address
     */
    private HederaAccount blockedAccountWith(BlockedInfo blockedInfo) {
        final var expiry = properties.getLongProperty(BOOTSTRAP_SYSTEM_ENTITY_EXPIRY);
        final var account = new HederaAccountCustomizer()
                .isReceiverSigRequired(true)
                .isDeclinedReward(true)
                .isDeleted(false)
                .expiry(expiry)
                .memo(blockedInfo.memo)
                .isSmartContract(false)
                .key(getGenesisKey())
                .autoRenewPeriod(expiry)
                .alias(blockedInfo.evmAddress)
                .customizing(accountSupplier.get());
        try {
            account.setBalance(ZERO_BALANCE);
        } catch (final NegativeAccountBalanceException e) {
            throw new IllegalStateException(e);
        }
        return account;
    }

    private JKey getGenesisKey() {
        if (genesisKey == null) {
            // Traditionally the genesis key has been a key list, keep that way to avoid breaking
            // any clients
            genesisKey = asFcKeyUnchecked(Key.newBuilder()
                    .setKeyList(KeyList.newBuilder().addKeys(asKeyUnchecked(genesisKeySource.get())))
                    .build());
        }
        return genesisKey;
    }

    /**
     * Derives the ECDSA public key bytes from the given ECDSA private key bytes.
     *
     * @param privateKeyBytes ECDSA private key bytes
     * @return ECDSA public key bytes
     */
    private byte[] ecdsaPrivateToPublicKey(byte[] privateKeyBytes) {
        final var ecdsaSecp256K1Curve = SECNamedCurves.getByName("secp256k1");
        final var ecdsaSecp256K1Domain = new ECDomainParameters(
                ecdsaSecp256K1Curve.getCurve(),
                ecdsaSecp256K1Curve.getG(),
                ecdsaSecp256K1Curve.getN(),
                ecdsaSecp256K1Curve.getH());
        final var privateKeyData = new BigInteger(1, privateKeyBytes);
        var q = ecdsaSecp256K1Domain.getG().multiply(privateKeyData);
        var publicParams = new ECPublicKeyParameters(q, ecdsaSecp256K1Domain);
        return publicParams.getQ().getEncoded(true);
    }

    /**
     * Returns a list of {@link HederaAccount}, denoting all the blocked accounts created by a previous call to {@link BlocklistAccountCreator#ensureBlockedAccounts()}.
     *
     * @return a list of blocked accounts created during the current run of the node
     */
    public List<HederaAccount> getBlockedAccountsCreated() {
        return blockedAccountsCreated;
    }

    /**
     * Clears the list of blocked accounts created by a previous call to {@link BlocklistAccountCreator#ensureBlockedAccounts()}
     */
    public void forgetCreatedBlockedAccounts() {
        blockedAccountsCreated.clear();
    }
}
