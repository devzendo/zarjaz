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
import java.util.List;

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
public class TestNullTransceiver {
    private static final Logger logger = LoggerFactory.getLogger(TestNullTransceiver.class);

    {
        BasicConfigurator.configure();
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private Transceiver transceiver;

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
    public void bufferSentFromClientIsReceivedByServer() throws IOException {
        transceiver.open();
        final EventCollectingTransceiverObserver observer = new EventCollectingTransceiverObserver();

        transceiver.getServerEnd().addTransceiverObserver(observer);

        final ByteBuffer buf0 = createByteBuffer();
        final ByteBuffer expectedBuffer0 = duplicateOutgoingByteBuffer(buf0);
        transceiver.getServerWriter().writeBuffer(singletonList(buf0));

        final ByteBuffer buf1 = createByteBuffer();
        final ByteBuffer expectedBuffer1 = duplicateOutgoingByteBuffer(buf1);
        transceiver.getServerWriter().writeBuffer(singletonList(buf1));

        ThreadUtils.waitNoInterruption(500);

        final List<TransceiverObservableEvent> events = observer.getCollectedEvents();
        assertThat(events, hasSize(2));
        assertThat(events.get(0).getData(), hasSize(1));
        assertThat(events.get(0).getData().get(0), equalTo(expectedBuffer0));
        assertThat(events.get(1).getData(), hasSize(1));
        assertThat(events.get(1).getData().get(0), equalTo(expectedBuffer1));
    }

    @Test(timeout = 1000)
    public void sentBufferCanBeRepliedTo() throws IOException {
        transceiver.open();

        // the client collects the reply coming from the server
        final EventCollectingTransceiverObserver serverReplyObserver = new EventCollectingTransceiverObserver();
        transceiver.getClientEnd().addTransceiverObserver(serverReplyObserver);

        // the server will reply to its request
        final ByteBuffer replyWithBuffer = createByteBuffer();
        final ByteBuffer expectedReplyWithBuffer = duplicateOutgoingByteBuffer(replyWithBuffer);
        final ReplyingTransceiverObserver replyingObserver = new ReplyingTransceiverObserver(singletonList(replyWithBuffer));
        transceiver.getServerEnd().addTransceiverObserver(replyingObserver);

        // the server transceiver is that transceiver that talks TO THE SERVER
        final Transceiver.BufferWriter toServerTransceiver = transceiver.getServerWriter();
        // send the request to the server
        logger.debug("sending initial receiveBuffer to server");
        toServerTransceiver.writeBuffer(singletonList(createByteBuffer()));

        ThreadUtils.waitNoInterruption(500);

        // the server has received a reply to its request
        final List<TransceiverObservableEvent> collectedEvents = serverReplyObserver.getCollectedEvents();
        assertThat(collectedEvents, hasSize(1));
        assertThat(collectedEvents.get(0).getData(), hasSize(1));
        assertThat(collectedEvents.get(0).getData().get(0), equalTo(expectedReplyWithBuffer));
    }

    @Test
    public void cannotSendToUnopenedServerTransceiver() throws IOException {
        expectTransceiverNotOpen();
        transceiver.getServerWriter().writeBuffer(singletonList(createByteBuffer()));
    }

    @Test
    public void cannotSendToClosedServerTransceiver() throws IOException {
        expectTransceiverNotOpen();
        transceiver.open();
        ThreadUtils.waitNoInterruption(250);
        transceiver.close();
        ThreadUtils.waitNoInterruption(250);
        transceiver.getServerWriter().writeBuffer(singletonList(createByteBuffer()));
    }

    private void expectTransceiverNotOpen() {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Transceiver not open");
    }

    // TODO exception if data sent to non open transceiver
}
