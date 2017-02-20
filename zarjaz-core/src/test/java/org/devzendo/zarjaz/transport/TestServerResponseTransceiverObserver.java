package org.devzendo.zarjaz.transport;

import org.devzendo.zarjaz.protocol.ByteBufferEncoder;
import org.devzendo.zarjaz.protocol.Protocol;
import org.devzendo.zarjaz.transceiver.DataReceived;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

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
public class TestServerResponseTransceiverObserver {
    private static final int SEQUENCE = 69;

    private final ByteBufferEncoder encoder = new ByteBufferEncoder();
    private final Map<Integer, TransceiverTransport.OutstandingMethodCall> outstandingMethodCalls = new ConcurrentHashMap<>();
    private final CompletableFuture<Object> future = new CompletableFuture<>();
    private final TransceiverTransport.ServerResponseTransceiverObserver observer = new TransceiverTransport.ServerResponseTransceiverObserver(outstandingMethodCalls);

    private interface SampleInterface {
        String someMethod();
    }

    @Before
    public void setupFuture() { // future? There's no future in England's dreaming...
        outstandingMethodCalls.put(SEQUENCE, new TransceiverTransport.OutstandingMethodCall(new byte[16], SampleInterface.class.getDeclaredMethods()[0], future));
    }

    @Test
    public void decodeValidResponse() throws ExecutionException, InterruptedException {
        observeStringResponseDataReceived();
        // outstanding method call is no longer outstanding
        assertThat(outstandingMethodCalls, not(hasKey(SEQUENCE)));
        assertThat(future.isDone(), is(true));
        assertThat(future.isCancelled(), is(false));
        assertThat(future.isCompletedExceptionally(), is(false));
        assertThat(future.get(), is("ReplyString"));
        // TODO METRIC increment successful method response decode
    }

    // TODO no sequence in outstanding method calls
    // TODO wrong data in response?
    // TODO shall we send the outgoing hash in the response, match this up?
    // we could still have bad data in the response
    // TODO failures - TransceiverFailure observed

    private void observeStringResponseDataReceived() {
        encoder.writeByte(Protocol.InitialFrameType.METHOD_RETURN_RESULT.getInitialFrameType());
        encoder.writeInt(SEQUENCE);
        encoder.writeString("ReplyString");
        observer.eventOccurred(new DataReceived(encoder.getBuffers(), null));
    }
}
