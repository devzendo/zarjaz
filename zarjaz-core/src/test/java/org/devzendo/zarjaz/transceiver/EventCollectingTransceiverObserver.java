package org.devzendo.zarjaz.transceiver;

import org.devzendo.zarjaz.protocol.ByteBufferDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
public class EventCollectingTransceiverObserver implements TransceiverObserver {
    private static final Logger logger = LoggerFactory.getLogger(EventCollectingTransceiverObserver.class);

    private List<TransceiverObservableEvent> events = new ArrayList<>();

    public List<TransceiverObservableEvent> getCollectedEvents() {
        synchronized (events) {
            return Collections.unmodifiableList(events);
        }
    }

    @Override
    public void eventOccurred(final TransceiverObservableEvent observableEvent) {
        logger.debug("Received a " + observableEvent.getClass().getSimpleName());
        // Need to clone the incoming data, since these listeners are expected to fully process the data, then the
        // incoming buffer can be re-used. Since this observer collects for later use, the incoming buffer will likely
        // be overwritten by the next received data.
        if (observableEvent.isFailure()) {
            addEvent(observableEvent);
        } else {
            final List<ByteBuffer> cloneList = new ArrayList<>();
            final List<ByteBuffer> data = observableEvent.getData();
            for (ByteBuffer buffer : data) {
                final ByteBuffer cloneBuffer = ByteBuffer.allocate(buffer.limit());
                cloneBuffer.put(buffer);
                cloneBuffer.rewind();
                cloneList.add(cloneBuffer);
            }
            addEvent(new DataReceived(cloneList, observableEvent.getReplyWriter()));
        }
    }

    private void addEvent(TransceiverObservableEvent observableEvent) {
        synchronized (events) {
            events.add(observableEvent);
        }
    }
}
