package org.devzendo.zarjaz.transport;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

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
public class OutstandingMethodCalls {

    static class OutstandingMethodCall {
        private final byte[] hash;
        private final Method method;
        private final CompletableFuture<Object> future;
        private final Optional<Consumer<Object>> consumer;

        public OutstandingMethodCall(final byte[] hash, final Method method, final CompletableFuture<Object> future, final Optional<Consumer<Object>> consumer) {
            this.hash = hash;
            this.method = method;
            this.future = future;
            this.consumer = consumer;
        }

        public void resultReceived(final Object result) {
            if (consumer.isPresent()) {
                consumer.get().accept(result);
            } else {
                future.complete(result);
            }
        }

        public Method getMethod() {
            return method;
        }

        public boolean isMultipleReturn() {
            return consumer.isPresent();
        }
    }

    private final AtomicInteger sequence = new AtomicInteger(0);
    private final Map<Integer, OutstandingMethodCall> outstandingMethodCalls = new HashMap<>();
    private final ReentrantLock outstandingMethodCallsLock = new ReentrantLock();


    public OutstandingMethodCall remove(final int sequence) {
        outstandingMethodCallsLock.lock();
        final OutstandingMethodCall outstandingMethodCall = outstandingMethodCalls.remove(sequence);
        outstandingMethodCallsLock.unlock();
        return outstandingMethodCall;
    }

    public int put(final OutstandingMethodCall outstandingMethodCall) {
        outstandingMethodCallsLock.lock();
        try {
            final int seq = sequence.incrementAndGet();
            outstandingMethodCalls.put(seq, outstandingMethodCall);
            return seq;
        } finally {
            outstandingMethodCallsLock.unlock();
        }
    }

    public boolean containsSequence(final int sequence) {
        outstandingMethodCallsLock.lock();
        final boolean contains = outstandingMethodCalls.containsKey(sequence);
        outstandingMethodCallsLock.unlock();
        return contains;
    }

    public int size() {
        outstandingMethodCallsLock.lock();
        final int size = outstandingMethodCalls.size();
        outstandingMethodCallsLock.unlock();
        return size;
    }

    public OutstandingMethodCall get(final int sequence) {
        outstandingMethodCallsLock.lock();
        final OutstandingMethodCall outstandingMethodCall = outstandingMethodCalls.get(sequence);
        if (outstandingMethodCall == null) {
            throw noSequenceFound(sequence);
        }
        outstandingMethodCallsLock.unlock();
        return outstandingMethodCall;
    }

    public void resultReceived(final int sequence, final Object returnValue) {
        outstandingMethodCallsLock.lock();
        final OutstandingMethodCall outstandingMethodCall = outstandingMethodCalls.get(sequence);
        if (outstandingMethodCall == null) {
            throw noSequenceFound(sequence);
        }
        // Multiple return method calls stay in the outstanding list until timeout, when they are explicitly removed.
        if (!outstandingMethodCall.isMultipleReturn()) {
            remove(sequence);
        }
        outstandingMethodCallsLock.unlock();

        outstandingMethodCall.resultReceived(returnValue);
    }

    private SequenceNotFoundException noSequenceFound(final int sequence) {
        outstandingMethodCallsLock.unlock();
        return new SequenceNotFoundException("Completed method return with sequence " + sequence + " is not outstanding");
    }
}
