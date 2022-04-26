package com.hedera.services.bdd.spec.transactions.contract;

import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import com.esaulpaugh.headlong.util.Integers;
import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.spec.keys.KeyGenerator;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.spec.transactions.file.HapiFileCreate;
import com.hedera.services.bdd.suites.contract.Utils;
import com.hedera.services.ethereum.EthTxData;
import com.hedera.services.ethereum.EthTxSigs;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.EthereumTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.ObjLongConsumer;
import java.util.function.Supplier;

import static com.hedera.services.bdd.spec.transactions.TxnFactory.bannerWith;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.equivAccount;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.solidityIdFrom;
import static com.hedera.services.bdd.spec.transactions.contract.HapiContractCall.doGasLookup;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class HapiEthereumContractCreate extends HapiTxnOp<HapiEthereumContractCreate> {
    static final Key MISSING_ADMIN_KEY = Key.getDefaultInstance();
    static final Key DEPRECATED_CID_ADMIN_KEY =
            Key.newBuilder().setContractID(ContractID.newBuilder().setContractNum(1_234L)).build();
    static final Logger log = LogManager.getLogger(HapiEthereumContractCreate.class);
    private static final int BYTES_PER_KB = 1024;
    private static final int MAX_CALL_DATA_SIZE = 6 * BYTES_PER_KB;

    private static final BigInteger WEIBARS_TO_TINYBARS = BigInteger.valueOf(10_000_000_000L);
    private Key adminKey;
    private boolean omitAdminKey = false;
    private boolean makeImmutable = false;
    private boolean advertiseCreation = false;
    private boolean shouldAlsoRegisterAsAccount = true;
    private boolean useDeprecatedAdminKey = false;
    private final String contract;
    private OptionalLong gas = OptionalLong.empty();
    Optional<String> key = Optional.empty();
    Optional<Long> autoRenewPeriodSecs = Optional.empty();
    Optional<Long> balance = Optional.empty();
    Optional<SigControl> adminKeyControl = Optional.empty();
    Optional<KeyFactory.KeyType> adminKeyType = Optional.empty();
    Optional<String> memo = Optional.empty();
    Optional<String> bytecodeFile = Optional.empty();
    Optional<Supplier<String>> bytecodeFileFn = Optional.empty();
    Optional<Consumer<HapiSpecRegistry>> successCb = Optional.empty();
    Optional<String> abi = Optional.empty();
    Optional<Object[]> args = Optional.empty();
    Optional<ObjLongConsumer<ResponseCodeEnum>> gasObserver = Optional.empty();
    Optional<LongConsumer> newNumObserver = Optional.empty();
    private Optional<String> proxy = Optional.empty();
    private Optional<Supplier<String>> explicitHexedParams = Optional.empty();

    private EthTxData.EthTransactionType type;
    private byte[] chainId = Integers.toBytes(298);
    private long nonce;
    private long gasPrice;
    private long maxPriorityGas;
    private long gasLimit;
    private Optional<FileID> ethFileID = Optional.empty();
    private Optional<Long> maxGasAllowance = Optional.empty();
    private String privateKeyRef;

    public HapiEthereumContractCreate exposingNumTo(LongConsumer obs) {
        newNumObserver = Optional.of(obs);
        return this;
    }

    public HapiEthereumContractCreate withExplicitParams(final Supplier<String> supplier) {
        explicitHexedParams = Optional.of(supplier);
        return this;
    }

    public HapiEthereumContractCreate proxy(String proxy) {
        this.proxy = Optional.of(proxy);
        return this;
    }

    public HapiEthereumContractCreate advertisingCreation() {
        advertiseCreation = true;
        return this;
    }

    public HapiEthereumContractCreate(String contract) {
        this.contract = contract;
    }

    public HapiEthereumContractCreate(String contract, String abi, Object... args) {
        this.contract = contract;
        this.abi = Optional.of(abi);
        this.args = Optional.of(args);
    }

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.EthereumTransaction;
    }

    @Override
    protected HapiEthereumContractCreate self() {
        return this;
    }

    @Override
    protected Key lookupKey(HapiApiSpec spec, String name) {
        return name.equals(contract) ? adminKey : spec.registry().getKey(name);
    }

    public HapiEthereumContractCreate exposingGasTo(ObjLongConsumer<ResponseCodeEnum> gasObserver) {
        this.gasObserver = Optional.of(gasObserver);
        return this;
    }

    public HapiEthereumContractCreate skipAccountRegistration() {
        shouldAlsoRegisterAsAccount = false;
        return this;
    }

    public HapiEthereumContractCreate uponSuccess(Consumer<HapiSpecRegistry> cb) {
        successCb = Optional.of(cb);
        return this;
    }

    public HapiEthereumContractCreate bytecode(String fileName) {
        bytecodeFile = Optional.of(fileName);
        return this;
    }

    public HapiEthereumContractCreate bytecode(Supplier<String> supplier) {
        bytecodeFileFn = Optional.of(supplier);
        return this;
    }

    public HapiEthereumContractCreate adminKey(KeyFactory.KeyType type) {
        adminKeyType = Optional.of(type);
        return this;
    }

    public HapiEthereumContractCreate adminKeyShape(SigControl controller) {
        adminKeyControl = Optional.of(controller);
        return this;
    }

    public HapiEthereumContractCreate autoRenewSecs(long period) {
        autoRenewPeriodSecs = Optional.of(period);
        return this;
    }

    public HapiEthereumContractCreate balance(long initial) {
        balance = Optional.of(initial);
        return this;
    }

    public HapiEthereumContractCreate gas(long amount) {
        gas = OptionalLong.of(amount);
        return this;
    }

    public HapiEthereumContractCreate entityMemo(String s) {
        memo = Optional.of(s);
        return this;
    }

    public HapiEthereumContractCreate omitAdminKey() {
        omitAdminKey = true;
        return this;
    }

    public HapiEthereumContractCreate immutable() {
        omitAdminKey = true;
        makeImmutable = true;
        return this;
    }

    public HapiEthereumContractCreate useDeprecatedAdminKey() {
        useDeprecatedAdminKey = true;
        return this;
    }

    public HapiEthereumContractCreate adminKey(String existingKey) {
        key = Optional.of(existingKey);
        return this;
    }

    public HapiEthereumContractCreate maxGasAllowance(long maxGasAllowance) {
        this.maxGasAllowance = Optional.of(maxGasAllowance);
        return this;
    }

    public HapiEthereumContractCreate signingWith(String signingWith) {
        this.privateKeyRef = signingWith;
        return this;
    }

    public HapiEthereumContractCreate type(EthTxData.EthTransactionType type) {
        this.type = type;
        return this;
    }

    public HapiEthereumContractCreate nonce(long nonce) {
        this.nonce = nonce;
        return this;
    }

    public HapiEthereumContractCreate gasPrice(long gasPrice) {
        this.gasPrice = gasPrice;
        return this;
    }

    public HapiEthereumContractCreate maxPriorityGas(long maxPriorityGas) {
        this.maxPriorityGas = maxPriorityGas;
        return this;
    }

    public HapiEthereumContractCreate gasLimit(long gasLimit) {
        this.gasLimit = gasLimit;
        return this;
    }

    @Override
    protected List<Function<HapiApiSpec, Key>> defaultSigners() {
        return (omitAdminKey || useDeprecatedAdminKey)
                ? super.defaultSigners()
                : List.of(spec -> spec.registry().getKey(effectivePayer(spec)), ignore -> adminKey);
    }

    @Override
    protected void updateStateOf(HapiApiSpec spec) throws Throwable {
        if (actualStatus != SUCCESS) {
            if (gasObserver.isPresent()) {
                doGasLookup(gas -> gasObserver.get().accept(actualStatus, gas), spec, txnSubmitted, true);
            }
            return;
        }
        final var newId = lastReceipt.getContractID();
        newNumObserver.ifPresent(obs -> obs.accept(newId.getContractNum()));
        if (shouldAlsoRegisterAsAccount) {
            spec.registry().saveAccountId(contract, equivAccount(lastReceipt.getContractID()));
        }
        spec.registry().saveKey(contract, (omitAdminKey || useDeprecatedAdminKey) ? MISSING_ADMIN_KEY : adminKey);
        spec.registry().saveContractId(contract, newId);
        final var otherInfoBuilder = ContractGetInfoResponse.ContractInfo.newBuilder()
                .setContractAccountID(solidityIdFrom(lastReceipt.getContractID()))
                .setMemo(memo.orElse(spec.setup().defaultMemo()))
                .setAutoRenewPeriod(
                        Duration.newBuilder().setSeconds(
                                autoRenewPeriodSecs.orElse(spec.setup().defaultAutoRenewPeriod().getSeconds())).build());
        if (!omitAdminKey && !useDeprecatedAdminKey) {
            otherInfoBuilder.setAdminKey(adminKey);
        }
        final var otherInfo = otherInfoBuilder.build();
        spec.registry().saveContractInfo(contract, otherInfo);
        successCb.ifPresent(cb -> cb.accept(spec.registry()));
        if (advertiseCreation) {
            String banner = "\n\n" + bannerWith(
                    String.format(
                            "Created contract '%s' with id '0.0.%d'.",
                            contract,
                            lastReceipt.getContractID().getContractNum()));
            log.info(banner);
        }
        if (gasObserver.isPresent()) {
            doGasLookup(gas -> gasObserver.get().accept(SUCCESS, gas), spec, txnSubmitted, true);
        }
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
        if (!omitAdminKey && !useDeprecatedAdminKey) {
            generateAdminKey(spec);
        }
        if (bytecodeFileFn.isPresent()) {
            bytecodeFile = Optional.of(bytecodeFileFn.get().get());
        }
        if (!bytecodeFile.isPresent()) {
            setBytecodeToDefaultContract(spec);
        }

        final var filePath = Utils.getResourcePath(bytecodeFile.get(), ".bin");
        final var fileContents = Utils.extractByteCode(filePath);

        byte[] callData = new byte[0];
        if(fileContents.toByteArray().length < MAX_CALL_DATA_SIZE) {
            callData = Bytes.fromHexString(new String(fileContents.toByteArray())).toArray();
        } else {
            ethFileID = Optional.of(TxnUtils.asFileId(bytecodeFile.get(), spec));
        }

        final var value = balance.isEmpty() ? BigInteger.ZERO : WEIBARS_TO_TINYBARS.multiply(BigInteger.valueOf(balance.get()));
        final var longTuple = TupleType.parse("(int64)");
        final var gasPriceBytes = Bytes.wrap(longTuple.encode(Tuple.of(gasPrice)).array()).toArray();
        final var maxPriorityGasBytes = Bytes.wrap(longTuple.encode(Tuple.of(maxPriorityGas)).array()).toArray();
        final var gasBytes = gas.isEmpty() ? new byte[] {} : Bytes.wrap(longTuple.encode(Tuple.of(gas.getAsLong())).array()).toArray();

        final var ethTxData = new EthTxData(null, type, chainId, nonce, gasPriceBytes,
                maxPriorityGasBytes, gasBytes, gasLimit,
                new byte[]{}, value, callData, new byte[]{}, 0, null, null, null);

        var key = spec.registry().getKey(privateKeyRef);
        final var privateKey = spec.keys().getPrivateKey(com.swirlds.common.utility.CommonUtils.hex(key.getECDSASecp256K1().toByteArray()));

        byte[] privateKeyByteArray;
        byte[] dByteArray = ((BCECPrivateKey)privateKey).getD().toByteArray();
        if (dByteArray.length < 32) {
            privateKeyByteArray = new byte[32];
            System.arraycopy(dByteArray, 0, privateKeyByteArray, 32 - dByteArray.length, dByteArray.length);
        } else if (dByteArray.length == 32) {
            privateKeyByteArray = dByteArray;
        } else {
            privateKeyByteArray = new byte[32];
            System.arraycopy(dByteArray, dByteArray.length - 32, privateKeyByteArray, 0, 32);
        }

        final var signedEthTxData = EthTxSigs.signMessage(ethTxData, privateKeyByteArray);

        EthereumTransactionBody opBody = spec
                .txns()
                .<EthereumTransactionBody, EthereumTransactionBody.Builder>body(
                        EthereumTransactionBody.class, builder -> {
                            builder.setEthereumData(ByteString.copyFrom(signedEthTxData.encodeTx()));
                            ethFileID.ifPresent(builder::setCallData);
                            maxGasAllowance.ifPresent(builder::setMaxGasAllowance);
                        }
                );
        return b -> b.setEthereumTransaction(opBody);
    }

    private void generateAdminKey(HapiApiSpec spec) {
        if (key.isPresent()) {
            adminKey = spec.registry().getKey(key.get());
        } else {
            KeyGenerator generator = effectiveKeyGen();
            if (adminKeyControl.isEmpty()) {
                adminKey = spec.keys().generate(spec, adminKeyType.orElse(KeyFactory.KeyType.SIMPLE), generator);
            } else {
                adminKey = spec.keys().generateSubjectTo(spec, adminKeyControl.get(), generator);
            }
        }
    }

    private void setBytecodeToDefaultContract(HapiApiSpec spec) throws Throwable {
        String implicitBytecodeFile = contract + "Bytecode";
        HapiFileCreate fileCreate = TxnVerbs
                .fileCreate(implicitBytecodeFile)
                .path(spec.setup().defaultContractPath());
        Optional<Throwable> opError = fileCreate.execFor(spec);
        if (opError.isPresent()) {
            throw opError.get();
        }
        bytecodeFile = Optional.of(implicitBytecodeFile);
    }

    @Override
    protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerSigs) throws Throwable {
        return spec.fees().forActivityBasedOp(
                HederaFunctionality.EthereumTransaction,
                scFees::getEthereumTransactionFeeMatrices, txn, numPayerSigs);
    }

    @Override
    protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
        return spec.clients().getScSvcStub(targetNodeFor(spec), useTls)::createContract;
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        MoreObjects.ToStringHelper helper = super.toStringHelper()
                .add("contract", contract);
        bytecodeFile.ifPresent(f -> helper.add("bytecode", f));
        memo.ifPresent(m -> helper.add("memo", m));
        autoRenewPeriodSecs.ifPresent(p -> helper.add("autoRenewPeriod", p));
        adminKeyControl.ifPresent(c -> helper.add("customKeyShape", Boolean.TRUE));
        Optional.ofNullable(lastReceipt)
                .ifPresent(receipt -> helper.add("created", receipt.getContractID().getContractNum()));
        return helper;
    }

    public long numOfCreatedContract() {
        return Optional
                .ofNullable(lastReceipt)
                .map(receipt -> receipt.getContractID().getContractNum())
                .orElse(-1L);
    }
}
