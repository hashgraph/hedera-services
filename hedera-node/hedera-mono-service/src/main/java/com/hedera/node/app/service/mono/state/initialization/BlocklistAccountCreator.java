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

@Singleton
public class BlocklistAccountCreator {
    public static final String BLOCKLIST_ACCOUNT_MEMO = "Account is blocked";
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

    public void ensureBlockedAccounts() {
        final List<byte[]> blocklist;
        try {
            blocklist = readPrivateKeyBlocklist(blocklistFileName).stream()
                    .map(Hex::decode)
                    .collect(Collectors.toList());
        } catch (DecoderException de) {
            log.error("Failed to parse blocklist, entry not in hex format", de);
            return;
        } catch (Exception e) {
            log.error("Failed to read blocklist file {}", blocklistFileName, e);
            return;
        }

        final var blockedEVMAddresses = blocklist.stream()
                .map(this::ecdsaPrivateToPublicKey)
                .map(EthSigsUtils::recoverAddressFromPubKey)
                .map(ByteString::copyFrom)
                .filter(evmAddress -> aliasManager.lookupIdBy(evmAddress).equals(MISSING_NUM))
                .collect(Collectors.toSet());

        final var genesisAccountId = AccountID.newBuilder()
                .setRealmNum(0)
                .setShardNum(0)
                .setAccountNum(accountNumbers.treasury())
                .build();

        for (final var evmAddress : blockedEVMAddresses) {
            final var newId = ids.newAccountId(genesisAccountId);
            final var account = blockedAccountWith(evmAddress);
            accounts.put(newId, account);
            blockedAccountsCreated.add(account);
            aliasManager.link(evmAddress, EntityNum.fromAccountId(newId));
        }
    }

    private List<String> readPrivateKeyBlocklist(String fileName) {
        final var inputStream = getClass().getClassLoader().getResourceAsStream(fileName);
        final var reader = new BufferedReader(new InputStreamReader(inputStream));
        return reader.lines().toList();
    }

    private HederaAccount blockedAccountWith(ByteString evmAddress) {
        final var expiry = properties.getLongProperty(BOOTSTRAP_SYSTEM_ENTITY_EXPIRY);
        final var account = new HederaAccountCustomizer()
                .isReceiverSigRequired(true)
                .isDeclinedReward(true)
                .isDeleted(false)
                .expiry(expiry)
                .memo(BLOCKLIST_ACCOUNT_MEMO)
                .isSmartContract(false)
                .key(getGenesisKey())
                .autoRenewPeriod(expiry)
                .alias(evmAddress)
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

    public List<HederaAccount> getBlockedAccountsCreated() {
        return blockedAccountsCreated;
    }

    public void forgetCreatedBlockedAccounts() {
        blockedAccountsCreated.clear();
    }
}
