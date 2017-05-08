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
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.DatagramChannel;
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
public class UDPTransceiver implements Transceiver {
    private static final Logger logger = LoggerFactory.getLogger(UDPTransceiver.class);

    private final Optional<SocketAddress> remote;
    private final DatagramChannel channel;
    private volatile ServerObservableTransceiverEnd serverEnd;
    private volatile ClientObservableTransceiverEnd clientEnd;
    private volatile boolean active = false;

    public UDPTransceiver(final SocketAddress remote) throws IOException {
        this.channel = DatagramChannel.open();
        this.remote = Optional.of(remote);
    }

    public UDPTransceiver(final DatagramChannel channel) {
        this.channel = channel;
        this.remote = Optional.empty();
    }

    public static UDPTransceiver createServer(final SocketAddress remote) throws IOException {
        if (logger.isDebugEnabled()) {
            logger.debug("Creating server UDPTransceiver on address " + remote);
        }
        final DatagramChannel channel = DatagramChannel.open();
        final DatagramSocket socket = channel.socket();
        socket.bind(remote);
        return new UDPTransceiver(remote, channel);
    }

    public static UDPTransceiver createClient(final SocketAddress remote, final boolean broadcast) throws IOException {
        if (logger.isDebugEnabled()) {
            logger.debug("Creating client UDPTransceiver on address " + remote + "; " + (broadcast ? "broadcast" : "non-broadcast"));
        }
        final DatagramChannel channel = DatagramChannel.open();
        final DatagramSocket socket = channel.socket();
        socket.setBroadcast(broadcast);
        if (!broadcast) {
            socket.connect(remote);
        }
        return new UDPTransceiver(remote, channel);
    }

    public UDPTransceiver(final SocketAddress remote, final DatagramChannel channel) {
        this.remote = Optional.of(remote);
        this.channel = channel;
    }

    @Override
    public void open() throws IOException {
        if (serverEnd != null) {
            serverEnd.open();
        }
        if (clientEnd != null) {
            clientEnd.open();
        }
        active = true;
    }

    @Override
    public ObservableTransceiverEnd getClientEnd() {
        if (clientEnd == null) {
            clientEnd = new ClientObservableTransceiverEnd(channel);
        }
        return clientEnd;
    }

    @Override
    public ObservableTransceiverEnd getServerEnd() {
        if (serverEnd == null) {
            serverEnd = new ServerObservableTransceiverEnd(channel);
        }
        return serverEnd;
    }

    @Override
    public BufferWriter getServerWriter() {
        if (!remote.isPresent()) {
            throw new IllegalStateException("No endpoint to which to write");
        }
        return new RemoteBufferWriter(() -> active, remote.get(), channel);
    }

    @Override
    public void close() throws IOException {
        if (logger.isDebugEnabled()) {
            logger.debug("Closing UDPTransceiver");
        }
        active = false;
        if (serverEnd != null) {
            serverEnd.close();
        }
        if (clientEnd != null) {
            clientEnd.close();
        }
        if (channel.isConnected()) {
            channel.disconnect();
        }
        channel.close();
        if (logger.isDebugEnabled()) {
            logger.debug("Closed UDPTransceiver");
        }
    }

    private static class ClientObservableTransceiverEnd extends UDPObservableTransceiverEnd {
        public ClientObservableTransceiverEnd(final DatagramChannel channel) {
            super(channel, "client");
        }
    }

    private static class ServerObservableTransceiverEnd extends UDPObservableTransceiverEnd {
        public ServerObservableTransceiverEnd(final DatagramChannel channel) {
            super(channel, "server");
        }
    }

    private static abstract class UDPObservableTransceiverEnd implements ObservableTransceiverEnd, Runnable {
        protected final DatagramChannel channel;
        protected final String name;
        protected final ObserverList<TransceiverObservableEvent> observers = new ObserverList<>();
        protected final ByteBuffer receiveBuffer = ByteBuffer.allocate(Protocol.BUFFER_SIZE * 4);
        protected volatile boolean active = false;
        protected final Thread listeningThread;

        public UDPObservableTransceiverEnd(final DatagramChannel channel, final String name) {
            this.channel = channel;
            this.name = name;
            listeningThread = new Thread(this);
            listeningThread.setDaemon(true);
            listeningThread.setName("UDPTransceiver " + name + " listening thread");
        }

        @Override
        public void run() {
            while (active) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Waiting to receive incoming UDP data");
                }
                receiveBuffer.clear();
                try {
                    // channel.receive: If there are fewer bytes remaining in the buffer than are required to hold the
                    // datagram then the remainder of the datagram is silently discarded.
                    // TODO METRIC can this be detected? add a checksum to messages?
                    final SocketAddress remote = channel.receive(receiveBuffer);
                    receiveBuffer.flip();
                    //BufferDumper.dumpBuffer("Received from SocketAddress " + remote, receiveBuffer);
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
                        logger.debug("Received UDP data:");
                        BufferDumper.dumpBuffers(buffers);
                    }
                    final BufferWriter replyWriter = new RemoteBufferWriter(() -> active, remote, channel);
                    fireEvent(new DataReceived(buffers, replyWriter));
                } catch (final ClosedByInterruptException cli) {
                    logger.debug("UDP listening thread interrupted");
                } catch (final IOException ioe) {
                    logger.warn("Receive failure: " + ioe.getMessage(), ioe);
                    fireEvent(new TransceiverFailure(ioe));
                }
            }
            if (logger.isDebugEnabled()) {
                logger.debug("UDP listening thread ending");
            }
        }

        public void open() {
            if (logger.isDebugEnabled()) {
                logger.debug("Opening UDPTransceiver " + name + " end");
            }
            active = true;
            listeningThread.start();
        }

        public void close() {
            if (logger.isDebugEnabled()) {
                logger.debug("Closing UDPTransceiver " + name + " end");
            }
            active = false;
            if (listeningThread != null && listeningThread.isAlive()) {
                listeningThread.interrupt();
            }
        }

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
    };

    private static class RemoteBufferWriter implements BufferWriter {
        private final WritableByteBuffer writeBuffer = DefaultWritableByteBuffer.allocate(Protocol.BUFFER_SIZE * 4);
        private final Supplier<Boolean> isActive;
        private final SocketAddress socketAddress;
        private final DatagramChannel channel;

        public RemoteBufferWriter(final Supplier<Boolean> isActive, final SocketAddress socketAddress, final DatagramChannel channel) {
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
            for (final ReadableByteBuffer readable : data) {
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
            channel.send(writeBuffer.raw(), socketAddress);
            if (logger.isDebugEnabled()) {
                logger.debug("writeBuffer sent");
            }
        }
    }
}
