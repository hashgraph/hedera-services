/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.crypto;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.common.utility.CommonUtils.nameToAlias;
import static com.swirlds.logging.legacy.LogMarker.CERTIFICATES;
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.STARTUP;
import static com.swirlds.platform.crypto.CryptoConstants.PUBLIC_KEYS_FILE;
import static com.swirlds.platform.crypto.KeyCertPurpose.SIGNING;

import com.hedera.cryptography.bls.BlsKeyPair;
import com.hedera.cryptography.bls.GroupAssignment;
import com.hedera.cryptography.bls.SignatureSchema;
import com.hedera.cryptography.pairings.api.Curve;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.CryptographyException;
import com.swirlds.common.crypto.config.CryptoConfig;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.config.api.Configuration;
import com.swirlds.logging.legacy.LogMarker;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.config.BasicConfig;
import com.swirlds.platform.config.PathsConfig;
import com.swirlds.platform.network.PeerInfo;
import com.swirlds.platform.state.address.AddressBookNetworkUtils;
import com.swirlds.platform.system.SystemExitCode;
import com.swirlds.platform.system.SystemExitUtils;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import javax.security.auth.x500.X500Principal;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * A collection of various static crypto methods
 */
public final class CryptoStatic {
    private static final Logger logger = LogManager.getLogger(CryptoStatic.class);
    private static final int SERIAL_NUMBER_BITS = 64;
    private static final int MASTER_KEY_MULTIPLIER = 157;
    private static final int SWIRLD_ID_MULTIPLIER = 163;
    private static final int BITS_IN_BYTE = 8;
    private static final String ADDRESS_BOOK_MUST_NOT_BE_NULL = "addressBook must not be null";

    static {
        // used to generate certificates
        Security.addProvider(new BouncyCastleProvider());
    }

    private CryptoStatic() {}

    /**
     * return a key-value pair as is found in a distinguished name in a n x509 certificate. For example "CN=Alice" or
     * ",CN=Alice" (if it isn't the first). This returned value (without the comma) is called a "relative distinguished
     * name" in RFC4514. If the value is null or "", then it returns "". Otherwise, it sets separator[0] to "," and
     * returns the RDN.
     *
     * @param commaSeparator should initially be "" then "," for all calls thereafter
     * @param attributeType  the code, such as CN or STREET
     * @param attributeValue the value, such as "John Smith"
     * @return the RDN (if any), possibly preceded by a comma (if not first)
     */
    private static String rdn(String[] commaSeparator, String attributeType, String attributeValue) {
        if (attributeValue == null || attributeValue.equals("")) {
            return "";
        }
        // need to escape the 6 characters: \ " , ; < >
        // and spaces at start/end of string
        // and # at start of string.
        // The RFC requires + to be escaped if it doesn't combine two separate values,
        // but that escape must be done by the caller. It won't be done here.
        attributeValue = attributeValue.replace("\\", "\\\\");
        attributeValue = attributeValue.replace("\"", "\\\"");
        attributeValue = attributeValue.replace(",", "\\,");
        attributeValue = attributeValue.replace(";", "\\;");
        attributeValue = attributeValue.replace("<", "\\<");
        attributeValue = attributeValue.replace(">", "\\>");
        attributeValue = attributeValue.replaceAll(" $", "\\ ");
        attributeValue = attributeValue.replaceAll("^ ", "\\ ");
        attributeValue = attributeValue.replaceAll("^#", "\\#");
        String s = commaSeparator[0] + attributeType + "=" + attributeValue;
        commaSeparator[0] = ",";
        return s;
    }

