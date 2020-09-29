package com.hedera.test.factories.sigs;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hedera.services.utils.MiscUtils;
import com.hedera.test.factories.keys.KeyFactory;
import com.hedera.test.factories.keys.KeyTree;
import com.hedera.test.factories.keys.KeyTreeLeaf;
import com.hedera.test.factories.keys.KeyTreeListNode;
import com.hedera.test.factories.keys.KeyTreeNode;
import com.hedera.test.factories.keys.KeyTreeThresholdNode;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Signature;
import com.hederahashgraph.api.proto.java.SignatureList;
import com.hederahashgraph.api.proto.java.ThresholdSignature;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hedera.services.legacy.proto.utils.SignatureGenerator;
import com.swirlds.common.crypto.SignatureType;

import java.security.PrivateKey;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import static com.hedera.services.legacy.proto.utils.SignatureGenerator.signBytes;
import static java.util.stream.Collectors.toList;

public class SigFactory {
	private final SigMapGenerator sigMapGen;
	public static final byte[] NONSENSE_RSA_SIG = "MOME".getBytes();
	public static final byte[] NONSENSE_ECDSA_SIG = "OUTGRABE".getBytes();

	public SigFactory() {
		this(SigMapGenerator.withUniquePrefixes());
	}
	public SigFactory(SigMapGenerator sigMapGen) {
		this.sigMapGen = sigMapGen;
	}

	public static byte[] signUnchecked(byte[] data, PrivateKey pk) {
		try {
			return MiscUtils.commonsHexToBytes(signBytes(data, pk));
		} catch (Exception e) {
			e.printStackTrace();
			throw new IllegalStateException("Impossible signing error!");
		}
	}

	private Signature asHederaSignature(KeyTreeNode node, byte[] data, KeyFactory factory) {
		if (node instanceof KeyTreeLeaf) {
			@SuppressWarnings("unchecked")
			KeyTreeLeaf leaf = (KeyTreeLeaf)node;
			if (leaf.isUsedToSign()) {
				if (leaf.getSigType() == SignatureType.ED25519) {
					byte[] sig = signUnchecked(data, factory.lookupPrivateKey(leaf.asKey(factory)));
					return Signature.newBuilder().setEd25519(ByteString.copyFrom(sig)).build();
				} else if (leaf.getSigType() == SignatureType.RSA) {
					return Signature.newBuilder().setRSA3072(ByteString.copyFrom(NONSENSE_RSA_SIG)).build();
				} else if (leaf.getSigType() == SignatureType.ECDSA) {
					return Signature.newBuilder().setECDSA384(ByteString.copyFrom(NONSENSE_ECDSA_SIG)).build();
				}
			} else {
				return Signature.getDefaultInstance();
			}
		} else if (node instanceof KeyTreeThresholdNode) {
			@SuppressWarnings("unchecked")
			KeyTreeThresholdNode thresholdNode = (KeyTreeThresholdNode)node;
			return Signature.newBuilder()
					.setThresholdSignature(
							ThresholdSignature.newBuilder()
									.setSigs(
											SignatureList.newBuilder()
													.addAllSigs(
														thresholdNode.getChildren().stream()
																.map(child -> asHederaSignature(child, data, factory))
																.collect(toList())
													).build()
									).build()
					).build();
		} else if (node instanceof KeyTreeListNode) {
			@SuppressWarnings("unchecked")
			KeyTreeListNode listNode = (KeyTreeListNode)node;
			return Signature.newBuilder()
					.setSignatureList(
							SignatureList.newBuilder()
									.addAllSigs(
											listNode.getChildren().stream()
													.map(child -> asHederaSignature(child, data, factory))
													.collect(toList())

									).build()

					).build();

		}
		throw new AssertionError("Impossible node type!");
	}

	public Transaction signWithSigMap(Transaction.Builder txn, List<KeyTree> signers) throws Throwable {
		return signWithSigMap(txn, signers, KeyFactory.getDefaultInstance());
	}
	public Transaction signWithSigMap(
			Transaction.Builder txn,
			List<KeyTree> signers,
			KeyFactory factory
	) throws Throwable {
		SimpleSigning signing = new SimpleSigning(CommonUtils.extractTransactionBodyBytes(txn), signers, factory);
		List<Map.Entry<byte[], byte[]>> sigs = signing.completed();
		txn.setSigMap(sigMapGen.generate(sigs, signing.sigTypes()));
		return txn.build();
	}

	private static class SimpleSigning {
		private final byte[] data;
		private final KeyFactory factory;
		private final List<KeyTree> signers;
		private final Set<String> used = new HashSet<>();
		private final List<SignatureType> sigTypes = new ArrayList<>();
		private final List<Map.Entry<byte[], byte[]>> keySigs = new ArrayList<>();

		public SimpleSigning(byte[] data, List<KeyTree> signers, KeyFactory factory) {
			this.data = data;
			this.signers = signers;
			this.factory = factory;
		}

		public Supplier<SignatureType> sigTypes() {
			return new Supplier<>() {
				private int i = 0;

				@Override
				public SignatureType get() {
					return sigTypes.get(i++);
				}
			};
		}

		public List<Map.Entry<byte[], byte[]>> completed() throws Throwable {
			for (KeyTree signer : signers) {
				signRecursively(signer.getRoot());
			}
			return keySigs;
		}

		private void signRecursively(KeyTreeNode node) throws Throwable {
			if (node instanceof KeyTreeLeaf) {
				if (((KeyTreeLeaf)node).isUsedToSign()) {
					signIfNecessary(node.asKey(factory));
				}
			} else if (node instanceof KeyTreeListNode) {
				for (KeyTreeNode child : ((KeyTreeListNode)node).getChildren()) {
					signRecursively(child);
				}
			}
		}

		private void signIfNecessary(Key key) throws Throwable {
			String pubKeyHex = KeyFactory.asPubKeyHex(key);
			if (!used.contains(pubKeyHex)) {
				signFor(pubKeyHex, key);
				used.add(pubKeyHex);
			}
		}

		private void signFor(String pubKeyHex, Key key) throws Throwable {
			SignatureType sigType = sigTypeOf(key);
			if (sigType == SignatureType.ED25519) {
				PrivateKey signer = factory.lookupPrivateKey(pubKeyHex);
				byte[] sig = MiscUtils.commonsHexToBytes(SignatureGenerator.signBytes(data, signer));
				keySigs.add(new AbstractMap.SimpleEntry<>(key.getEd25519().toByteArray(), sig));
			} else if (sigType == SignatureType.ECDSA) {
				keySigs.add(new AbstractMap.SimpleEntry<>(key.getECDSA384().toByteArray(), NONSENSE_ECDSA_SIG));
			} else if (sigType == SignatureType.RSA) {
				keySigs.add(new AbstractMap.SimpleEntry<>(key.getRSA3072().toByteArray(), NONSENSE_RSA_SIG));
			}
			sigTypes.add(sigType);
		}

		private SignatureType sigTypeOf(Key key) {
			if (key.getEd25519() != ByteString.EMPTY) {
				return SignatureType.ED25519;
			} else if (key.getECDSA384() != ByteString.EMPTY) {
				return SignatureType.ECDSA;
			} else if (key.getRSA3072() != ByteString.EMPTY) {
				return SignatureType.RSA;
			}
			throw new AssertionError("Simple gRPC key has no public key!");
		}
	}
}
