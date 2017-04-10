package org.devzendo.zarjaz.transceiver;

import org.devzendo.commoncode.patterns.observer.ObserverList;
import org.devzendo.zarjaz.nio.DefaultReadableByteBuffer;
import org.devzendo.zarjaz.nio.DefaultWritableByteBuffer;
import org.devzendo.zarjaz.nio.ReadableByteBuffer;
import org.devzendo.zarjaz.nio.WritableByteBuffer;
import org.devzendo.zarjaz.protocol.Protocol;
import org.devzendo.zarjaz.util.BufferDumper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

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
public class TCPTransceiver implements Transceiver {
    private static final Logger logger = LoggerFactory.getLogger(TCPTransceiver.class);

    public static TCPTransceiver createServer(final SocketAddress local) throws IOException {
        if (logger.isDebugEnabled()) {
            logger.debug("Creating server TCPTransceiver on address " + local);
        }
        final ServerSocketChannel channel = ServerSocketChannel.open();
        channel.socket().bind(local);

        return new TCPTransceiver(local, channel);
    }

    public static TCPTransceiver createClient(final SocketAddress remote) throws IOException {
        if (logger.isDebugEnabled()) {
            logger.debug("Creating client TCPTransceiver on address " + remote);
        }
        final SocketChannel channel = SocketChannel.open();
        final Socket socket = channel.socket();
        socket.connect(remote);
        return new TCPTransceiver(remote, channel);
    }

    private final ThreadGroup threadGroup = new ThreadGroup("TCPTransceiver");
    private final Optional<Thread> acceptingThread;
    private final Optional<ConnectionHandler> clientConnectionHandler;
    private final SocketAddress address;
    private final SocketChannel channel;
    private final TCPObservableTransceiverEnd serverEnd = new TCPObservableTransceiverEnd();
    private final TCPObservableTransceiverEnd clientEnd = new TCPObservableTransceiverEnd();

    private volatile boolean active = false;

    public TCPTransceiver(final SocketAddress remote, final SocketChannel channel) {
        this.address = remote;
        this.channel = channel;
        this.acceptingThread = Optional.empty();
        this.clientConnectionHandler = Optional.of(new ConnectionHandler(clientEnd, channel, threadGroup, "client"));
    }

    public TCPTransceiver(final SocketAddress local, final ServerSocketChannel channel) {
        this.address = local;
        this.channel = null;
        final Thread thread = new Thread(new AcceptHandler(() -> active, channel, threadGroup, serverEnd));
        thread.setName("TCPTransceiver accept thread");
        thread.setDaemon(true);
        this.acceptingThread = Optional.of(thread);
        this.clientConnectionHandler = Optional.empty();
    }

    @Override
    public void open() throws IOException {
        acceptingThread.ifPresent(Thread::start);
        clientConnectionHandler.ifPresent(ConnectionHandler::open);
        active = true;
    }

    @Override
    public ObservableTransceiverEnd getClientEnd() {
        return clientEnd;
    }

    @Override
    public TCPObservableTransceiverEnd getServerEnd() {
        return serverEnd;
    }

    @Override
    public BufferWriter getServerWriter() {
        if (channel != null) {
            throw new IllegalStateException("No endpoint to which to write");
        }
        return new RemoteBufferWriter(() -> active, address, channel);
    }

    @Override
    public void close() throws IOException {
        if (logger.isDebugEnabled()) {
            logger.debug("Closing TCPTransceiver");
        }
        active = false;
        threadGroup.interrupt();
        acceptingThread.ifPresent(Thread::interrupt);
        clientConnectionHandler.ifPresent(ConnectionHandler::close);
        channel.close();
    }

    private static class AcceptHandler implements Runnable {
        private final Supplier<Boolean> isActive;
        private final ServerSocketChannel channel;
        private final ThreadGroup threadGroup;
        private final TCPObservableTransceiverEnd serverEnd;

        public AcceptHandler(final Supplier<Boolean> isActive, final ServerSocketChannel channel, final ThreadGroup threadGroup, final TCPObservableTransceiverEnd serverEnd)
        {
            this.isActive = isActive;
            this.channel = channel;
            this.threadGroup = threadGroup;
            this.serverEnd = serverEnd;
        }

        @Override
        public void run() {
            while (isActive.get()) {
                try {
                    logger.debug("Waiting to accept a connection");
                    final SocketChannel socketChannel = channel.accept();
                    logger.debug("Connection from " + socketChannel.getRemoteAddress());
                    final ConnectionHandler connectionHandler = new ConnectionHandler(serverEnd, socketChannel, threadGroup, "server");
                    connectionHandler.open(); // creates thread in group

                } catch (final IOException e) {
                    logger.warn("Accept failure: " + e.getMessage());
                }
            }
            logger.debug("Stopping " + channel.socket().getInetAddress());
            try {
                channel.close();
            } catch (final IOException e) {
                logger.debug("Accept channel close failure: " + e.getMessage());
            }
            logger.debug("TCPTransceiver accept thread ending");
        }
    }

    private static class ConnectionHandler implements Runnable {
        protected final TCPObservableTransceiverEnd transceiverEnd;
        protected final SocketChannel channel;
        protected final ThreadGroup threadGroup;
        protected final String name;

        protected final ObserverList<TransceiverObservableEvent> observers = new ObserverList<>();
        protected final ByteBuffer receiveBuffer = ByteBuffer.allocate(Protocol.BUFFER_SIZE * 4);
        protected volatile boolean active = false;
        protected final Thread readingThread;