    /**
     * Return the distinguished name for an entity for use in an x509 certificate, such as "CN=Alice+Bob, L=Los Angeles,
     * ST=CA, C=US". Any component that is either null or the empty string will be left out. If there are multiple
     * answers for a field, separate them with plus signs, such as "Alice+Bob" for both Alice and Bob. For the
     * organization, the list of names should go from the top level to the bottom (most general to least). For the
     * domain, it should go from general to specific, such as {"com", "acme","www"}.
     * <p>
     * This method will take care of escaping values, so it is ok to pass in a common name such as "#John Smith, Jr. ",
     * which is automatically converted to "\#John Smith\, Jr\.\ ", which follows the rules in the RFC, such as escaping
     * the space at the end but not the one in the middle.
     * <p>
     * The only exception is the plus sign. If the string "Alice+Bob" is passed in for the common name, that is
     * interpreted as two names, "Alice" and "Bob". If there is a single person named "Alice+Bob", then it must be
     * escaped by passing in the string "Alice\+Bob", which would be typed as a Java literal as "Alice\\+Bob".
     * <p>
     * This follows RFC 4514, which gives these distinguished name string representations:
     *
     * <pre>
     * String  X.500 AttributeType
     * ------  --------------------------------------------
     * CN      commonName (2.5.4.3)
     * L       localityName (2.5.4.7)
     * ST      stateOrProvinceName (2.5.4.8)
     * O       organizationName (2.5.4.10)
     * OU      organizationalUnitName (2.5.4.11)
     * C       countryName (2.5.4.6)
     * STREET  streetAddress (2.5.4.9)
     * DC      domainComponent (0.9.2342.19200300.100.1.25)
     * UID     userId (0.9.2342.19200300.100.1.1)
     * </pre>
     *
     * @param commonName name such as "John Smith" or "Acme Inc"
     * @return the distinguished name, suitable for passing to generateCertificate()
     */
    static String distinguishedName(String commonName) {
        String[] commaSeparator = new String[] {""};
        return rdn(commaSeparator, "CN", commonName)
                + rdn(commaSeparator, "O", null)
                + rdn(commaSeparator, "STREET", null)
                + rdn(commaSeparator, "L", null)
                + rdn(commaSeparator, "ST", null)
                + rdn(commaSeparator, "C", null)
                + rdn(commaSeparator, "UID", null);
    }

