package org.devzendo.zarjaz.transport;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

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
        public final byte[] hash;
        public final Method method;
        public final CompletableFuture<Object> future;

        public OutstandingMethodCall(final byte[] hash, final Method method, final CompletableFuture<Object> future) {
            this.hash = hash;
            this.method = method;
            this.future = future;
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
        final int seq = sequence.incrementAndGet();
        outstandingMethodCalls.put(seq, outstandingMethodCall);
        outstandingMethodCallsLock.unlock();
        return seq;
    }

    public boolean containsSequence(final int seq) {
        outstandingMethodCallsLock.lock();
        final boolean contains = outstandingMethodCalls.containsKey(seq);
        outstandingMethodCallsLock.unlock();
        return contains;
    }

    public int size() {
        outstandingMethodCallsLock.lock();
        final int size = outstandingMethodCalls.size();
        outstandingMethodCallsLock.unlock();
        return size;
    }
}
