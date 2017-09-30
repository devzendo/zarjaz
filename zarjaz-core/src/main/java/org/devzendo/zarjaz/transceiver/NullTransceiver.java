package org.devzendo.zarjaz.transceiver;

import org.devzendo.commoncode.concurrency.ThreadUtils;
import org.devzendo.commoncode.patterns.observer.ObserverList;
import org.devzendo.zarjaz.nio.ReadableByteBuffer;
import org.devzendo.zarjaz.util.BufferDumper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
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
 *
 * --
 * The NullTransceiver is an in-process transceiver that provides all the semantics of other transceivers, including
 * multiple return and timeout management. Especially useful in tests.
 */
public class NullTransceiver implements Transceiver {
    private static final Logger logger = LoggerFactory.getLogger(NullTransceiver.class);

    private final ArrayBlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(10);
    private final Thread dispatchThread;
    private volatile boolean active = false;

    private final NullBufferWriter sendToServer;
    private final NullBufferWriter sendToClient;

    private class NullBufferWriter implements BufferWriter {

        private final NullObservableTransceiverEnd sendToEnd;
        private final String sendToEndName;
        private BufferWriter replyWriter;

        public NullBufferWriter(final NullObservableTransceiverEnd sendToEnd, final String sendToEndName) {
            this.sendToEnd = sendToEnd;
            this.sendToEndName = sendToEndName;
        }

        public void setOtherEnd(final BufferWriter replyWriter) {
            this.replyWriter = replyWriter;
        }

        @Override
        public void writeBuffer(final List<ReadableByteBuffer> data) throws IOException {
            if (!active) {
                throw new IllegalStateException("Transceiver not open");
            }
            logger.debug("Queueing ByteBuffer for sending to observers");
            for (final ReadableByteBuffer buffer : data) {
                if (buffer.remaining() == 0) {
                    throw new IOException("RemoteBufferWriter has been given pre-flipped ByteBuffers");
                }
                buffer.raw().rewind(); // now fake the sending over a channel
            }

            queue.add(() -> {
                logger.debug("Dispatching queued ByteBuffer to " + sendToEndName + " end");
                BufferDumper.dumpBuffers(data);
                sendToEnd.fireEvent(new DataReceived(data, replyWriter));
            });
        }
    }

    private final NullObservableTransceiverEnd serverObservableEnd;
    private final NullObservableTransceiverEnd clientObservableEnd;

    private static class NullObservableTransceiverEnd implements ObservableTransceiverEnd {

        private final ObserverList<TransceiverObservableEvent> observers = new ObserverList<>();

        public void fireEvent(final TransceiverObservableEvent event) {
            observers.eventOccurred(event);
        }

        @Override
        public void addTransceiverObserver(final TransceiverObserver observer) {
            observers.addObserver(observer);
        }

        @Override
        public void removeTransceiverObserver(final TransceiverObserver observer) {
            // TODO rename removeListener in common code to removeObserver
            observers.removeListener(observer);
        }
    }

    public NullTransceiver() {
        dispatchThread = new Thread(() -> {
            try {
                while (active) {
                    logger.debug("Waiting for Runnable");
                    final Runnable runnable = queue.take();
                    logger.debug("Running queued Runnable");
                    runnable.run();
                    logger.debug("Run");
                }
                logger.info("Dispatch thread ended");
            } catch (final InterruptedException e) {
                logger.warn("Dispatch thread interrupted");
                active = false;
            }
        });
        dispatchThread.setDaemon(true);
        dispatchThread.setName("NullTransceiver dispatch thread");

        serverObservableEnd = new NullObservableTransceiverEnd();
        clientObservableEnd = new NullObservableTransceiverEnd();

        sendToClient = new NullBufferWriter(clientObservableEnd, "client");
        sendToServer = new NullBufferWriter(serverObservableEnd, "server");
        sendToClient.setOtherEnd(sendToServer);
        sendToServer.setOtherEnd(sendToClient);
    }

    @Override
    public String toString() {
        return "NullTransceiver";
    }

    @Override
    public void close() throws IOException {
        logger.info("Closing NullTransceiver");
        active = false;
        while (dispatchThread.isAlive()) {
            dispatchThread.interrupt();
            ThreadUtils.waitNoInterruption(100);
        }
        logger.info("Closed NullTransceiver");
    }

    @Override
    public void open() {
        if (!active) {
            logger.info("Opening NullTransceiver");
            dispatchThread.start();
            active = true;
        }
    }

    @Override
    public ObservableTransceiverEnd getClientEnd() {
        return clientObservableEnd;
    }

    @Override
    public ObservableTransceiverEnd getServerEnd() {
        return serverObservableEnd;
    }

    @Override
    public BufferWriter getServerWriter() {
        return sendToServer;
    }

    @Override
    public boolean supportsMultipleReturn() {
        // You can attach multiple transports to the same NullTransceiver, and they will all receive incoming requests,
        // and are able to reply - so this transceiver supports multiple return.
        return true;
    }
}
