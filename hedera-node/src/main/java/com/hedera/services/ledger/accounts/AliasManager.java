package com.hedera.services.ledger.accounts;

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

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.legacy.core.jproto.JECDSASecp256k1Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x9.X9IntegerConverter;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.math.ec.custom.sec.SecP256K1Curve;
import org.hyperledger.besu.datatypes.Address;
import org.jetbrains.annotations.Nullable;
import org.spongycastle.util.encoders.Hex;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigInteger;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.hedera.services.utils.EntityNum.MISSING_NUM;
import static com.hedera.services.utils.MiscUtils.forEach;

/**
 * Handles a map with all the accounts that are auto-created. The map will be re-built on restart, reconnect.
 * Entries from the map are removed when the entity expires
 */
@Singleton
public class AliasManager extends AbstractContractAliases implements ContractAliases {
	private static final Logger log = LogManager.getLogger(AliasManager.class);

	private static final String NON_TRANSACTIONAL_MSG = "Base alias manager does not buffer changes";
	
	private static final ECCurve SECP256K1_CURVE = new SecP256K1Curve();

	private final Supplier<Map<ByteString, EntityNum>> aliases;

	@Inject
	public AliasManager(final Supplier<Map<ByteString, EntityNum>> aliases) {
		this.aliases = aliases;
	}

	@Override
	public void revert() {
		throw new UnsupportedOperationException(NON_TRANSACTIONAL_MSG);
	}

	@Override
	public void filterPendingChanges(Predicate<Address> filter) {
		throw new UnsupportedOperationException(NON_TRANSACTIONAL_MSG);
	}

	@Override
	public void commit(final @Nullable SigImpactHistorian observer) {
		throw new UnsupportedOperationException(NON_TRANSACTIONAL_MSG);
	}

	@Override
	public void link(final Address alias, final Address address) {
		link(ByteString.copyFrom(alias.toArrayUnsafe()), EntityNum.fromEvmAddress(address));
	}

	@Override
	public void unlink(final Address alias) {
		unlink(ByteString.copyFrom(alias.toArrayUnsafe()));
	}

	@Override
	public Address resolveForEvm(final Address addressOrAlias) {
		if (isMirror(addressOrAlias)) {
			return addressOrAlias;
		}
		final var aliasKey = ByteString.copyFrom(addressOrAlias.toArrayUnsafe());
		final var contractNum = curAliases().get(aliasKey);
		// If we cannot resolve to a mirror address, we return the missing alias and let a
		// downstream component fail the transaction by returning null from its get() method.
		// Cf. the address validator provided by ContractsModule#provideAddressValidator().
		return (contractNum == null) ? addressOrAlias : contractNum.toEvmAddress();
	}

	@Override
	public boolean isInUse(final Address address) {
		return curAliases().containsKey(ByteString.copyFrom(address.toArrayUnsafe()));
	}

	public void link(final ByteString alias, final EntityNum num) {
		curAliases().put(alias, num);
	}

	public boolean maybeLinkEvmAddress(final JKey key, final EntityNum num) {
		if (key != null && key.hasECDSAsecp256k1Key()) {
			byte[] rawCompressedKey = fromBytesInternal(key.getECDSASecp256k1Key());
			if (rawCompressedKey.length == JECDSASecp256k1Key.ECDSASECP256_COMPRESSED_BYTE_LENGTH) {
				var evmAddress = calculateEthAddress(rawCompressedKey);
				link(evmAddress, num);
				//FIXME System out
				System.out.printf(" %s <- 0x%s%n", num, Hex.toHexString(evmAddress.toByteArray()));
				return true;
			}
		}
		return false;
	}
	
	public void unlink(final ByteString alias) {
		curAliases().remove(alias);
	}

