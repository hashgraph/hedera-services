/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.spec.queries.file;

import static com.hedera.services.bdd.spec.queries.QueryUtils.answerCostHeader;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerHeader;

import com.google.common.base.MoreObjects;
import com.google.common.io.ByteSink;
import com.google.common.io.CharSink;
import com.google.common.io.Files;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.FileGetContentsQuery;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import com.hederahashgraph.api.proto.java.Transaction;
import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

public class HapiGetFileContents extends HapiQueryOp<HapiGetFileContents> {
    private static final Logger log = LogManager.getLogger(HapiGetFileContents.class);

    private static final Map<String, String> IMMUTABLE_MAP = Collections.emptyMap();

    private Map<String, String> props = IMMUTABLE_MAP;
    private Predicate<String> includeProp = ignore -> true;

    private boolean saveIn4kChunks = false;
    private int sizeLookup = -1;
    private Function<byte[], String> parser = bytes -> "<N/A>";
    private final String fileName;
    Optional<String> readablePath = Optional.empty();
    Optional<String> snapshotPath = Optional.empty();
    Optional<String> registryEntry = Optional.empty();
    Optional<Consumer<byte[]>> contentsCb = Optional.empty();
    Optional<Function<HapiSpec, ByteString>> expContentFn = Optional.empty();
    Optional<UnaryOperator<byte[]>> afterBytesTransform = Optional.empty();

    Optional<Consumer<FileID>> preQueryCb = Optional.empty();
    Optional<Consumer<Response>> postQueryCb = Optional.empty();

