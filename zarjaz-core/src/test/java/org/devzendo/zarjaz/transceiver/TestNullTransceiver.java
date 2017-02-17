package org.devzendo.zarjaz.transceiver;

import org.apache.log4j.BasicConfigurator;
import org.devzendo.commoncode.concurrency.ThreadUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

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
public class TestNullTransceiver {
    private static final Logger logger = LoggerFactory.getLogger(TestNullTransceiver.class);

    {
        BasicConfigurator.configure();
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private static class EventCollectingTransceiverObserver implements TransceiverObserver {
        public List<TransceiverObservableEvent> events = new ArrayList<>();

        @Override
        public void eventOccurred(final TransceiverObservableEvent observableEvent) {
            logger.debug("Received a " + observableEvent.getClass().getSimpleName());
            events.add(observableEvent);
        }
    }

    private NullTransceiver transceiver;

    @Before
    public void setupTransceiver() {
        transceiver = new NullTransceiver();
    }

    @After
    public void closeTransceiver() throws IOException {
        if (transceiver != null) {
            transceiver.close();
        }
    }

    @Test(timeout = 2000)
    public void sentBufferIsReceivedByClient() throws IOException {
        transceiver.open();
        final EventCollectingTransceiverObserver observer = new EventCollectingTransceiverObserver();

        final Transceiver.ClientTransceiver clientTransceiver = transceiver.getClientTransceiver();
        final Transceiver.ServerTransceiver serverTransceiver = transceiver.getServerTransceiver();
        clientTransceiver.addTransceiverObserver(observer);

        final List<ByteBuffer> buf0 = createByteBuffer();
        serverTransceiver.writeBuffer(buf0);
        final List<ByteBuffer> buf1 = createByteBuffer();
        serverTransceiver.writeBuffer(buf1);

        ThreadUtils.waitNoInterruption(500);

        assertThat(observer.events, hasSize(2));
        assertThat(observer.events.get(0).getData(), equalTo(buf0));
        assertThat(observer.events.get(1).getData(), equalTo(buf1));
    }

    @Test(timeout = 2000)
    public void bidirectionalTest() throws IOException {
        transceiver.open();

        // the client transceiver is that transceiver that talks TO THE CLIENT
        // the server transceiver is that transceiver that talks TO THE SERVER
        final Transceiver.ClientTransceiver clientTransceiver = transceiver.getClientTransceiver();
        final Transceiver.ServerTransceiver serverTransceiver = transceiver.getServerTransceiver();

        // server sending to client
        final EventCollectingTransceiverObserver clientObserver = new EventCollectingTransceiverObserver();
        clientTransceiver.addTransceiverObserver(clientObserver);

        final List<ByteBuffer> s2c0 = createByteBuffer();
        serverTransceiver.writeBuffer(s2c0);
        final List<ByteBuffer> s2c1 = createByteBuffer();
        serverTransceiver.writeBuffer(s2c1);

        ThreadUtils.waitNoInterruption(500);

        assertThat(clientObserver.events, hasSize(2));
        assertThat(clientObserver.events.get(0).getData(), equalTo(s2c0));
        assertThat(clientObserver.events.get(1).getData(), equalTo(s2c1));

        // and now in the other direction... client sending to server
        final EventCollectingTransceiverObserver serverObserver = new EventCollectingTransceiverObserver();
        final Transceiver.ClientTransceiver serverToClientTransceiver = serverTransceiver.getClientTransceiver();
        serverToClientTransceiver.addTransceiverObserver(serverObserver);

        final Transceiver.ServerTransceiver clientToServerTransceiver = clientTransceiver.getServerTransceiver();
        final List<ByteBuffer> c2s0 = createByteBuffer();
        clientToServerTransceiver.writeBuffer(c2s0);
        final List<ByteBuffer> c2s1 = createByteBuffer();
        clientToServerTransceiver.writeBuffer(c2s1);

        ThreadUtils.waitNoInterruption(500);

        assertThat(serverObserver.events, hasSize(2));
        assertThat(serverObserver.events.get(0).getData(), equalTo(c2s0));
        assertThat(serverObserver.events.get(1).getData(), equalTo(c2s1));
    }

    private class ReplyingTransceiverObserver implements TransceiverObserver {

        private final List<ByteBuffer> buffersToReplyWith;

        public ReplyingTransceiverObserver(List<ByteBuffer> buffersToReplyWith) {
            this.buffersToReplyWith = buffersToReplyWith;
        }

        @Override
        public void eventOccurred(final TransceiverObservableEvent observableEvent) {
            try {
                logger.debug("ReplyingTransceiverObserver got data " + observableEvent.getData() + " - replying");
                observableEvent.getServerTransceiver().writeBuffer(buffersToReplyWith);
                logger.debug("replied");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test(timeout = 1000)
    public void sentBufferCanBeRepliedTo() throws IOException {
        transceiver.open();

        // collect the reply coming from the server
        final EventCollectingTransceiverObserver serverReplyObserver = new EventCollectingTransceiverObserver();
        // the server transceiver is that transceiver that talks TO THE SERVER
        final Transceiver.ServerTransceiver toServerTransceiver = transceiver.getServerTransceiver();
        final Transceiver.ClientTransceiver serverReplyTransceiver = toServerTransceiver.getClientTransceiver();
        serverReplyTransceiver.addTransceiverObserver(serverReplyObserver);

        // the client will reply to its request
        final List<ByteBuffer> replyWith = createByteBuffer();
        final ReplyingTransceiverObserver replyingObserver = new ReplyingTransceiverObserver(replyWith);
        // the client transceiver is that transceiver that talks TO THE CLIENT
        final Transceiver.ClientTransceiver toClientTransceiver = transceiver.getClientTransceiver();
        toClientTransceiver.addTransceiverObserver(replyingObserver);

        // send the request to the server
        logger.debug("sending initial buffer to server");
        toServerTransceiver.writeBuffer(createByteBuffer());

        ThreadUtils.waitNoInterruption(500);

        // the server has received a reply to its request
        assertThat(serverReplyObserver.events, hasSize(1));
        assertThat(serverReplyObserver.events.get(0).getData(), equalTo(replyWith));
    }

    @Test
    public void inceptionClient() {
        inceptionNotSupported();
        transceiver.getClientTransceiver().getServerTransceiver().getClientTransceiver();
    }

    @Test
    public void inceptionServer() {
        inceptionNotSupported();
        transceiver.getServerTransceiver().getClientTransceiver().getServerTransceiver();
    }

    private void inceptionNotSupported() {
        thrown.expect(UnsupportedOperationException.class);
        thrown.expectMessage("This isn't Inception, you know...");
    }

    @Test
    public void cannotSendToUnopenedServerTransceiver() throws IOException {
        expectTransceiverNotOpen();
        transceiver.getServerTransceiver().writeBuffer(createByteBuffer());
    }

    @Test
    public void cannotSendToUnopenedClientTransceiver() throws IOException {
        expectTransceiverNotOpen();
        transceiver.getClientTransceiver().getServerTransceiver().writeBuffer(createByteBuffer());
    }

    @Test
    public void cannotSendToClosedServerTransceiver() throws IOException {
        expectTransceiverNotOpen();
        transceiver.open();
        ThreadUtils.waitNoInterruption(250);
        transceiver.close();
        ThreadUtils.waitNoInterruption(250);
        transceiver.getServerTransceiver().writeBuffer(createByteBuffer());
    }

    @Test
    public void cannotSendToClosedClientTransceiver() throws IOException {
        expectTransceiverNotOpen();
        transceiver.open();
        ThreadUtils.waitNoInterruption(250);
        transceiver.close();
        ThreadUtils.waitNoInterruption(250);
        transceiver.getClientTransceiver().getServerTransceiver().writeBuffer(createByteBuffer());
    }

    private void expectTransceiverNotOpen() {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Transceiver not open");
    }

    // TODO exception if data sent to non open transceiver

    private byte startByte = 0;
    private List<ByteBuffer> createByteBuffer() {
        final List<ByteBuffer> list = new ArrayList<>();
        final ByteBuffer bb = ByteBuffer.allocate(10);
        for (int i = 0; i < 10; i++) {
            bb.put(startByte++);
        }
        list.add(bb);
        return list;
    }
}
