package org.devzendo.zarjaz;

import org.apache.log4j.BasicConfigurator;
import org.devzendo.commoncode.concurrency.ThreadUtils;
import org.devzendo.zarjaz.transceiver.NullTransceiver;
import org.devzendo.zarjaz.transceiver.Transceiver;
import org.devzendo.zarjaz.transceiver.TransceiverObservableEvent;
import org.devzendo.zarjaz.transceiver.TransceiverObserver;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
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

    private List<TransceiverObservableEvent> events = new ArrayList<>();
    private TransceiverObserver observer = new TransceiverObserver() {
        @Override
        public void eventOccurred(final TransceiverObservableEvent observableEvent) {
            logger.debug("Received a " + observableEvent.getClass().getSimpleName());
            events.add(observableEvent);
        }
    };
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

        final Transceiver.ClientTransceiver clientTransceiver = transceiver.getClientTransceiver();
        final Transceiver.ServerTransceiver serverTransceiver = transceiver.getServerTransceiver();
        clientTransceiver.addTransceiverObserver(observer);

        final ByteBuffer buf0 = createByteBuffer();
        serverTransceiver.writeBuffer(buf0);
        final ByteBuffer buf1 = createByteBuffer();
        serverTransceiver.writeBuffer(buf1);

        ThreadUtils.waitNoInterruption(500);

        assertThat(events, hasSize(2));
        assertThat(events.get(0).getData(), equalTo(buf0));
        assertThat(events.get(1).getData(), equalTo(buf1));
    }

    // TODO exception if data sent to non open transceiver

    private byte startByte = 0;
    private ByteBuffer createByteBuffer() {
        final ByteBuffer bb = ByteBuffer.allocate(10);
        for (int i = 0; i < 10; i++) {
            bb.put(startByte++);
        }
        return bb;
    }
}
