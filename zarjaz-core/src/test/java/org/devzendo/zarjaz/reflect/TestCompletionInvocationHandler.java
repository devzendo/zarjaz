package org.devzendo.zarjaz.reflect;

import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;
import org.devzendo.commoncode.concurrency.ThreadUtils;
import org.devzendo.zarjaz.logging.LoggingUnittestCase;
import org.devzendo.zarjaz.timeout.TimeoutScheduler;
import org.devzendo.zarjaz.transport.EndpointName;
import org.devzendo.zarjaz.transport.MethodInvocationTimeoutException;
import org.devzendo.zarjaz.transport.TransportInvocationHandler;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.devzendo.zarjaz.logging.IsLoggingEvent.loggingEvent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Copyright (C) 2008-2015 Matt Gumbley, DevZendo.org <http://devzendo.org>
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@RunWith(MockitoJUnitRunner.class)
public class TestCompletionInvocationHandler extends LoggingUnittestCase {

    private static final Logger logger = LoggerFactory.getLogger(TestCompletionInvocationHandler.class);
    private TimeoutScheduler timeoutScheduler;


    private interface SampleInterface {
        String getName();
        CompletableFuture<String> getNameFuture();
    }

    private TransportInvocationHandler transportInvocationHandler;

    private Method getNameMethod;
    private final Object[] noArgs = new Object[0];

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void reflectOnThingsAndStartScheduler() throws NoSuchMethodException {
        getNameMethod = SampleInterface.class.getMethod("getName", new Class[0]);
        timeoutScheduler = new TimeoutScheduler();
        timeoutScheduler.start();
    }

    @After
    public void stopScheduler() {
        timeoutScheduler.stop();
    }

    @Test
    public void loggingOfNonFutureMethodInvocations() throws NoSuchMethodException {
        // given
        transportInvocationHandler = new TransportInvocationHandler() {
            @Override
            public void invoke(final Method method, final Object[] args, final CompletableFuture<Object> future) {
                assertThat(method.getName(), equalTo("getName"));
                assertThat(args, arrayWithSize(0));
                future.complete("Bob");
            }
        };

        final CompletionInvocationHandler<SampleInterface> handler =
                new CompletionInvocationHandler<>(timeoutScheduler, new EndpointName("Sample"), SampleInterface.class, transportInvocationHandler, 500L);
        final Object irrelevantProxy = null;

        // when
        final Object returnValue = handler.invoke(irrelevantProxy, getNameMethod, noArgs);

        // then
        final List<LoggingEvent> events = capturingAppender.getEvents();
        assertThat(events, Matchers.hasSize(3));
        assertThat(events.get(0), loggingEvent(Level.DEBUG, "Invoking [Sample] org.devzendo.zarjaz.reflect.TestCompletionInvocationHandler$SampleInterface.getName"));
        assertThat(events.get(1), loggingEvent(Level.DEBUG, "Waiting on Future"));
        assertThat(events.get(2), loggingEvent(Level.DEBUG, "Wait over; returning value"));
        assertThat(returnValue, instanceOf(String.class));
        assertThat(returnValue, hasToString("Bob"));
    }

    // TODO test for methods that return a completablefuture

    @Test(timeout = 4000L)
    public void methodsCanTimeOut() throws NoSuchMethodException {
        // given
        transportInvocationHandler = new TransportInvocationHandler() {
            @Override
            public void invoke(final Method method, final Object[] args, final CompletableFuture<Object> future) {
                ThreadUtils.waitNoInterruption(2000L);
                future.complete("Bob");
            }
        };
        final CompletionInvocationHandler<SampleInterface> handler =
                new CompletionInvocationHandler<>(timeoutScheduler, new EndpointName("Sample"), SampleInterface.class, transportInvocationHandler, 500L);
        final Object irrelevantProxy = null;

        // then
        thrown.expect(MethodInvocationTimeoutException.class);
        thrown.expectMessage("method call [Sample] 'getName' timed out after 500ms");

        // when
        handler.invoke(irrelevantProxy, getNameMethod, noArgs);

        ThreadUtils.waitNoInterruption(3000L);
    }
}