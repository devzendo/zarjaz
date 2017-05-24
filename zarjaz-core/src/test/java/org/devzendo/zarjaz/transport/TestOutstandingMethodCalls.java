package org.devzendo.zarjaz.transport;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

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
public class TestOutstandingMethodCalls {
    private final OutstandingMethodCalls calls = new OutstandingMethodCalls();
    private final Method irrelevantMethod = null;
    private final CompletableFuture<Object> irrelevantFuture = null;
    private final byte[] irrelevantHash = null;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void emptiness() {
        assertEmptiness(0);
    }

    @Test
    public void futuresCanBeCompleted() throws ExecutionException, InterruptedException {
        final CompletableFuture<Object> future = new CompletableFuture<>();
        final OutstandingMethodCalls.OutstandingMethodCall call = new OutstandingMethodCalls.OutstandingMethodCall(irrelevantHash, irrelevantMethod, future, Optional.empty());
        call.resultReceived("Hello");
        assertThat(future.isDone(), equalTo(true));
        assertThat(future.get(), equalTo("Hello"));
    }

    @Test
    public void consumersAreSuppliedIfTheyAreSomeInsteadOfFuturesCompleted() throws ExecutionException, InterruptedException {
        final CompletableFuture<Object> future = new CompletableFuture<>();
        final Object[] consumed = new Object[]{null};
        final Consumer<Object> consumer = o -> consumed[0] = o;
        final OutstandingMethodCalls.OutstandingMethodCall call = new OutstandingMethodCalls.OutstandingMethodCall(irrelevantHash, irrelevantMethod, future, Optional.of(consumer));
        call.resultReceived("Hello");
        assertThat(future.isDone(), equalTo(false));
        assertThat(consumed[0], equalTo("Hello"));
    }

    @Test
    public void methodCallsCanBeRemoved() {
        final OutstandingMethodCalls.OutstandingMethodCall call = new OutstandingMethodCalls.OutstandingMethodCall(irrelevantHash, irrelevantMethod, irrelevantFuture, Optional.empty());
        final int sequence = calls.put(call);
        assertThat(calls.size(), equalTo(1));
        assertThat(calls.containsSequence(sequence), equalTo(true));

        assertThat(calls.remove(sequence), equalTo(call));

        assertEmptiness(sequence);
    }

    @Test
    public void methodFinishedThrowsIfNoSequencedMethodStored() {
        thrown.expect(SequenceNotFoundException.class);
        thrown.expectMessage("Completed method return with sequence 7 is not outstanding");
        calls.resultReceived(7, "boo");
    }

    @Test
    public void futuresCanBeCompletedByIncomingSequenceReturn() throws ExecutionException, InterruptedException {
        final CompletableFuture<Object> future = new CompletableFuture<>();
        final OutstandingMethodCalls.OutstandingMethodCall call = new OutstandingMethodCalls.OutstandingMethodCall(irrelevantHash, irrelevantMethod, future, Optional.empty());
        final int sequence = calls.put(call);
        calls.resultReceived(sequence, "Hello");
        assertThat(future.isDone(), equalTo(true));
        assertThat(future.get(), equalTo("Hello"));
    }

    @Test
    public void consumersAreSuppliedIfTheyAreSomeInsteadOfFuturesCompletedByIncomingSequenceReturn() throws ExecutionException, InterruptedException {
        final CompletableFuture<Object> future = new CompletableFuture<>();
        final Object[] consumed = new Object[]{null};
        final Consumer<Object> consumer = o -> consumed[0] = o;
        final OutstandingMethodCalls.OutstandingMethodCall call = new OutstandingMethodCalls.OutstandingMethodCall(irrelevantHash, irrelevantMethod, future, Optional.of(consumer));
        final int sequence = calls.put(call);
        calls.resultReceived(sequence, "Hello");
        assertThat(future.isDone(), equalTo(false));
        assertThat(consumed[0], equalTo("Hello"));
    }

    private void assertEmptiness(final int sequence) {
        assertThat(calls.size(), equalTo(0));
        assertThat(calls.containsSequence(sequence), equalTo(false));
        assertThat(calls.remove(sequence), nullValue());
    }
}
