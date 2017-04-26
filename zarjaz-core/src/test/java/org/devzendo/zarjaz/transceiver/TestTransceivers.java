package org.devzendo.zarjaz.transceiver;

import org.apache.log4j.BasicConfigurator;
import org.devzendo.commoncode.concurrency.ThreadUtils;
import org.devzendo.zarjaz.nio.ReadableByteBuffer;
import org.devzendo.zarjaz.util.BufferDumper;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.devzendo.zarjaz.transceiver.BufferUtils.createByteBuffer;
import static org.devzendo.zarjaz.transceiver.BufferUtils.duplicateOutgoingByteBuffer;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

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
public class TestTransceivers {
    private static final Logger logger = LoggerFactory.getLogger(TestTransceivers.class);

    {
        BasicConfigurator.configure();
    }

    @Parameterized.Parameters
    public static Collection<Class> data() {
        return asList(NullTransceiver.class, UDPTransceiver.class, TCPTransceiver.class);
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private final Class<? extends Transceiver> transceiverClass;
    private Transceiver serverTransceiver;
    private Transceiver clientTransceiver;

    // runs before mocks initialised, so do real construction in @Before.
    public TestTransceivers(final Class<? extends Transceiver> transceiverClass) {
        this.transceiverClass = transceiverClass;
    }

    @Before
    public void setupTransceiver() throws IOException {
        logger.debug("start of setupTransceiver");
        if (transceiverClass.equals(NullTransceiver.class)) {
            serverTransceiver = new NullTransceiver();
            clientTransceiver = serverTransceiver;
        } else if (transceiverClass.equals(UDPTransceiver.class)) {
            serverTransceiver = UDPTransceiver.createServer(new InetSocketAddress(9876));
            clientTransceiver = UDPTransceiver.createClient(new InetSocketAddress(9876), false);
        } else if (transceiverClass.equals(TCPTransceiver.class)) {
            serverTransceiver = TCPTransceiver.createServer(new InetSocketAddress(9876));
            clientTransceiver = TCPTransceiver.createClient(new InetSocketAddress(9876));
        }
        logger.debug("end of setupTransceiver");
    }

    @After
    public void closeTransceiver() throws IOException {
        logger.debug("start of closeTransceiver");
        if (serverTransceiver != null) {
            serverTransceiver.close();
        }
        if (clientTransceiver != null) {
            clientTransceiver.close();
        }
        logger.debug("end of setupTransceiver");
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
        for (TransceiverObservableEvent event: events) {
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

        serverTransceiver.open();
        waitForListeningToStart();

        clientTransceiver.open();
        ThreadUtils.waitNoInterruption(250);
        clientTransceiver.close();
        ThreadUtils.waitNoInterruption(250);

        clientTransceiver.getServerWriter().writeBuffer(singletonList(createByteBuffer()));
    }

    private void expectTransceiverNotOpen() {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Transceiver not open");
    }

    // TODO exception if data sent to non open transceiver
}
