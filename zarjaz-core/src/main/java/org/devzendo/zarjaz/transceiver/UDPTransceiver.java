package org.devzendo.zarjaz.transceiver;

import org.devzendo.commoncode.patterns.observer.ObserverList;
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

    public UDPTransceiver(final SocketAddress remote) throws IOException {
        this.channel = DatagramChannel.open();
        this.remote = Optional.of(remote);
    }

    public UDPTransceiver(final DatagramChannel channel) {
        this.channel = channel;
        this.remote = Optional.empty();
    }

    public static UDPTransceiver createServer(final SocketAddress remote) throws IOException {
        final DatagramChannel channel = DatagramChannel.open();
        final DatagramSocket socket = channel.socket();
        socket.bind(remote);
        return new UDPTransceiver(remote, channel);
    }

    public static UDPTransceiver createClient(final SocketAddress remote, final boolean broadcast) throws IOException {
        final DatagramChannel channel = DatagramChannel.open();
        final DatagramSocket socket = channel.socket();
        socket.setBroadcast(broadcast);
        socket.connect(remote);
        return new UDPTransceiver(remote, channel);
    }

    public UDPTransceiver(final SocketAddress remote, final DatagramChannel channel) {
        this.remote = Optional.of(remote);
        this.channel = channel;
    }

    @Override
    public void open() throws IOException {
//        if (remote.isPresent()) {
//            final SocketAddress socketAddress = remote.get();
//            logger.info("Binding to address " + socketAddress);
//            channel.bind(socketAddress);
//        }
        if (serverEnd != null) {
            serverEnd.open();
        }
        if (clientEnd != null) {
            clientEnd.open();
        }
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
        return new RemoteBufferWriter(remote.get(), channel);
    }

    @Override
    public void close() throws IOException {
        logger.info("Closing UDPTransceiver");
        if (serverEnd != null) {
            serverEnd.close();
        }
        if (clientEnd != null) {
            clientEnd.close();
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

        public void run() {
            while (active) {
                logger.info("Waiting to receive incoming UDP data");
                receiveBuffer.clear();
                try {
                    // channel.receive: If there are fewer bytes remaining in the buffer than are required to hold the
                    // datagram then the remainder of the datagram is silently discarded.
                    // TODO METRIC can this be detected? add a checksum to messages?
                    final SocketAddress remote = channel.receive(receiveBuffer);
                    receiveBuffer.flip();
                    //BufferDumper.dumpBuffer("Received from SocketAddress " + remote, receiveBuffer);
                    final List<ByteBuffer> buffers = new ArrayList<ByteBuffer>();
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
                            buffers.add(chunk);
                        }
                    } while (length != 0);
                    // End of receiveBuffer stream..
                    if (logger.isDebugEnabled()) {
                        logger.debug("Received UDP data:");
                        BufferDumper.dumpBuffers(buffers);
                    }
                    final BufferWriter replyWriter = new RemoteBufferWriter(remote, channel);
                    fireEvent(new DataReceived(buffers, replyWriter));
                } catch (final ClosedByInterruptException cli) {
                    logger.info("Channel closed");
                } catch (final IOException ioe) {
                    logger.warn("Receive failure: " + ioe.getMessage(), ioe);
                    fireEvent(new TransceiverFailure(ioe));
                }
            }
            logger.info("UDP listening thread ending");
        }

        public void open() {
            logger.info("Opening UDPTransceiver " + name + " end");
            active = true;
            listeningThread.start();
        }

        public void close() {
            logger.info("Closing UDPTransceiver " + name + " end");
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
        public void removeTransceiverObserver(TransceiverObserver observer) {
            // TODO rename removeListener in common code to removeObserver
            observers.removeListener(observer);
        }
    };

    private static class RemoteBufferWriter implements BufferWriter {
        private final ByteBuffer writeBuffer = ByteBuffer.allocate(Protocol.BUFFER_SIZE * 4);
        private final SocketAddress socketAddress;
        private final DatagramChannel channel;

        public RemoteBufferWriter(final SocketAddress socketAddress, final DatagramChannel channel) {
            this.socketAddress = socketAddress;
            this.channel = channel;
        }

        @Override
        public void writeBuffer(final List<ByteBuffer> data) throws IOException {
            //logger.debug("Sending receiveBuffer list");
            // Need to write all the data buffers in one write, no way though?
            writeBuffer.clear();
            for (ByteBuffer eachData: data) {
                eachData.flip();
                //BufferDumper.dumpBuffer("individual send buffer", eachData);
                final int limit = eachData.limit();
                //logger.debug("putting length int of " + limit);
                writeBuffer.putInt(limit);
                writeBuffer.put(eachData);
            }
            //logger.debug("putting zero length");
            writeBuffer.putInt(0);
            writeBuffer.flip();
            if (logger.isDebugEnabled()) {
                BufferDumper.dumpBuffer("writeBuffer sending " + writeBuffer.limit() + " bytes to socket address " + socketAddress, writeBuffer);
            }
            channel.send(writeBuffer, socketAddress);
            //logger.info("writeBuffer sent");
        }
    }
}