	/**
	 * From given MerkleMap of accounts, populate the auto accounts creations map. Iterate through
	 * each account in accountsMap and add an entry to autoAccountsMap if {@code alias} exists on the account.
	 *
	 * @param accounts the current accounts
	 */
	public void rebuildAliasesMap(final MerkleMap<EntityNum, MerkleAccount> accounts) {
		final var numCreate2Aliases = new AtomicInteger();
		final var numEOAliases = new AtomicInteger();
		final var workingAliases = curAliases();
		workingAliases.clear();
		forEach(accounts, (k, v) -> {
			if (!v.getAlias().isEmpty()) {
				workingAliases.put(v.getAlias(), k);
				if (v.isSmartContract()) {
					numCreate2Aliases.getAndIncrement();
				}
				if (maybeLinkEvmAddress(v.getAccountKey(), v.getKey())) {
					numEOAliases.incrementAndGet();
				}
			}
		});
		log.info("Rebuild complete, re-mapped {} aliases ({} from CREATE2, {} externally owned accounts)",
				workingAliases::size, numCreate2Aliases::get, numEOAliases::get);
	}

	static byte[] fromBytesInternal(byte[] publicKey) {
		System.out.println(Hex.toHexString(publicKey));
		if (publicKey.length == 33) {
			// compressed 33 byte raw form
			return publicKey;
		} else if (publicKey.length == 35 && publicKey[0]==58 && publicKey[1]==33) {
			// compressed 33 byte raw form
			byte[] key = new byte[33];
			System.arraycopy(publicKey, 2, key, 0, 33);
			return key;
		} else if (publicKey.length == 65) {
			// compress the 65 byte form
			return SECP256K1_CURVE.decodePoint(publicKey).getEncoded(true);
		} else {
			// Assume a DER-encoded public key descriptor
			return SubjectPublicKeyInfo.getInstance(publicKey).getPublicKeyData().getBytes();
		}
	}

	static ByteString calculateEthAddress(byte[] rawCompressedKey) {
		BigInteger x = new BigInteger(rawCompressedKey, 1, 32);
		ECPoint ecPoint = decompressKey(x, (rawCompressedKey[0] & 0x1) == 0x1);
		byte[] uncompressedKeyDer = ecPoint.getEncoded(false);
		byte[] uncompressedKeyRaw = new byte[64];
		System.arraycopy(uncompressedKeyDer, 1, uncompressedKeyRaw, 0, 64);
		byte[] hashedKey = new Keccak.Digest256().digest(uncompressedKeyRaw);

		return ByteString.copyFrom(hashedKey, 12, 20);
	}

	// Decompress a compressed public key (x co-ord and low-bit of y-co-ord).
	protected static ECPoint decompressKey(final BigInteger xBN, final boolean yBit) {
		final X9IntegerConverter x9 = new X9IntegerConverter();
		final byte[] compEnc = x9.integerToBytes(xBN, 1 + x9.getByteLength(SECP256K1_CURVE));
		compEnc[0] = (byte) (yBit ? 0x03 : 0x02);
		// TODO: Find a better way to handle an invalid point compression here.
		// Currently ECCurve#decodePoint throws an IllegalArgumentException.
		return SECP256K1_CURVE.decodePoint(compEnc);
	}
	
	/**
	 * Ensures an alias is no longer in use, returning whether it previously was.
	 *
	 * @param alias the alias to forget
	 * @return whether it was present
	 */
	public boolean forgetAlias(final ByteString alias) {
		if (alias.isEmpty()) {
			return false;
		}
		return curAliases().remove(alias) != null;
	}

	/**
	 * Returns if there is an account linked the given alias.
	 *
	 * @param alias the alias of interest
	 * @return whether there is a linked account
	 */
	public boolean contains(final ByteString alias) {
		return curAliases().containsKey(alias);
	}

	/**
	 * Returns the entityNum for the given alias
	 *
	 * @param alias
	 * 		alias of the accountId
	 * @return EntityNum mapped to the given alias.
	 */
	public EntityNum lookupIdBy(final ByteString alias) {
		return curAliases().getOrDefault(alias, MISSING_NUM);
	}

	private Map<ByteString, EntityNum> curAliases() {
		return aliases.get();
	}

	@VisibleForTesting
	Map<ByteString, EntityNum> getAliases() {
		return curAliases();
	}
}
