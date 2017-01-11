package org.devzendo.zarjaz.transceiver;

import org.devzendo.commoncode.patterns.observer.ObserverList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;

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
public class NullTransceiver implements Transceiver {
    private static final Logger logger = LoggerFactory.getLogger(NullTransceiver.class);

    private final ObserverList<TransceiverObservableEvent> observers = new ObserverList<>();
    private final ArrayBlockingQueue<ByteBuffer> queue = new ArrayBlockingQueue<ByteBuffer>(10);
    private final Thread dispatchThread;
    private volatile boolean active = true;

    public NullTransceiver() {
        dispatchThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (active) {
                        logger.debug("Waiting for ByteBuffer");
                        final ByteBuffer byteBuffer = queue.take();
                        logger.debug("Dispatching queued ByteBuffer");
                        observers.eventOccurred(new DataReceived(byteBuffer));
                        logger.debug("Dispatched to observers");
                    }
                } catch (InterruptedException e) {
                    logger.warn("Dispatch thread interrupted");
                    active = false;
                }
            }
        });
        dispatchThread.setDaemon(true);
        dispatchThread.setName("NullTransceiver dispatch thread");
    }

    @Override
    public void close() throws IOException {
        logger.info("Closing NullTransceiver");
        active = false;
        if (dispatchThread.isAlive()) {
            dispatchThread.interrupt();
        }
    }

    @Override
    public void open() {
        logger.info("Opening NullTransceiver");
        dispatchThread.start();
    }

    @Override
    public ClientTransceiver getClientTransceiver() {
        return new ClientTransceiver() {
            @Override
            public void addTransceiverObserver(final TransceiverObserver observer) {
                observers.addObserver(observer);
            }

            @Override
            public void removeTransceiverObserver(final TransceiverObserver observer) {
                observers.removeListener(observer);
            }
        };
    }

    @Override
    public ServerTransceiver getServerTransceiver() {
        return new ServerTransceiver() {
            @Override
            public void writeBuffer(final ByteBuffer data) throws IOException {
                logger.debug("Queueing ByteBuffer for sending to observers");
                queue.add(data);
            }
        };
    }
}
