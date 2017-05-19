package org.devzendo.zarjaz.reflect;

import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;
import org.devzendo.zarjaz.logging.LoggingUnittestCase;
import org.devzendo.zarjaz.timeout.TimeoutScheduler;
import org.devzendo.zarjaz.transport.EndpointName;
import org.devzendo.zarjaz.transport.TransportInvocationHandler;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Consumer;

import static org.devzendo.zarjaz.logging.IsLoggingEvent.loggingEvent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.equalTo;

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
public class TestCompletionMultipleReturnInvoker extends LoggingUnittestCase {
    private static final Logger logger = LoggerFactory.getLogger(TestCompletionMultipleReturnInvoker.class);
    private TimeoutScheduler timeoutScheduler;

    private interface SampleInterface {
        String getName();
    }

    private class DevNull<T> implements Consumer<T> {

        @Override
        public void accept(final T t) {
            // do nothing
        }
    }

    private final DevNull<String> irrelevantConsumer = new DevNull<>();

    private TransportInvocationHandler transportInvocationHandler;

    private Method getNameMethod;

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void reflectOnThingsAndStartScheduler() throws NoSuchMethodException {
        getNameMethod = SampleInterface.class.getMethod("getName");
        timeoutScheduler = new TimeoutScheduler();
        timeoutScheduler.start();
    }

    @After
    public void stopScheduler() {
        timeoutScheduler.stop();
    }

    @Test
    public void loggingOfMethodCallInvocations() {
        // given
        transportInvocationHandler = (method, args, future, timeoutRunnables) -> {
            assertThat(method.getName(), equalTo("getName"));
            assertThat(args, arrayWithSize(0));
            future.complete("Bob");
        };

        final CompletionMultipleReturnInvoker<SampleInterface> invoker =
                new CompletionMultipleReturnInvoker<>(timeoutScheduler, new EndpointName("Sample"), SampleInterface.class, transportInvocationHandler, 500L);

        // when
        invoker.invoke(getNameMethod, irrelevantConsumer, 500L);

        // then
        final List<LoggingEvent> copiedEvents = getLoggingEvents();
        assertThat(copiedEvents, Matchers.hasSize(1));
        assertThat(copiedEvents.get(0), loggingEvent(Level.DEBUG, "Invoking (multiple return) [Sample] org.devzendo.zarjaz.reflect.TestCompletionMultipleReturnInvoker$SampleInterface.getName()"));
//        assertThat(copiedEvents.get(1), loggingEvent(Level.DEBUG, "Waiting for timeout"));
//        assertThat(copiedEvents.get(2), loggingEvent(Level.DEBUG, "Wait over; removing timeout handler"));
    }
}