        public ConnectionHandler(final TCPObservableTransceiverEnd transceiverEnd, final SocketChannel channel, final ThreadGroup threadGroup, final String name) {
            this.transceiverEnd = transceiverEnd;
            this.channel = channel;
            this.threadGroup = threadGroup;
            this.name = name;
            // TODO use a threadpool/executor
            readingThread = new Thread(threadGroup, this);
            readingThread.setDaemon(true);
            readingThread.setName("TCPTransceiver " + name + " reading thread");
        }

        public void run() {
            while (active) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Waiting to receive incoming TCP data");
                }
                receiveBuffer.clear();
                try {
                    final int bytesRead = channel.read(receiveBuffer);
                    receiveBuffer.flip();
                    //BufferDumper.dumpBuffer("Received from SocketAddress " + address, receiveBuffer);
                    final List<ReadableByteBuffer> buffers = new ArrayList<ReadableByteBuffer>();
                    int length = 0;
                    do {
                        //logger.debug("Top of chunk loop, receiveBuffer position " + receiveBuffer.position() + " limit " + receiveBuffer.limit() + " - getting a length int...");
                        length = receiveBuffer.getInt();
                        if (length != 0) {
                            //logger.debug("Received chunk length of " + length);
                            //BufferDumper.dumpBuffer("after getting length int, receive buffer", receiveBuffer);
                            // Extract slice of incoming data, of the correct length, add to output list.
                            final ByteBuffer chunk = receiveBuffer.slice();
                            chunk.limit(length);
                            //BufferDumper.dumpBuffer("chunk buffer", chunk);
                            //logger.debug("chunk limit is " + chunk.limit() + " remaining " + chunk.remaining() + " position " + chunk.position());
                            //logger.debug("receiveBuffer limit is " + receiveBuffer.limit() + " remaining " + receiveBuffer.remaining() + " position " + receiveBuffer.position());
                            receiveBuffer.position(receiveBuffer.position() + length);
                            buffers.add(new DefaultReadableByteBuffer(chunk));
                        }
                    } while (length != 0);
                    // End of receiveBuffer stream..
                    if (logger.isDebugEnabled()) {
                        logger.debug("Received TCP data:");
                        BufferDumper.dumpBuffers(buffers);
                    }
                    final BufferWriter replyWriter = new RemoteBufferWriter(() -> active, channel.getRemoteAddress(), channel);
                    transceiverEnd.fireEvent(new DataReceived(buffers, replyWriter));
                } catch (final ClosedByInterruptException cli) {
                    logger.debug("Channel closed");
                } catch (final IOException ioe) {
                    logger.warn("Receive failure: " + ioe.getMessage(), ioe);
                    transceiverEnd.fireEvent(new TransceiverFailure(ioe));
                }
            }
            if (logger.isDebugEnabled()) {
                logger.debug("TCP reading thread ending");
            }
        }

        public void open() {
            if (logger.isDebugEnabled()) {
                logger.debug("Opening TCPTransceiver " + name + " end");
            }
            active = true;
            readingThread.start();
        }

        public void close() {
            if (logger.isDebugEnabled()) {
                logger.debug("Closing TCPTransceiver " + name + " end");
            }
            active = false;
            if (readingThread != null && readingThread.isAlive()) {
                readingThread.interrupt();
            }
        }
    }

    private static class TCPObservableTransceiverEnd implements ObservableTransceiverEnd {
        protected final ObserverList<TransceiverObservableEvent> observers = new ObserverList<>();

        public void fireEvent(final TransceiverObservableEvent event) {
            observers.eventOccurred(event);
        }

        @Override
        public void addTransceiverObserver(final TransceiverObserver observer) {
            observers.addObserver(observer);
        }

        @Override
        public void removeTransceiverObserver(TransceiverObserver observer) {
            // TODO rename removeListener in common code to removeObserver
            observers.removeListener(observer);
        }
    }

    private static class RemoteBufferWriter implements BufferWriter {
        private final WritableByteBuffer writeBuffer = DefaultWritableByteBuffer.allocate(Protocol.BUFFER_SIZE * 4);
        private final Supplier<Boolean> isActive;
        private final SocketAddress socketAddress;
        private final SocketChannel channel;

        public RemoteBufferWriter(final Supplier<Boolean> isActive, final SocketAddress socketAddress, final SocketChannel channel) {
            this.isActive = isActive;
            this.socketAddress = socketAddress;
            this.channel = channel;
        }

        @Override
        public void writeBuffer(final List<ReadableByteBuffer> data) throws IOException {
            if (!isActive.get()) {
                throw new IllegalStateException("Transceiver not open");
            }

            if (logger.isDebugEnabled()) {
                logger.debug("Sending buffer list");
            }
            // Need to write all the data buffers in one write, no way though?
            writeBuffer.clear();
            for (ReadableByteBuffer readable: data) {
                // TODO test for this
                if (readable.remaining() == 0) {
                    throw new IOException("RemoteBufferWriter has been given pre-flipped ByteBuffers");
                }
                if (logger.isDebugEnabled()) {
                    BufferDumper.dumpBuffer("individual send buffer", readable.raw());
                }
                final int limit = readable.limit();
                if (logger.isDebugEnabled()) {
                    logger.debug("putting length int of " + limit);
                }
                writeBuffer.putInt(limit);
                writeBuffer.put(readable);
            }
            if (logger.isDebugEnabled()) {
                logger.debug("putting zero length");
            }
            writeBuffer.putInt(0);
            writeBuffer.flip();
            if (logger.isDebugEnabled()) {
                BufferDumper.dumpBuffer("writeBuffer sending " + writeBuffer.limit() + " bytes to socket address " + socketAddress, writeBuffer.raw());
            }
            channel.write(writeBuffer.raw());
            if (logger.isDebugEnabled()) {
                logger.debug("writeBuffer sent");
            }
        }
    }
}
