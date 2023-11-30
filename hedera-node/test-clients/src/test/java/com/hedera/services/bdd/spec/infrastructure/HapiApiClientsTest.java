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

package com.hedera.services.bdd.spec.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.infrastructure.interceptor.RetryCallbacks;
import com.hedera.services.bdd.spec.infrastructure.interceptor.RetryInterceptor;
import com.hedera.services.bdd.spec.props.NodeConnectInfo;
import com.hederahashgraph.api.proto.java.*;
import io.grpc.*;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http2.*;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.SAME_THREAD)
public class HapiApiClientsTest {

    private static HapiApiClients apiClients;
    private static AccountID accountID;
    private static NodeConnectInfo node;

    private static Channel serverChannel;
    private static EventLoopGroup bossGroup;
    private static EventLoopGroup workerGroup;
    private static ExecutorService serverExecutor;
    private static CountDownLatch latch;
    private static Queue<Http2DataFrameCallback> serverHandlerCallbacks;
    private static AtomicInteger lastValidStreamId;

    @BeforeEach
    public void startServer() throws InterruptedException {
        lastValidStreamId = new AtomicInteger(-1);
        latch = new CountDownLatch(1);
        int port = 50211;
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        serverHandlerCallbacks = new LinkedList<>();

        serverExecutor = Executors.newSingleThreadExecutor();
        serverExecutor.submit(() -> {
            try {
                ServerBootstrap b = new ServerBootstrap();
                b.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(new Http2ServerInitializer());

                serverChannel = b.bind(port)
                        .addListener(future -> {
                            if (future.isSuccess()) {
                                System.out.println("Server successfully started.");
                                latch.countDown();
                            } else {
                                System.err.println("Server failed to bind to port: " + port);
                            }
                        })
                        .syncUninterruptibly()
                        .channel();
                System.out.println("HTTP/2 (no SSL) server started at port " + port);
            } catch (Exception e) {
                System.err.println("Server failed to start: " + e.getMessage());
            }
        });

        // Wait for the server to be up before proceeding
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Server did not start within the expected time");
        }
    }

    @AfterEach
    public void stopServer() {
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
        if (serverExecutor != null) {
            serverExecutor.shutdown();
        }
    }

    @BeforeAll
    public static void setup() {
        // Initialize HapiApiClients with mock nodes or test configuration
        HapiSpecSetup setup = createTestSetup();
        node = setup.nodes().get(0);
        accountID = node.getAccount();
        System.out.printf("node: %s | accountID: %s\n", node.uri(), accountID);
        apiClients = HapiApiClients.clientsFor(setup);
    }

    /**
     * This test simulates the GOAWAY from the server by closing the channel.
     */
    @Test
    public void testConnectionGoawayWithStub() {
        // Get a stub to simulate GOAWAY
        ManagedChannel channel = getChannelToTerminate(apiClients);
        simulateGoAwaySignal(channel);

        // Attempt to use a stub from the terminated channel
        try {
            final var builder = CryptoGetAccountBalanceQuery.newBuilder().setAccountID(accountID);
            final var query = Query.newBuilder().setCryptogetAccountBalance(builder).build();
            final var response = apiClients.getCryptoSvcStub(accountID, false).cryptoGetBalance(query);
            fail(String.format("Expected an exception due to GOAWAY signal but none was thrown: %s", response.toString()));
        } catch (StatusRuntimeException e) {
            // Assert that the correct exception is thrown
            assertEquals(Status.Code.UNAVAILABLE, e.getStatus().getCode());
        }
    }

    /**
     * This test actually processes the GOAWAY from the server,
     * AND ensures that we get an exception when there are no retries enabled.
     */
    @Test
    public void testConnectionGoawayNoInterceptor() {
        // Set up our handler to respond with an abrupt close of the channel.
        serverHandlerCallbacks.add(new SendAbruptGoawayResponseHandler());
        try {
            final var builder = CryptoGetAccountBalanceQuery.newBuilder().setAccountID(accountID);
            final var query = Query.newBuilder().setCryptogetAccountBalance(builder).build();
            final var client = apiClients.getCryptoSvcStub(accountID, false);
            final var response = client.cryptoGetBalance(query);
            fail(String.format("Expected an exception due to GOAWAY signal but none was thrown: %s", response.toString()));
        } catch (StatusRuntimeException e) {
            // Assert that the correct exception is thrown.
            assertEquals(Status.Code.UNAVAILABLE, e.getStatus().getCode());
        }
    }

    /**
     * This test processes a valid response from the server,
     * using a RetryInterceptor set to zero retries.
     */
    @Test
    public void testConnectionGoawayWithRetryInterceptorAndZeroRetries() throws InterruptedException {
        // Set up our handler to respond with a valid response from the server.
        serverHandlerCallbacks.add(new SendSuccessfulResponseHandler());

        // Some retry setup. We want zero retries because this should succeed.
        var retries = 0;
        var latch = new CountDownLatch(retries);
        var retryCallbacks = new RetryCallbacksLatch(latch);
        var interceptor = RetryInterceptor.newBuilder()
                .setRetries(retries) // Set the number of times this interceptor is allowed to retry.
                .setRetryCallbacks(retryCallbacks); // On each retry, execute our callback, so we can verify it retried.

        apiClients.setClientInterceptor(interceptor);

        final var builder = CryptoGetAccountBalanceQuery.newBuilder().setAccountID(accountID);
        final var query = Query.newBuilder().setCryptogetAccountBalance(builder).build();
        final var client = apiClients.getCryptoSvcStub(accountID, false);

        // Execute the query.
        var response = client.cryptoGetBalance(query);

        // Assert that the query did not fail.
        assertNotNull(response, "Could not get response");
        assertEquals(response.getCryptogetAccountBalance().getBalance(), 1000);

        // We now need to assert that the latch was counted down. We should have had zero retries.
        // The latch should execute immediately, leaving this here so this test doesn't hang if there
        // is a problem in the future.
        boolean noTimeout = latch.await(5, TimeUnit.SECONDS);

        // Test should fail if the callback is not called within the timeout.
        assertTrue(noTimeout, "The latch timed out waiting for the number of retries");
    }

    /**
     * This test processes the GOAWAY from the server,
     * AND ensures that a second request will succeed after the first connection is closed.
     * The client should automatically create a new connection without the retry interceptor
     * for making the second request after the first request is closed.
     */
    @Test
    public void testClientAutomaticallyCreatingNewConnection() {
        // Set up our handler to respond with the sequence from the server.
        serverHandlerCallbacks.add(new SendValidGoawayResponseHandler());
        serverHandlerCallbacks.add(new SendSuccessfulResponseHandler());

        final var builder = CryptoGetAccountBalanceQuery.newBuilder().setAccountID(accountID);
        final var query = Query.newBuilder().setCryptogetAccountBalance(builder).build();
        final var client = apiClients.getCryptoSvcStub(accountID, false);
        var response = client.cryptoGetBalance(query);

        // Assert that the response passed this time.
        assertNotNull(response, "Could not get response");
        assertEquals(response.getCryptogetAccountBalance().getBalance(), 1000);

        // Now execute call #2. This should trigger RST_STREAM and be handled by the interceptor.
        var response2 = client.cryptoGetBalance(query);
        assertNotNull(response2, "Could not get response");
        assertEquals(response2.getCryptogetAccountBalance().getBalance(), 1000);
    }

    /**
     * This test actually processes the GOAWAY from the server,
     * AND ensures that we pass when there are retries enabled.
     * It:
     * 1. Makes a call and gets a valid response and receives a GOAWAY, no more requests should be made on that channel.
     * 2. Makes a second call at which point the call should fail with RST_STREAM because GOAWAY has already been sent.
     * 3. Makes a third call with a new channel and gets a valid response.
     */
    @Test
    public void testRetryInterceptor() throws InterruptedException {
        // Set up our handler to respond with the sequence from the server.
        serverHandlerCallbacks.add(new SendAbruptGoawayResponseHandler()); // first does an abrupt close.
        serverHandlerCallbacks.add(new SendSuccessfulResponseHandler()); // second is called by retry.

        // Some retry setup. In practice only the RetryInterceptor and setRetries is needed.
        // The setRetryCallbacks and CountDownLatch are only so we can verify the retry happened.
        var retries = 1;
        var latch = new CountDownLatch(retries);
        var retryCallbacks = new RetryCallbacksLatch(latch);
        var interceptor = RetryInterceptor.newBuilder()
                .setRetries(retries) // Set the number of times this interceptor is allowed to retry.
                .setRetryCallbacks(retryCallbacks); // On each retry, execute our callback, so we can verify it retried.

        apiClients.setClientInterceptor(interceptor);

        final var builder = CryptoGetAccountBalanceQuery.newBuilder().setAccountID(accountID);
        final var query = Query.newBuilder().setCryptogetAccountBalance(builder).build();
        final var client = apiClients.getCryptoSvcStub(accountID, false);

        System.out.println("Making client call 1");
        var response = client.cryptoGetBalance(query);

        System.out.printf(
                "in executor - ran crypto get balance %s\n",
                response.getCryptogetAccountBalance().getBalance());

        // We should be getting here because on the second retry it should pass.
//        System.out.printf("ran crypto get balance 2:   %s\n", response.toString());

        // Assert that the response passed this time.
        assertNotNull(response, "Could not get response");
        assertEquals(response.getCryptogetAccountBalance().getBalance(), 1000);

//        System.out.println("Making client call 2");
        // Now execute call #2. This should trigger RST_STREAM and be handled by the interceptor.
//        var response2 = client.cryptoGetBalance(query);

        // We now need to assert that the latch was counted down to ensure we retried.
        boolean noTimeout = latch.await(5, TimeUnit.SECONDS);

        // Test should fail if the callback is not called within the timeout
        assertTrue(noTimeout, "The latch timed out waiting for the number of retries");
    }

    private static void simulateGoAwaySignal(ManagedChannel channel) {
        // Shutting down the channel to simulate a GOAWAY condition
        channel.shutdown();
        try {
            // Optionally, wait for the channel to terminate to ensure the GOAWAY condition is simulated.
            // I hate waiting like this, but I need to dig in more and create a hook for this.
            boolean terminated = channel.awaitTermination(5, TimeUnit.SECONDS);
            if (!terminated) {
                System.err.println("Channel did not terminate in the expected time.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for channel termination", e);
        }
    }

    private static ManagedChannel getChannelToTerminate(HapiApiClients clients) {
        return clients.getChannel(accountID, false);
    }

    private static HapiSpecSetup createTestSetup() {
        // Create and return a HapiSpecSetup instance with the necessary test configuration
        return new HapiSpecSetup(HapiSpecSetup.getDefaultPropertySource());
    }

    @AfterAll
    public static void tearDown() {
        // Clean up resources
        HapiApiClients.tearDown();
    }

    private static class Http2ServerInitializer extends ChannelInitializer<SocketChannel> {

        @Override
        protected void initChannel(SocketChannel ch) {
            System.out.printf("initializing channel - serverAddress: %s - clientAddress: %s\n", ch.localAddress(), ch.remoteAddress());
            ChannelPipeline p = ch.pipeline();
            p.addLast(new LoggingHandler());
            p.addLast(Http2FrameCodecBuilder.forServer().build());
            p.addLast(new Http2ServerHandler());
        }
    }

    //    private static class Http2ServerHandler extends SimpleChannelInboundHandler<Http2Frame> {
    //
    //        private Http2ServerHandler() { }
    //
    //        @Override
    //        protected void channelRead0(ChannelHandlerContext ctx, Http2Frame frame) throws InterruptedException {
    //            System.out.printf("called channelRead0: %s\n", frame);
    //            if (frame instanceof Http2PingFrame) {
    //                Http2PingFrame pingFrame = (Http2PingFrame) frame;
    //                if (!pingFrame.ack()) {
    //                    // Respond with a PING frame with ACK set to true
    //                    ctx.writeAndFlush(new DefaultHttp2PingFrame(pingFrame.content(), true));
    //                }
    //            }
    //            if frame instanceof Http2DataFrame {
    //                DefaultHttp2DataFrame dataFrame = (DefaultHttp2DataFrame) frame;
    //                if (dataFrame.isEndStream()) {
    //
    //                }
    //            }
    //            if (frame instanceof Http2HeadersFrame) {
    //                Http2HeadersFrame headersFrame = (Http2HeadersFrame) frame;
    //
    //                // Send a Headers Frame as a response
    //                Http2Headers responseHeaders = new DefaultHttp2Headers();
    //                responseHeaders.status("200"); // OK Status
    //                responseHeaders.add("content-type", "application/grpc");
    //
    //                // Send a Data Frame with some content
    //                ByteBuf content = ctx.alloc().buffer();
    //                long balance = 1000; // Mock balance
    //                var response = CryptoGetAccountBalanceResponse.newBuilder()
    //
    // .setHeader(ResponseHeader.newBuilder().setNodeTransactionPrecheckCode(ResponseCodeEnum.OK))
    //                        .setAccountID(accountID)
    //                        .setBalance(balance)
    //                        .build();
    //                byte[] responseBytes = response.toByteArray(); // Serialize the response
    //                // Write the gRPC frame header
    //                content.writeByte(0); // Compression flag (0 for uncompressed)
    //                content.writeInt(responseBytes.length); // Message length
    //                content.writeBytes(responseBytes); // Write the serialized bytes to the buffer
    //
    //                // Send headers frame
    //                ctx.write(new DefaultHttp2HeadersFrame(responseHeaders).stream(headersFrame.stream()));
    //
    //                // If this is the first request, write the GOAWAY.
    //                final var call = serverHandlerCalls.getAndIncrement();
    //                if (call < 1) {
    //                    System.out.printf("sending GOAWAY: %d\n", call);
    //                    // Send data frame without end of stream flag
    ////                    ctx.writeAndFlush(new DefaultHttp2DataFrame(content,
    // false).stream(headersFrame.stream())).addListener(futr -> {
    ////                        if (futr.isSuccess()) {
    ////                            System.out.println("DataFrame 1 success");
    //                            // Send data frame with end of stream flag
    ////                            ctx.writeAndFlush(new
    // DefaultHttp2DataFrame(true).stream(headersFrame.stream())).addListener(futr2 -> {
    ////                                if (futr2.isSuccess()) {
    ////                                    System.out.println("DataFrame 2 success");
    //                                    // Ensure all response frames are flushed before sending GOAWAY
    //                                    ctx.writeAndFlush(new
    // DefaultHttp2GoAwayFrame(Http2Error.NO_ERROR)).addListener(future -> {
    //                                        if (future.isSuccess()) {
    //                                            System.out.println("closing ctx 1");
    //                                           //  ctx.close();
    //                                        } else {
    //                                            System.out.println("not success future");
    //                                        }
    //                                    });
    ////                                } else {
    ////                                    System.out.println("futr2 not success future");
    ////                                }
    ////                            });
    ////                        } else {
    ////                            System.out.println("not success futr");
    ////                        }
    ////                    });
    //
    //                } else {
    //                    System.out.printf("NOT sending GOAWAY: %d\n", call);
    //                    // Send data frame with end of stream flag
    //                    ctx.writeAndFlush(new DefaultHttp2DataFrame(content,
    // true).stream(headersFrame.stream())).addListener(future -> {
    //                        // Close the channel after ensuring the data frame is flushed
    //                        if (future.isSuccess()) {
    //                            // System.out.println("closing ctx 2");
    //                           // ctx.close();
    //                        } else {
    //                            System.out.println("NOTGOAWAY: not success future");
    //                        }
    //                    });
    //                }
    //            }
    //        }
    //    }

    private interface Http2DataFrameCallback {
        int handle(ChannelHandlerContext ctx, Http2FrameStream stream);
    }

    private static class Http2ServerHandler extends SimpleChannelInboundHandler<Http2Frame> {

        private ByteBuf accumulatedData;
        private boolean endOfStreamReceived = false;

        private Queue<Http2DataFrameCallback> handlers;

        public Http2ServerHandler() {
            super();
            accumulatedData = Unpooled.buffer();
            handlers = serverHandlerCallbacks;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Http2Frame frame) {
            System.out.printf("called channelRead0: %s - lastValidStreamId: %d\n", frame, lastValidStreamId.get());

            Http2FrameStream http2Stream = null;

            if (frame instanceof DefaultHttp2PingFrame) {
                DefaultHttp2PingFrame pingFrame = (DefaultHttp2PingFrame) frame;
                if (!pingFrame.ack()) {
                    System.out.println("sending PING ACK");
                    // Echo the ping frame with ACK set
                    ctx.write(new DefaultHttp2PingFrame(pingFrame.content(), true));
                    return;
                }
            }

            if (frame instanceof Http2DataFrame) {
                Http2DataFrame dataFrame = (Http2DataFrame) frame;
                accumulatedData.writeBytes(dataFrame.content());
                endOfStreamReceived = dataFrame.isEndStream();
                http2Stream = dataFrame.stream();
            }

            if (http2Stream != null || frame instanceof Http2FrameStream) {
                Http2FrameStream stream = http2Stream != null ? http2Stream : (Http2FrameStream) frame;

                var lastStrId = lastValidStreamId.get();
                if (lastStrId != -1 && stream.id() > lastStrId)  {
                    System.out.printf("sending RST_STREAM on stream: %s\n", frame.toString());
                    // If a new stream is attempted after GOAWAY, send RST_STREAM
                    ctx.writeAndFlush(new DefaultHttp2ResetFrame(Http2Error.REFUSED_STREAM).stream(stream));
                    return;
                }
            }

            if (endOfStreamReceived && http2Stream != null) {
//                var callCount = serverHandlerCalls.decrementAndGet();
                // The first time we call, we want it to send a GOAWAY.
                // After the first time we call, we want to succeed.
                // serverHandlerCalls set to a number greater than 1, i.e. called twice when being retried
                // when set to 1 it should only be called once and in that case sends a successful response.
//                if (callCount >= 1) processRequestAndSendGoawayResponse(ctx, http2Stream);
//                else processRequestAndSendSuccessfulResponse(ctx, http2Stream);
                var cb = handlers.poll();
                assertNotNull(cb, "Http2DataFrame ready to be processed but serverHandlerCallbacks is empty");
                var beforeLastValidStreamId = lastValidStreamId.get();
                assertTrue(
                        lastValidStreamId.compareAndSet(beforeLastValidStreamId, cb.handle(ctx, http2Stream)),
                        "unable to atomically set the new lastValidStreamId");

                accumulatedData.clear();
                endOfStreamReceived = false;
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            accumulatedData.release();
            ctx.fireChannelInactive();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }

    /**
     * This class crafts a successful test response to the client.
     *
     * @see io.grpc.internal.Http2ClientStreamTransportState#transportDataReceived for details on the response protocol.
     */
    private static class SendSuccessfulResponseHandler implements Http2DataFrameCallback {
        @Override
        public int handle(ChannelHandlerContext ctx, Http2FrameStream stream) {
            System.out.println("SendSuccessfulResponseHandler");

            // Assuming accumulatedData contains the serialized request

            // Create and serialize the response
            CryptoGetAccountBalanceResponse.Builder b = CryptoGetAccountBalanceResponse.newBuilder()
//                    .setHeader(ResponseHeader.newBuilder().setNodeTransactionPrecheckCode(ResponseCodeEnum.OK))
                    .setAccountID(accountID)
                    .setBalance(1000L); // Mock balance
            Response response = Response.newBuilder().setCryptogetAccountBalance(b).build();

            byte[] responseBytes = response.toByteArray(); // new byte[]{};

            System.out.printf("responseBytes: %s\n", Arrays.toString(responseBytes));
            System.out.printf("responseBytes len: %d\n", responseBytes.length);


            ByteBuf content = ctx.alloc().buffer();
            content.writeByte(0); // Compression flag
            content.writeInt(responseBytes.length); // Message length
            content.writeBytes(responseBytes);

            System.out.printf("content: %s\n", Arrays.toString(ByteBufUtil.getBytes(content)));

            // Send the response
            Http2Headers responseHeaders = new DefaultHttp2Headers();
            responseHeaders.status("200"); // OK Status
            responseHeaders.add("content-type", "application/grpc");
            ctx.write(new DefaultHttp2HeadersFrame(responseHeaders, false).stream(stream));
            ctx.writeAndFlush(new DefaultHttp2DataFrame(content, false).stream(stream));

            // Finally, send the trailers (with EOS flag).
            // According to Http2ClientStreamTransportState.transportDataReceived
            // you cannot end a stream with a DefaultHttp2DataFrame, so we must do this with a trailer.
            Http2Headers trailers = new DefaultHttp2Headers();
            trailers.set("content-length", String.format("%d", responseBytes.length));
            trailers.add("grpc-status", "0"); // "0" indicates success
            trailers.add("grpc-message", ""); // Optional message, can be left empty for success
            ctx.writeAndFlush(new DefaultHttp2HeadersFrame(trailers, true).stream(stream));

            return -1; // allow new streams
        }
    }

    /**
     * This class crafts a GOAWAY response to the client.
     *
     * @see io.grpc.internal.Http2ClientStreamTransportState#transportDataReceived for details on the response protocol.
     */
    private static class SendValidGoawayResponseHandler implements Http2DataFrameCallback {
        @Override
        public int handle(ChannelHandlerContext ctx, Http2FrameStream stream) {
            System.out.println("SendValidGoawayResponseHandler");

//            // Assuming accumulatedData contains the serialized request
//
//            // Create and serialize the response
            CryptoGetAccountBalanceResponse.Builder b = CryptoGetAccountBalanceResponse.newBuilder()
//                    .setHeader(ResponseHeader.newBuilder().setNodeTransactionPrecheckCode(ResponseCodeEnum.OK))
                    .setAccountID(accountID)
                    .setBalance(1000L); // Mock balance
            Response response = Response.newBuilder().setCryptogetAccountBalance(b).build();

            byte[] responseBytes = response.toByteArray(); // new byte[]{};

            System.out.printf("responseBytes: %s\n", Arrays.toString(responseBytes));
            System.out.printf("responseBytes len: %d\n", responseBytes.length);


            ByteBuf content = ctx.alloc().buffer();
            content.writeByte(0); // Compression flag
            content.writeInt(responseBytes.length); // Message length
            content.writeBytes(responseBytes);

            System.out.printf("content: %s\n", Arrays.toString(ByteBufUtil.getBytes(content)));

            // Really, a server should send a GOAWAY, and then continue to process the remaining open streams gracefully.
            // We don't want to test that though. We want to send the GOAWAY and then immediately shut down as if the client
            // had not received all the data from a stream.

            // Send the response
            Http2Headers responseHeaders = new DefaultHttp2Headers();
            responseHeaders.status("200"); // OK Status
            responseHeaders.add("content-type", "application/grpc");
            ctx.write(new DefaultHttp2HeadersFrame(responseHeaders, false).stream(stream));
            ctx.writeAndFlush(new DefaultHttp2DataFrame(content, false).stream(stream));

            // Finally, send the trailers (with EOS flag).
            // According to Http2ClientStreamTransportState.transportDataReceived
            // you cannot end a stream with a DefaultHttp2DataFrame, so we must do this with a trailer.
            Http2Headers trailers = new DefaultHttp2Headers();
            trailers.add("grpc-status", "0"); // "0" indicates success
            trailers.add("grpc-message", "closing"); // Optional message, can be left empty for success
            ctx.writeAndFlush(new DefaultHttp2HeadersFrame(trailers, true).stream(stream));

            ctx.writeAndFlush(new DefaultHttp2GoAwayFrame(Http2Error.NO_ERROR));

            return stream.id(); // Don't allow new streams after this one.
        }
    }

    /**
     * SendInvalidGoawayResponseHandler will abruptly close a connection.
     */
    private static class SendAbruptGoawayResponseHandler implements Http2DataFrameCallback {
        @Override
        public int handle(ChannelHandlerContext ctx, Http2FrameStream stream) {
//            // Send a header
//            Http2Headers responseHeaders = new DefaultHttp2Headers();
//            responseHeaders.status("200"); // OK Status
//            responseHeaders.add("content-type", "application/grpc");
//            ctx.write(new DefaultHttp2HeadersFrame(responseHeaders, false).stream(stream));
//
//            // Finally, send the trailers (with EOS flag).
//            // According to Http2ClientStreamTransportState.transportDataReceived
//            // you cannot end a stream with a DefaultHttp2DataFrame, so we must do this with a trailer.
//            Http2Headers trailers = new DefaultHttp2Headers();
//            trailers.add("grpc-status", "0"); // "0" indicates success
//            trailers.add("grpc-message", "closing"); // Optional message, can be left empty for success
//            ctx.writeAndFlush(new DefaultHttp2HeadersFrame(trailers, true).stream(stream));

            ByteBuf debugData = ctx.alloc().buffer().writeBytes("max_age".getBytes(CharsetUtil.UTF_8));
            ctx.writeAndFlush(new DefaultHttp2GoAwayFrame(Http2Error.NO_ERROR, debugData));

            // Finally, send the trailers (with EOS flag).
//            // According to Http2ClientStreamTransportState.transportDataReceived
//            // you cannot end a stream with a DefaultHttp2DataFrame, so we must do this with a trailer.
            Http2Headers trailers = new DefaultHttp2Headers();
            trailers.status("200");
            trailers.add("content-type", "application/grpc");
            trailers.add("grpc-status", String.valueOf(Status.Code.UNAVAILABLE.value()));
            trailers.add("grpc-message", "Connection closed after GOAWAY. HTTP/2 error code: NO_ERROR, debug data: max_age");
            ctx.writeAndFlush(new DefaultHttp2HeadersFrame(trailers, true).stream(stream));

//            ctx.close(); // Immediately close.
//            ctx.disconnect();

            return stream.id(); // Don't allow new streams after this one.
        }
    }

    private static class LoggingHandler extends ChannelInboundHandlerAdapter {

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            SocketAddress remoteAddress = ctx.channel().remoteAddress();
            System.out.println("Connection established with: " + remoteAddress);
            super.channelActive(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            System.err.println("Exception caught: " + cause.getMessage());
            ctx.close();
        }
    }

    private static class RetryCallbacksLatch implements RetryCallbacks {

        private final CountDownLatch latch;

        private RetryCallbacksLatch(CountDownLatch latch) {
            this.latch = latch;
        }

        public <ReqT, RespT> boolean onRetry(
                io.grpc.MethodDescriptor<ReqT, RespT> method,
                CallOptions callOptions,
                io.grpc.Channel newChannel,
                ClientCall.Listener<RespT> originalListener,
                Metadata originalHeaders) {
            this.latch.countDown();
            return true;
        }
    }
}
