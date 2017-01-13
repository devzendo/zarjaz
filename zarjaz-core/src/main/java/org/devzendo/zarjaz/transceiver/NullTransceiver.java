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

    private static class NullTransceiverEnd {
        private final ObserverList<TransceiverObservableEvent> observers = new ObserverList<>();
    }
    private final NullTransceiverEnd clientEnd = new NullTransceiverEnd();
    private final NullTransceiverEnd serverEnd = new NullTransceiverEnd();

    private final ArrayBlockingQueue<Runnable> queue = new ArrayBlockingQueue<Runnable>(10);
    private final Thread dispatchThread;
    private volatile boolean active = false;

    public NullTransceiver() {
        dispatchThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (active) {
                        logger.debug("Waiting for Runnable");
                        final Runnable runnable = queue.take();
                        logger.debug("Running queued Runnable");
                        runnable.run();
                        logger.debug("Run");
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
        active = true;
    }

    @Override
    public ClientTransceiver getClientTransceiver() {
        return new ClientTransceiver() {
            @Override
            public void addTransceiverObserver(final TransceiverObserver observer) {
                clientEnd.observers.addObserver(observer);
            }

            @Override
            public void removeTransceiverObserver(final TransceiverObserver observer) {
                clientEnd.observers.removeListener(observer);
            }

            @Override
            public ServerTransceiver getServerTransceiver() {
                return new ServerTransceiver() {
                    @Override
                    public void writeBuffer(final ByteBuffer data) throws IOException {
                        if (!active) {
                            throw new IllegalStateException("Transceiver not open");
                        }
                        serverEnd.observers.eventOccurred(new DataReceived(data));
                    }

                    @Override
                    public ClientTransceiver getClientTransceiver() {
                        throw new UnsupportedOperationException("This isn't Inception, you know...");
                    }
                };
            }
        };
    }

    @Override
    public ServerTransceiver getServerTransceiver() {
        return new ServerTransceiver() {
            @Override
            public void writeBuffer(final ByteBuffer data) throws IOException {
                if (!active) {
                    throw new IllegalStateException("Transceiver not open");
                }
                logger.debug("Queueing ByteBuffer for sending to observers");
                queue.add(new Runnable() {
                    @Override
                    public void run() {
                        logger.debug("Dispatching queued ByteBuffer");
                        clientEnd.observers.eventOccurred(new DataReceived(data));
                        logger.debug("Dispatched to observers");
                    }
                });
            }

            @Override
            public ClientTransceiver getClientTransceiver() {
                return new ClientTransceiver() {
                    @Override
                    public void addTransceiverObserver(final TransceiverObserver observer) {
                        serverEnd.observers.addObserver(observer);
                    }

                    @Override
                    public void removeTransceiverObserver(final TransceiverObserver observer) {
                        serverEnd.observers.removeListener(observer);
                    }

                    @Override
                    public ServerTransceiver getServerTransceiver() {
                        throw new UnsupportedOperationException("This isn't Inception, you know...");
                    }
                };
            }
        };
    }
}
