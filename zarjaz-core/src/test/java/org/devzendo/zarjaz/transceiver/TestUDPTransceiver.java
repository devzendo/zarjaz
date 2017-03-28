package org.devzendo.zarjaz.transceiver;

import org.apache.log4j.BasicConfigurator;
import org.devzendo.commoncode.concurrency.ThreadUtils;
import org.devzendo.zarjaz.util.BufferDumper;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
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
public class TestUDPTransceiver {
    private static final Logger logger = LoggerFactory.getLogger(TestUDPTransceiver.class);

    {
        BasicConfigurator.configure();
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private Transceiver serverTransceiver;
    private Transceiver clientTransceiver;

    @Before
    public void setupTransceiver() throws IOException {
        serverTransceiver = UDPTransceiver.createServer(new InetSocketAddress(9876));
        clientTransceiver = UDPTransceiver.createClient(new InetSocketAddress(9876), true);
    }

    @After
    public void closeTransceiver() throws IOException {
        if (serverTransceiver != null) {
            serverTransceiver.close();
        }
        if (clientTransceiver != null) {
            clientTransceiver.close();
        }
    }

    @Test(timeout = 2000)
    public void bufferSentFromClientIsReceivedByServer() throws IOException {
        final EventCollectingTransceiverObserver observer = new EventCollectingTransceiverObserver();
        serverTransceiver.getServerEnd().addTransceiverObserver(observer);
        serverTransceiver.open();

        final ByteBuffer buf0 = createByteBuffer();
        final ByteBuffer expectedBuffer0 = duplicateOutgoingByteBuffer(buf0);
        final List<ByteBuffer> buf0List = singletonList(buf0);
        BufferDumper.dumpBuffer("original buf0 (duplicate rewound):", expectedBuffer0);
        clientTransceiver.getServerWriter().writeBuffer(buf0List);

        final ByteBuffer buf1 = createByteBuffer();
        final ByteBuffer expectedBuffer1 = duplicateOutgoingByteBuffer(buf1);
        final List<ByteBuffer> buf1List = singletonList(buf1);
        BufferDumper.dumpBuffer("original buf1 (duplicate rewound):", expectedBuffer1);
        clientTransceiver.getServerWriter().writeBuffer(buf1List);

        ThreadUtils.waitNoInterruption(500);

        logger.info("----------------------------------------------- data received ------------------------------------");
        final List<TransceiverObservableEvent> events = observer.getCollectedEvents();
        assertThat(events, hasSize(2));
        for (TransceiverObservableEvent event: events) {
            assertThat(event.getData(), hasSize(1));
            BufferDumper.dumpBuffer("test received", event.getData().get(0));
        }

        final ByteBuffer receivedBuffer0 = events.get(0).getData().get(0);
        BufferDumper.equalData("got0", receivedBuffer0, "expected0", expectedBuffer0);
        assertThat(receivedBuffer0, equalTo(expectedBuffer0));

        final ByteBuffer receivedBuffer1 = events.get(1).getData().get(0);
        BufferDumper.equalData("got1", receivedBuffer1, "expected1", expectedBuffer1);
        assertThat(receivedBuffer1, equalTo(expectedBuffer1));
    }
}