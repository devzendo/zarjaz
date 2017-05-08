package org.devzendo.zarjaz.transceiver;

import org.devzendo.commoncode.concurrency.ThreadUtils;
import org.devzendo.zarjaz.logging.ConsoleLoggingUnittestCase;
import org.devzendo.zarjaz.nio.ReadableByteBuffer;
import org.devzendo.zarjaz.util.BufferDumper;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.devzendo.zarjaz.transceiver.BufferUtils.createByteBuffer;
import static org.devzendo.zarjaz.transceiver.BufferUtils.duplicateOutgoingByteBuffer;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeThat;

/**
 * Copyright (C) 2008-2016 Matt Gumbley, DevZendo.org http://devzendo.org
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@RunWith(Parameterized.class)
public class TestTransceivers extends ConsoleLoggingUnittestCase {
    private static final Logger logger = LoggerFactory.getLogger(TestTransceivers.class);

    @BeforeClass
    public static void getBroadcastAddress() throws SocketException {

        setupLoggingStatically();

        // logging in here comes out at the end of the tests when run in intellij.
        final Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
        while (networkInterfaces.hasMoreElements()) {
            final NetworkInterface networkInterface = networkInterfaces.nextElement();
            final List<InterfaceAddress> interfaceAddresses = networkInterface.getInterfaceAddresses();
            for (final InterfaceAddress interfaceAddress : interfaceAddresses) {
                final InetAddress broadcast = interfaceAddress.getBroadcast();
                if (broadcast != null)
                {
                    logger.info("Using broadcast address " + broadcast);
                    broadcastAddress = broadcast;
                    return;
                }
            }
        }
        logger.info("No broadcast address available");
    }
    private static InetAddress broadcastAddress = null;

    private enum ConnectionType {NULL, TCP, UDP, UDP_BROADCAST}
    private final ConnectionType connectionType;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private Transceiver serverTransceiver;
    private Transceiver clientTransceiver;

    @Parameterized.Parameters
    public static Collection<ConnectionType> data() {
        return asList(ConnectionType.NULL, ConnectionType.UDP, ConnectionType.TCP, ConnectionType.UDP_BROADCAST);
    }

    // runs before mocks initialised, so do real construction in @Before.
    public TestTransceivers(final ConnectionType connectionType) {
        this.connectionType = connectionType;
    }

    @Before
    public void setupTransceiver() throws IOException {
        logger.info(">>>>> BEFORE ***** start of setupTransceiver, connection type " + connectionType + " *****");
        switch (connectionType) {
            case NULL:
                serverTransceiver = new NullTransceiver();
                clientTransceiver = serverTransceiver;
                break;
            case UDP:
                serverTransceiver = UDPTransceiver.createServer(new InetSocketAddress(9876));
                clientTransceiver = UDPTransceiver.createClient(new InetSocketAddress(9876), false);
                break;
            case TCP:
                serverTransceiver = TCPTransceiver.createServer(new InetSocketAddress(9876));
                clientTransceiver = TCPTransceiver.createClient(new InetSocketAddress(9876));
                break;
            case UDP_BROADCAST:
                assumeNotNull(broadcastAddress);
                serverTransceiver = UDPTransceiver.createServer(new InetSocketAddress(9876));
                clientTransceiver = UDPTransceiver.createClient(new InetSocketAddress(broadcastAddress,9876), true);
                break;
        }
        logger.debug("<<<<< BEFORE ***** end of setupTransceiver *****");
    }

    @After
    public void closeTransceiver() throws IOException {
        logger.debug(">>>>> AFTER ***** start of closeTransceiver *****");
        if (serverTransceiver != null) {
            serverTransceiver.close();
        }
        if (clientTransceiver != null) {
            clientTransceiver.close();
        }
        logger.debug("<<<<< AFTER ***** end of setupTransceiver ******");
    }

    @Test
    public void canRebindServerTransceiversQuickly() throws IOException {
        serverTransceiver.open();
        serverTransceiver.close();

        setupTransceiver(); // binds server again.
        serverTransceiver.open();
    }

    @Test(timeout = 2000)
    public void bufferSentFromClientIsReceivedByServer() throws IOException {
        final EventCollectingTransceiverObserver observer = new EventCollectingTransceiverObserver();
        serverTransceiver.getServerEnd().addTransceiverObserver(observer);
        logger.debug("opening server");
        serverTransceiver.open();
        waitForListeningToStart();

        logger.debug("opening client");
        clientTransceiver.open();

        logger.debug("starting send test");
        final ReadableByteBuffer buf0 = createByteBuffer();
        final ReadableByteBuffer expectedBuffer0 = duplicateOutgoingByteBuffer(buf0);
        final List<ReadableByteBuffer> buf0List = singletonList(buf0);
        BufferDumper.dumpBuffer("original buf0 (duplicate rewound):", expectedBuffer0.raw());
        BufferDumper.dumpBuffers(buf0List);
        clientTransceiver.getServerWriter().writeBuffer(buf0List);

        final ReadableByteBuffer buf1 = createByteBuffer();
        final ReadableByteBuffer expectedBuffer1 = duplicateOutgoingByteBuffer(buf1);
        final List<ReadableByteBuffer> buf1List = singletonList(buf1);
        BufferDumper.dumpBuffer("original buf1 (duplicate rewound):", expectedBuffer1.raw());
        clientTransceiver.getServerWriter().writeBuffer(buf1List);

        ThreadUtils.waitNoInterruption(500);

        logger.info("----------------------------------------------- data received ------------------------------------");
        final List<TransceiverObservableEvent> events = observer.getCollectedEvents();
        assertThat(events, hasSize(2));
        for (final TransceiverObservableEvent event : events) {
            assertThat(event.getData(), hasSize(1));
            BufferDumper.dumpBuffer("test received", event.getData().get(0).raw());
        }

        final ByteBuffer receivedBuffer0 = events.get(0).getData().get(0).raw();
        BufferDumper.equalData("got0", receivedBuffer0, "expected0", expectedBuffer0.raw());
        assertThat(receivedBuffer0, equalTo(expectedBuffer0.raw()));

        final ByteBuffer receivedBuffer1 = events.get(1).getData().get(0).raw();
        BufferDumper.equalData("got1", receivedBuffer1, "expected1", expectedBuffer1.raw());
        assertThat(receivedBuffer1, equalTo(expectedBuffer1.raw()));
    }

    private void waitForListeningToStart() {
        // wait for listening to be set up...
        ThreadUtils.waitNoInterruption(200);
    }

    @Test(timeout = 1000)
    public void sentBufferCanBeRepliedTo() throws IOException {
        // the client collects the reply coming from the server
        final EventCollectingTransceiverObserver serverReplyObserver = new EventCollectingTransceiverObserver();
        clientTransceiver.getClientEnd().addTransceiverObserver(serverReplyObserver);

        // the server will reply to its request
        final ReadableByteBuffer replyWithBuffer = createByteBuffer();
        final ReadableByteBuffer expectedReplyWithBuffer = duplicateOutgoingByteBuffer(replyWithBuffer);
        final ReplyingTransceiverObserver replyingObserver = new ReplyingTransceiverObserver(singletonList(replyWithBuffer));
        serverTransceiver.getServerEnd().addTransceiverObserver(replyingObserver);

        serverTransceiver.open();
        waitForListeningToStart();

        clientTransceiver.open();

        // send the request to the server
        final Transceiver.BufferWriter serverWriter = clientTransceiver.getServerWriter();
        logger.debug("sending initial buffer to server");
        serverWriter.writeBuffer(singletonList(createByteBuffer()));

        ThreadUtils.waitNoInterruption(500);

        // the server has received a reply to its request
        final List<TransceiverObservableEvent> collectedEvents = serverReplyObserver.getCollectedEvents();
        assertThat(collectedEvents, hasSize(1));
        assertThat(collectedEvents.get(0).getData(), hasSize(1));
        assertThat(collectedEvents.get(0).getData().get(0).raw(), equalTo(expectedReplyWithBuffer.raw()));
    }

    @Test
    public void cannotSendToUnopenedServerTransceiver() throws IOException {
        expectTransceiverNotOpen();
        clientTransceiver.getServerWriter().writeBuffer(singletonList(createByteBuffer()));
    }

    @Test
    public void cannotWriteToServerWriterOfClosedClientTransceiver() throws IOException {
        expectTransceiverNotOpen();
        logger.info(">>>> opening server transceiver");
        serverTransceiver.open();
        logger.info(">>>> waiting for listening to start");
        waitForListeningToStart();

        logger.info(">>>> opening client transceiver");
        clientTransceiver.open();
        ThreadUtils.waitNoInterruption(250);
        logger.info(">>>> closing client transceiver");
        clientTransceiver.close();
        ThreadUtils.waitNoInterruption(250);

        logger.info(">>>> trying to write to closed client transceiver");
        clientTransceiver.getServerWriter().writeBuffer(singletonList(createByteBuffer()));
    }

    @Test
    public void connectExceptionThrownWhenCannotOpen() throws IOException {
        assumeThat(connectionType, equalTo(ConnectionType.TCP));

        // open client first, which will fail since server is not open.
        logger.info(">>>> opening client transceiver");
        thrown.expect(ConnectException.class);
        thrown.expectMessage("Connection refused");
        clientTransceiver.open();
    }

    private void expectTransceiverNotOpen() {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Transceiver not open");
    }
}