    /**
     * Create a signed X.509 Certificate. The distinguishedName parameter can be generated by calling
     * distinguishedName(). In the distinguished name, the UID should be the memberId used in the AddressBook here. The
     * certificate only contains the public key from the given key pair, though it uses the private key during the self
     * signature.
     * <p>
     * The certificate records that pair.publicKey is owned by distinguishedName. This certificate is signed by a
     * Certificate Authority (CA), whose name is CaDistinguishedName and whose key pair is CaPair.
     * <p>
     * In Swirlds, each member creates a separate certificate for each of their 3 key pairs (signing, agreement). The
     * signing certificate is self-signed, and is treated as if it were a CA. The other two certificates are each signed
     * by the signing key pair. So for either of them, the complete certificate chain consists of two certificates.
     * <p>
     * For the validity dates, if null is passed in, then it starts in 2000 and goes to 2100. Another alternative is to
     * pass in (new Date()) for the start, and new Date(from.getTime() + 365 * 86400000l) for the end to make it valid
     * from now for the next 365 days.
     *
     * @param distinguishedName   the X.509 Distinguished Name, such as is returned by distName()
     * @param pair                the KeyPair whose public key is to be listed as belonging to distinguishedName
     * @param caDistinguishedName the name of the CA (which in Swirlds is always the same as distinguishedName)
     * @param caPair              the KeyPair of the CA (which in Swirlds is always the signing key pair)
     * @param secureRandom        the random number generator used to generate the certificate
     * @return the self-signed certificate
     * @throws KeyGeneratingException in any issue occurs
     */
    public static X509Certificate generateCertificate(
            String distinguishedName,
            KeyPair pair,
            String caDistinguishedName,
            KeyPair caPair,
            SecureRandom secureRandom)
            throws KeyGeneratingException {
        try {
            X509v3CertificateBuilder v3CertBldr = new JcaX509v3CertificateBuilder(
                    new X500Principal(caDistinguishedName), // issuer
                    new BigInteger(SERIAL_NUMBER_BITS, secureRandom), // serial number
                    Date.from(CryptoConstants.DEFAULT_VALID_FROM), // start time
                    Date.from(CryptoConstants.DEFAULT_VALID_TO), // expiry time
                    new X500Principal(distinguishedName), // subject
                    pair.getPublic()); // subject public key

            JcaContentSignerBuilder signerBuilder = new JcaContentSignerBuilder(CryptoConstants.SIG_TYPE2)
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME);
            return new JcaX509CertificateConverter()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .getCertificate(v3CertBldr.build(signerBuilder.build(caPair.getPrivate())));
        } catch (CertificateException | OperatorCreationException e) {
            throw new KeyGeneratingException("Could not generate certificate!", e);
        }
    }

    /**
     * Decode a X509Certificate from a byte array that was previously obtained via X509Certificate.getEncoded().
     *
     * @param encoded a byte array with an encoded representation of a certificate
     * @return the certificate reconstructed from its encoded form
     */
    @NonNull
    public static X509Certificate decodeCertificate(@NonNull final byte[] encoded) {
        try (final InputStream in = new ByteArrayInputStream(encoded)) {
            final CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) factory.generateCertificate(in);
        } catch (CertificateException | IOException e) {
            throw new CryptographyException(e);
        }
    }

    /**
     * Create a new trust store that is initially empty, but will later have all the members' key agreement public key
     * certificates added to it.
     *
     * @return the empty KeyStore to be used as a trust store for TLS for syncs.
     * @throws KeyStoreException if there is no provider that supports {@link CryptoConstants#KEYSTORE_TYPE}
     */
    static KeyStore createEmptyTrustStore() throws KeyStoreException {
        final KeyStore trustStore;
        try {
            trustStore = KeyStore.getInstance(CryptoConstants.KEYSTORE_TYPE);
            trustStore.load(null);
        } catch (CertificateException | IOException | NoSuchAlgorithmException e) {
            // cannot be thrown when calling load(null)
            throw new CryptographyException(e);
        }
        return trustStore;
    }

    /**
     * See {@link SignatureVerifier#verifySignature(Bytes, Bytes, PublicKey)}
     */
    public static boolean verifySignature(
            @NonNull final Bytes data, @NonNull final Bytes signature, @NonNull final PublicKey publicKey) {
        Objects.requireNonNull(data);
        Objects.requireNonNull(signature);
        Objects.requireNonNull(publicKey);
        try {
            final Signature sig = Signature.getInstance(CryptoConstants.SIG_TYPE2, CryptoConstants.SIG_PROVIDER);
            sig.initVerify(publicKey);
            data.updateSignature(sig);
            return signature.verifySignature(sig);
        } catch (final NoSuchAlgorithmException | NoSuchProviderException e) {
            // should never happen
            throw new CryptographyException("Exception occurred while validating a signature:", e, LogMarker.EXCEPTION);
        } catch (final InvalidKeyException | SignatureException e) {
            logger.error(LogMarker.EXCEPTION.getMarker(), "Exception occurred while validating a signature:", e);
            return false;
        }
    }

    /**
     * Loads all data from a .pfx file into a KeyStore
     *
     * @param file     the file to load from
     * @param password the encryption password
     * @return a KeyStore with all certificates and keys found in the file
     * @throws KeyStoreException   if {@link #createEmptyTrustStore()} throws
     * @throws KeyLoadingException if the file is empty or another issue occurs while reading it
     */
    @NonNull
    public static KeyStore loadKeys(@NonNull final Path file, @NonNull final char[] password)
            throws KeyStoreException, KeyLoadingException {
        final KeyStore store = createEmptyTrustStore();
        try (final FileInputStream fis = new FileInputStream(file.toFile())) {
            store.load(fis, password);
            if (store.size() == 0) {
                throw new KeyLoadingException("there are no valid keys or certificates in " + file);
            }
        } catch (final IOException | NoSuchAlgorithmException | CertificateException | KeyStoreException e) {
            throw new KeyLoadingException("there was a problem reading: " + file, e);
        }

        return store;
    }

    /**
     * @param memberName the name of the key owner
     * @return the file name that is supposed to store the private key for the supplied member
     */
    private static String getPrivateKeysFileName(final String memberName) {
        return "private-" + memberName + ".pfx";
    }

    /**
     * Try to load the public.pfx and private-*.pfx key stores from disk. If successful, it will return an array
     * containing the public.pfx followed by each private-*.pfx in the order that the names are given in the input. If
     * public.pfx is missing, or even one of the private-*.pfx files is missing, or one of those files fails to load
     * properly, then it returns null. It does NOT return a partial list with the ones that worked.
     *
     * @param addressBook the address book of the network
     * @param keysDirPath the key directory, such as /user/test/sdk/data/key/
     * @param password    the password used to protect the PKCS12 key stores containing the node RSA public/private key
     *                    pairs
     * @return map of key stores
     * @throws KeyStoreException   if there is no provider that supports {@link CryptoConstants#KEYSTORE_TYPE}
     * @throws KeyLoadingException in an issue occurs while loading keys and certificates
     * @deprecated use {@link EnhancedKeyStoreLoader} instead.
     */
    @NonNull
    @Deprecated(since = "0.47.0", forRemoval = false)
    static Map<NodeId, KeysAndCerts> loadKeysAndCerts(
            @NonNull final AddressBook addressBook, @NonNull final Path keysDirPath, @NonNull final char[] password)
            throws KeyStoreException, KeyLoadingException, UnrecoverableKeyException, NoSuchAlgorithmException,
                    KeyGeneratingException, NoSuchProviderException {
        Objects.requireNonNull(addressBook, ADDRESS_BOOK_MUST_NOT_BE_NULL);
        Objects.requireNonNull(keysDirPath, "keysDirPath must not be null");
        Objects.requireNonNull(password, "password must not be null");
        final int n = addressBook.getSize();

        final List<String> names = new ArrayList<>();

        for (int i = 0; i < addressBook.getSize(); i++) {
            final NodeId nodeId = addressBook.getNodeId(i);
            final Address add = addressBook.getAddress(nodeId);
            final String name = nameToAlias(add.getSelfName());
            names.add(name);
        }

        final KeyStore allPublic = loadKeys(keysDirPath.resolve(PUBLIC_KEYS_FILE), password);

        final PublicStores publicStores = PublicStores.fromAllPublic(allPublic, names);

        final Map<NodeId, KeysAndCerts> keysAndCerts = new HashMap<>();
        for (int i = 0; i < n; i++) {
            final NodeId nodeId = addressBook.getNodeId(i);
            final Address address = addressBook.getAddress(nodeId);
            if (!AddressBookNetworkUtils.isLocal(address)) {
                // in case we are not creating keys but loading them from disk, we do not need to create
                // a KeysAndCerts object for every node, just the local ones
                continue;
            }
            final String name = nameToAlias(addressBook.getAddress(nodeId).getSelfName());
            final KeyStore privateKS = loadKeys(keysDirPath.resolve(getPrivateKeysFileName(name)), password);

            keysAndCerts.put(
                    nodeId, KeysAndCerts.loadExistingAndCreateAgrKeyIfMissing(name, password, privateKS, publicStores));
        }
        copyPublicKeys(publicStores, addressBook);

        return keysAndCerts;
    }

    /**
     * This method is designed to generate all a user's keys from their master key, to help with key recovery if their
     * computer is erased.
     * <p>
     * We follow the "CNSA Suite" (Commercial National Security Algorithm), which is the current US government standard
     * for protecting information up to and including Top Secret:
     * <p>
     * <a
     * href="https://www.iad.gov/iad/library/ia-guidance/ia-solutions-for-classified/algorithm-guidance/commercial-national-security-algorithm-suite-factsheet.cfm">...</a>
     * <p>
     * The CNSA standard specifies AES-256, SHA-384, RSA, ECDH and ECDSA. So that is what is used here. Their intent
     * appears to be that AES and SHA will each have 128 bits of post-quantum security, against Grover's and the BHT
     * algorithm, respectively. Of course, ECDH and ECDSA aren't post-quantum, but AES-256 and SHA-384 are (as far as we
     * know).
     *
     * @param addressBook the address book of the network
     * @throws ExecutionException   if {@link KeysAndCerts#generate(String, byte[], byte[], byte[], PublicStores)}
     *                              throws an exception, it will be wrapped in an ExecutionException
     * @throws InterruptedException if this thread is interrupted
     * @throws KeyStoreException    if there is no provider that supports {@link CryptoConstants#KEYSTORE_TYPE}
     */
    @NonNull
    public static Map<NodeId, KeysAndCerts> generateKeysAndCerts(@NonNull final AddressBook addressBook)
            throws ExecutionException, InterruptedException, KeyStoreException {
        Objects.requireNonNull(addressBook, ADDRESS_BOOK_MUST_NOT_BE_NULL);

        final byte[] masterKey = new byte[CryptoConstants.SYM_KEY_SIZE_BYTES];
        final byte[] swirldId = new byte[CryptoConstants.HASH_SIZE_BYTES];

        final PublicStores publicStores = new PublicStores();

        final int n = addressBook.getSize();
        final Map<NodeId, Future<KeysAndCerts>> futures = HashMap.newHashMap(n);
        try (final ExecutorService threadPool =
                Executors.newCachedThreadPool(new ThreadConfiguration(getStaticThreadManager())
                        .setComponent("browser")
                        .setThreadName("crypto-generate")
                        .setDaemon(false)
                        .buildFactory())) {
            for (int i = 0; i < n; i++) {
                final NodeId nodeId = addressBook.getNodeId(i);
                final Address address = addressBook.getAddress(nodeId);
                final String name = nameToAlias(address.getSelfName());
                for (int j = 0; j < masterKey.length; j++) {
                    masterKey[j] = (byte) (j * MASTER_KEY_MULTIPLIER);
                }
                for (int j = 0; j < swirldId.length; j++) {
                    swirldId[j] = (byte) (j * SWIRLD_ID_MULTIPLIER);
                }
                masterKey[0] = (byte) i;
                masterKey[1] = (byte) (i >> BITS_IN_BYTE);

                // Crypto objects will be created in parallel. The process of creating a Crypto object is
                // very CPU intensive even if the keys are loaded from the hard drive, so making it parallel
                // greatly reduces the time it takes to create them all.
                byte[] masterKeyClone = masterKey.clone();
                byte[] swirldIdClone = swirldId.clone();
                final int memId = i;
                futures.put(
                        nodeId,
                        threadPool.submit(() -> KeysAndCerts.generate(
                                name, masterKeyClone, swirldIdClone, CommonUtils.intToBytes(memId), publicStores)));
            }
            final Map<NodeId, KeysAndCerts> keysAndCerts = futuresToMap(futures);
            threadPool.shutdown();
            // After the keys have been generated or loaded, they are then copied to the address book
            try {
                copyPublicKeys(publicStores, addressBook);
            } catch (KeyLoadingException e) {
                // this should not be possible since we just generated the certificates
                throw new CryptographyException(e);
            }
            return keysAndCerts;
        }
    }

    /**
     * Copies public keys from the stores provided to the address book
     *
     * @param publicStores the stores to read the keys from
     * @param addressBook  the address book to modify
     * @throws KeyLoadingException if {@link PublicStores#getPublicKey(KeyCertPurpose, String)} throws
     */
    static void copyPublicKeys(final PublicStores publicStores, final AddressBook addressBook)
            throws KeyLoadingException {
        for (int i = 0; i < addressBook.getSize(); i++) {
            final NodeId nodeId = addressBook.getNodeId(i);
            final Address add = addressBook.getAddress(nodeId);
            final String name = nameToAlias(add.getSelfName());
            final X509Certificate sigCert = publicStores.getCertificate(SIGNING, name);
            // the agreement key is never used from the address book anymore and is left null.
            addressBook.add(addressBook.getAddress(nodeId).copySetSigCert(sigCert));
        }
    }

    /**
     * Wait for all futures to finish and return the results as an array
     *
     * @param futures the futures to wait for
     * @param <T>     the result and array type
     * @return all results
     * @throws ExecutionException   if {@link Future#get} throws
     * @throws InterruptedException if {@link Future#get} throws
     */
    @NonNull
    private static <T> Map<NodeId, T> futuresToMap(@NonNull final Map<NodeId, Future<T>> futures)
            throws ExecutionException, InterruptedException {
        final Map<NodeId, T> map = new HashMap<>();
        for (Map.Entry<NodeId, Future<T>> entry : futures.entrySet()) {
            map.put(entry.getKey(), entry.getValue().get());
        }
        return map;
    }

    /**
     * Return the nondeterministic secure random number generator stored in this Crypto instance. If it doesn't already
     * exist, create it.
     *
     * @return the stored SecureRandom object
     */
    public static SecureRandom getNonDetRandom() {
        final SecureRandom nonDetRandom;
        try {
            nonDetRandom = SecureRandom.getInstanceStrong();
        } catch (final NoSuchAlgorithmException e) {
            throw new CryptographyException(e, EXCEPTION);
        }
        // call nextBytes before setSeed, because some algorithms (like SHA1PRNG) become
        // deterministic if you don't. This call might hang if the OS has too little entropy
        // collected. Or it might be that nextBytes doesn't hang but getSeed does. The behavior is
        // different for different choices of OS, Java version, and JDK library implementation.
        nonDetRandom.nextBytes(new byte[1]);
        return nonDetRandom;
    }

    /**
     * Create {@link KeysAndCerts} objects for all nodes in the address book.
     *
     * @param addressBook   the current address book
     * @param configuration the current configuration
     * @return a map of KeysAndCerts objects, one for each node
     */
    public static Map<NodeId, KeysAndCerts> initNodeSecurity(
            @NonNull final AddressBook addressBook, @NonNull final Configuration configuration) {
        Objects.requireNonNull(addressBook, ADDRESS_BOOK_MUST_NOT_BE_NULL);
        Objects.requireNonNull(configuration, "configuration must not be null");

        final PathsConfig pathsConfig = configuration.getConfigData(PathsConfig.class);
        final CryptoConfig cryptoConfig = configuration.getConfigData(CryptoConfig.class);
        final BasicConfig basicConfig = configuration.getConfigData(BasicConfig.class);

        final Map<NodeId, KeysAndCerts> keysAndCerts;
        try {
            if (basicConfig.loadKeysFromPfxFiles()) {
                try (final Stream<Path> list = Files.list(pathsConfig.getKeysDirPath())) {
                    CommonUtils.tellUserConsole("Reading crypto keys from the files here:   "
                            + Arrays.toString(list.map(p -> p.getFileName().toString())
                                    .filter(fileName -> fileName.endsWith("pfx") || fileName.endsWith("pem"))
                                    .toArray()));
                }

                logger.debug(STARTUP.getMarker(), "About to start loading keys");
                if (cryptoConfig.enableNewKeyStoreModel()) {
                    logger.debug(STARTUP.getMarker(), "Reading keys using the enhanced key loader");
                    keysAndCerts = EnhancedKeyStoreLoader.using(addressBook, configuration)
                            .migrate()
                            .scan()
                            .generate()
                            .verify()
                            .injectInAddressBook()
                            .keysAndCerts();
                } else {
                    logger.debug(STARTUP.getMarker(), "Reading keys using the legacy key loader");
                    keysAndCerts = loadKeysAndCerts(
                            addressBook,
                            pathsConfig.getKeysDirPath(),
                            cryptoConfig.keystorePassword().toCharArray());
                }
                logger.debug(STARTUP.getMarker(), "Done loading keys");
            } else {
                // if there are no keys on the disk, then create our own keys
                CommonUtils.tellUserConsole(
                        "Creating keys, because there are no files in " + pathsConfig.getKeysDirPath());
                logger.debug(STARTUP.getMarker(), "About to start creating generating keys");
                keysAndCerts = generateKeysAndCerts(addressBook);
                logger.debug(STARTUP.getMarker(), "Done generating keys");
            }
        } catch (final InterruptedException
                | ExecutionException
                | KeyStoreException
                | KeyLoadingException
                | UnrecoverableKeyException
                | NoSuchAlgorithmException
                | IOException
                | KeyGeneratingException
                | NoSuchProviderException e) {
            logger.error(EXCEPTION.getMarker(), "Exception while loading/generating keys", e);
            if (Utilities.isRootCauseSuppliedType(e, NoSuchAlgorithmException.class)
                    || Utilities.isRootCauseSuppliedType(e, NoSuchProviderException.class)) {
                CommonUtils.tellUserConsolePopup(
                        "ERROR",
                        "ERROR: This Java installation does not have the needed cryptography " + "providers installed");
            }
            CommonUtils.tellUserConsole(e.getMessage() + ": " + e.getCause().getMessage());
            SystemExitUtils.exitSystem(SystemExitCode.KEY_LOADING_FAILED);
            throw new CryptographyException(e); // will never reach this line due to exit above
        }

        final String msg = basicConfig.loadKeysFromPfxFiles() ? "Certificate loaded: {}" : "Certificate generated: {}";

        keysAndCerts.forEach((nodeId, keysAndCertsForNode) -> {
            if (keysAndCertsForNode == null) {
                logger.error(EXCEPTION.getMarker(), "No keys and certs for node {}", nodeId);
                return;
            }
            logger.debug(CERTIFICATES.getMarker(), "Node ID: {}", nodeId);
            logger.debug(CERTIFICATES.getMarker(), msg, keysAndCertsForNode.sigCert());
            logger.debug(CERTIFICATES.getMarker(), msg, keysAndCertsForNode.agrCert());
        });

        return keysAndCerts;
    }

    /**
     * Create a trust store that contains the public keys of all the members in the peer list
     *
     * @param peers all the peers in the network
     * @return a trust store containing the public keys of all the members
     * @throws KeyStoreException if there is no provider that supports {@link CryptoConstants#KEYSTORE_TYPE}
     */
    public static @NonNull KeyStore createPublicKeyStore(@NonNull final List<PeerInfo> peers) throws KeyStoreException {
        Objects.requireNonNull(peers);
        final KeyStore store = CryptoStatic.createEmptyTrustStore();
        for (final PeerInfo peer : peers) {
            final String name = nameToAlias(peer.nodeName());
            final Certificate sigCert = peer.signingCertificate();
            store.setCertificateEntry(SIGNING.storeName(name), sigCert);
        }
        return store;
    }

    /**
     * Generate a {@link BlsKeyPair} using a {@link SignatureSchema} and a {@link SecureRandom} instance
     * @return a new {@link BlsKeyPair}
     * @throws NoSuchAlgorithmException the algorithm is not supported
     */
    public static BlsKeyPair generateBlsKeyPair() throws NoSuchAlgorithmException {
        final SignatureSchema SIGNATURE_SCHEMA =
                SignatureSchema.create(Curve.ALT_BN128, GroupAssignment.SHORT_SIGNATURES);
        final SecureRandom secureRandom = SecureRandom.getInstanceStrong();
        return BlsKeyPair.generate(SIGNATURE_SCHEMA, secureRandom);
    }
}