    private FileID fileId;

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.FileGetContents;
    }

    @Override
    protected HapiGetFileContents self() {
        return this;
    }

    public HapiGetFileContents(String fileName) {
        this.fileName = fileName;
    }

    public HapiGetFileContents alertingPre(Consumer<FileID> cb) {
        preQueryCb = Optional.of(cb);
        return this;
    }

    public HapiGetFileContents alertingPost(Consumer<Response> cb) {
        postQueryCb = Optional.of(cb);
        return this;
    }

    public HapiGetFileContents saveTo(String path) {
        snapshotPath = Optional.of(path);
        return this;
    }

    public HapiGetFileContents consumedBy(Consumer<byte[]> cb) {
        contentsCb = Optional.of(cb);
        return this;
    }

    public HapiGetFileContents addingConfigListTo(Map<String, String> props) {
        this.props = props;
        return this;
    }

    public HapiGetFileContents addingFilteredConfigListTo(
            final Map<String, String> props, final Predicate<String> includeProp) {
        this.props = props;
        this.includeProp = includeProp;
        return this;
    }

    public HapiGetFileContents saveReadableTo(Function<byte[], String> parser, String path) {
        this.parser = parser;
        readablePath = Optional.of(path);
        return this;
    }

    public HapiGetFileContents saveToRegistry(String registryEntry) {
        this.registryEntry = Optional.of(registryEntry);
        return this;
    }

    public HapiGetFileContents in4kChunks(boolean flag) {
        saveIn4kChunks = flag;
        return this;
    }

    public HapiGetFileContents hasByteStringContents(Function<HapiSpec, ByteString> fn) {
        expContentFn = Optional.of(fn);
        return self();
    }

    public HapiGetFileContents hasContents(Function<HapiSpec, byte[]> fn) {
        Function<HapiSpec, ByteString> specToBtFn = spec -> ByteString.copyFrom(fn.apply(spec));
        return hasByteStringContents(specToBtFn);
    }

    public HapiGetFileContents afterBytesTransform(UnaryOperator<byte[]> transform) {
        afterBytesTransform = Optional.of(transform);
        return this;
    }

    public HapiGetFileContents hasContents(String registryEntry) {
        return hasContents(spec -> spec.registry().getBytes(registryEntry));
    }

    private boolean isConfigListFile(HapiSpec spec) {
        return fileName.equals(spec.setup().apiPermissionsFile())
                || fileName.equals(spec.setup().appPropertiesFile());
    }

    @Override
    protected void submitWith(HapiSpec spec, Transaction payment) throws Throwable {
        Query query = getFileContentQuery(spec, payment, false);
        preQueryCb.ifPresent(cb -> cb.accept(fileId));
        response = spec.clients().getFileSvcStub(targetNodeFor(spec), useTls).getFileContent(query);
        postQueryCb.ifPresent(cb -> cb.accept(response));
        byte[] bytes = response.getFileGetContents().getFileContents().getContents().toByteArray();
        if (verboseLoggingOn) {
            var len = response.getFileGetContents().getFileContents().getContents().size();
            log.info(String.format("%s contained %s bytes", fileName, len));
            if (isConfigListFile(spec)) {
                var configList = ServicesConfigurationList.parseFrom(bytes);
                var msg = new StringBuilder("As a config list, contents are:");
                List<String> entries = new ArrayList<>();
                configList
                        .getNameValueList()
                        .forEach(
                                setting ->
                                        entries.add(
                                                String.format(
                                                        "\n  %s=%s",
                                                        setting.getName(), setting.getValue())));
                Collections.sort(entries);
                entries.forEach(msg::append);
                log.info(msg.toString());
            }
        }
        if (fileName.equals(spec.setup().appPropertiesFile()) && (props != IMMUTABLE_MAP)) {
            try {
                var configList = ServicesConfigurationList.parseFrom(bytes);
                configList
                        .getNameValueList()
                        .forEach(
                                setting -> {
                                    if (includeProp.test(setting.getName())) {
                                        props.put(setting.getName(), setting.getValue());
                                    }
                                });
            } catch (Exception impossible) {
                throw new IllegalStateException(impossible);
            }
        }
        if (snapshotPath.isPresent() || readablePath.isPresent()) {
            try {
                if (snapshotPath.isPresent()) {
                    if (saveIn4kChunks) {
                        int i = 0;
                        int MAX_LEN = 4 * 1024;
                        int suffix = 0;
                        while (i < bytes.length) {
                            File snapshotFile =
                                    new File(
                                            String.format(
                                                    "part%d-%s", suffix++, snapshotPath.get()));
                            ByteSink byteSink = Files.asByteSink(snapshotFile);
                            int numToWrite = Math.min(bytes.length - i, MAX_LEN);
                            byteSink.write(Arrays.copyOfRange(bytes, i, i + numToWrite));
                            i += numToWrite;
                            log.info(
                                    "Saved next "
                                            + numToWrite
                                            + " bytes of '"
                                            + fileName
                                            + "' to "
                                            + snapshotFile.getAbsolutePath());
                        }
                    } else {
                        File snapshotFile = new File(snapshotPath.get());
                        ByteSink byteSink = Files.asByteSink(snapshotFile);
                        byteSink.write(bytes);
                        log.info(
                                "Saved "
                                        + bytes.length
                                        + " bytes of '"
                                        + fileName
                                        + "' to "
                                        + snapshotFile.getAbsolutePath());
                    }
                }
                if (readablePath.isPresent()) {
                    String contents = parser.apply(bytes);
                    File readableFile = new File(readablePath.get());
                    CharSink charSink = Files.asCharSink(readableFile, Charset.forName("UTF-8"));
                    charSink.write(contents);
                    log.info(
                            "Saved parsed contents of '"
                                    + fileName
                                    + "' to "
                                    + readableFile.getAbsolutePath());
                }
            } catch (Exception e) {
                log.error("Couldn't save '" + fileName + "' snapshot!", e);
            }
        }
        if (registryEntry.isPresent()) {
            spec.registry()
                    .saveBytes(
                            registryEntry.get(),
                            response.getFileGetContents().getFileContents().getContents());
        }
        contentsCb.ifPresent(
                cb ->
                        cb.accept(
                                response.getFileGetContents()
                                        .getFileContents()
                                        .getContents()
                                        .toByteArray()));
    }

    @Override
    protected long lookupCostWith(HapiSpec spec, Transaction payment) throws Throwable {
        Query query = getFileContentQuery(spec, payment, true);
        Response response =
                spec.clients().getFileSvcStub(targetNodeFor(spec), useTls).getFileContent(query);
        return costFrom(response);
    }

    @Override
    protected void assertExpectationsGiven(HapiSpec spec) throws Throwable {
        if (expContentFn.isPresent()) {
            ByteString expected = expContentFn.get().apply(spec);
            ByteString actual = response.getFileGetContents().getFileContents().getContents();
            if (afterBytesTransform.isPresent()) {
                actual = ByteString.copyFrom(afterBytesTransform.get().apply(actual.toByteArray()));
            }
            Assertions.assertEquals(
                    expected.toString(Charset.defaultCharset()),
                    actual.toString(Charset.defaultCharset()),
                    "Wrong file contents!");
        }
    }

    private Query getFileContentQuery(HapiSpec spec, Transaction payment, boolean costOnly) {
        fileId = TxnUtils.asFileId(fileName, spec);

        FileGetContentsQuery query =
                FileGetContentsQuery.newBuilder()
                        .setHeader(costOnly ? answerCostHeader(payment) : answerHeader(payment))
                        .setFileID(TxnUtils.asFileId(fileName, spec))
                        .build();
        return Query.newBuilder().setFileGetContents(query).build();
    }

    @Override
    protected boolean needsPayment() {
        return true;
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        MoreObjects.ToStringHelper helper =
                super.toStringHelper().add("file", fileName).add("fileId", fileId);

        if (sizeLookup != -1) {
            helper.add("size", sizeLookup);
        }
        return helper;
    }
}
