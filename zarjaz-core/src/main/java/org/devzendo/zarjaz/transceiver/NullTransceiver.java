package org.devzendo.zarjaz.transceiver;

import org.devzendo.commoncode.patterns.observer.ObserverList;
import org.devzendo.commoncode.string.HexDump;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
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
 */
public class NullTransceiver implements Transceiver {
    private static final Logger logger = LoggerFactory.getLogger(NullTransceiver.class);

    // TODO internalise in NullObservableTransceiverEnd
    private static class NullTransceiverEnd {
        private final ObserverList<TransceiverObservableEvent> observers = new ObserverList<>();
    }
    private final NullTransceiverEnd clientEnd = new NullTransceiverEnd();
    private final NullTransceiverEnd serverEnd = new NullTransceiverEnd();

    private final ArrayBlockingQueue<Runnable> queue = new ArrayBlockingQueue<Runnable>(10);
    private final Thread dispatchThread;
    private volatile boolean active = false;

    private final NullBufferWriter sendToServer;
    private final NullBufferWriter sendToClient;
    private class NullBufferWriter implements BufferWriter {

        private final NullTransceiverEnd sendToEnd;
        private final String sendToEndName;
        private BufferWriter replyWriter;

        public NullBufferWriter(final NullTransceiverEnd sendToEnd, final String sendToEndName) {
            this.sendToEnd = sendToEnd;
            this.sendToEndName = sendToEndName;
        }

        public void setOtherEnd(final BufferWriter replyWriter) {
            this.replyWriter = replyWriter;
        }

        @Override
        public void writeBuffer(final List<ByteBuffer> data) throws IOException {
            if (!active) {
                throw new IllegalStateException("Transceiver not open");
            }
            logger.debug("Queueing ByteBuffer for sending to observers");

            queue.add(() -> {
                logger.debug("Dispatching queued ByteBuffer to " + sendToEndName + " end");
                NullTransceiver.dumpBuffers(data);
                sendToEnd.observers.eventOccurred(new DataReceived(data, replyWriter));
            });
        }
    }

    private final NullObservableTransceiverEnd serverObservableEnd;
    private final NullObservableTransceiverEnd clientObservableEnd;
    private class NullObservableTransceiverEnd implements ObservableTransceiverEnd {

        private final NullTransceiverEnd attachEnd;

        public NullObservableTransceiverEnd(final NullTransceiverEnd attachEnd) {
            this.attachEnd = attachEnd;
        }

        @Override
        public void addTransceiverObserver(final TransceiverObserver observer) {
            attachEnd.observers.addObserver(observer);
        }

        @Override
        public void removeTransceiverObserver(TransceiverObserver observer) {
            // TODO rename removeListener in common code to removeObserver
            attachEnd.observers.removeListener(observer);
        }
    }

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

        sendToClient = new NullBufferWriter(clientEnd, "client");
        sendToServer = new NullBufferWriter(serverEnd, "server");
        sendToClient.setOtherEnd(sendToServer);
        sendToServer.setOtherEnd(sendToClient);

        serverObservableEnd = new NullObservableTransceiverEnd(serverEnd);
        clientObservableEnd = new NullObservableTransceiverEnd(clientEnd);
    }

    private static void dumpBuffers(final List<ByteBuffer> buffers) {
        logger.debug(" --- There are " + buffers.size() + " buffer(s) ---");
        for (ByteBuffer buffer: buffers) {
            final byte[] array = buffer.array();
            final String[] strings = HexDump.hexDump(array, 0, buffer.limit());
            for (String string: strings) {
                logger.debug(string);
            }
        }
        logger.debug(" ---");
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
}
