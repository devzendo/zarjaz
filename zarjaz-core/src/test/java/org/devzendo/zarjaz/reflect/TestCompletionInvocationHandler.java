package org.devzendo.zarjaz.reflect;

import org.apache.log4j.BasicConfigurator;
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
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.devzendo.commoncode.concurrency.ThreadUtils.waitNoInterruption;
import static org.devzendo.commoncode.logging.IsLoggingEvent.loggingEvent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Copyright (C) 2008-2015 Matt Gumbley, DevZendo.org http://devzendo.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class TestCompletionInvocationHandler extends LoggingUnittestCase {

    private static final Logger logger = LoggerFactory.getLogger(TestCompletionInvocationHandler.class);
    private TimeoutScheduler timeoutScheduler;

    private interface SampleInterface {
        String getName();
        Future<String> getNameFuture();
    }

    private TransportInvocationHandler transportInvocationHandler;

    private Method getNameMethod;
    private Method getNameFutureMethod;
    private final Object[] noArgs = new Object[0];

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void reflectOnThingsAndStartScheduler() throws NoSuchMethodException {
        getNameMethod = SampleInterface.class.getMethod("getName", new Class[0]);
        getNameFutureMethod = SampleInterface.class.getMethod("getNameFuture", new Class[0]);
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
        transportInvocationHandler = (method, args, future, consumer, timeoutRunnables) -> {
            assertThat(method.getName(), equalTo("getName"));
            assertThat(args, arrayWithSize(0));
            future.complete("Bob");
        };

        final CompletionInvocationHandler<SampleInterface> handler =
                new CompletionInvocationHandler<>(timeoutScheduler, new EndpointName("Sample"), SampleInterface.class, transportInvocationHandler, 500L);
        final Object irrelevantProxy = null;

        // when
        final Object returnValue = handler.invoke(irrelevantProxy, getNameMethod, noArgs);

        // then
        final List<LoggingEvent> copiedEvents = getLoggingEvents();
        assertThat(copiedEvents, Matchers.hasSize(3));
        assertThat(copiedEvents.get(0), loggingEvent(Level.DEBUG, "Invoking [Sample] org.devzendo.zarjaz.reflect.TestCompletionInvocationHandler$SampleInterface.getName()"));
        assertThat(copiedEvents.get(1), loggingEvent(Level.DEBUG, "Waiting on Future"));
        assertThat(copiedEvents.get(2), loggingEvent(Level.DEBUG, "Wait over; removing timeout handler; returning value"));
        assertThat(returnValue, instanceOf(String.class));
        assertThat(returnValue, hasToString("Bob"));
    }

    // TODO test for methods that return a completablefuture

    @Test(timeout = 4000L)
    public void methodsCanTimeOut() throws NoSuchMethodException {
        // given
        transportInvocationHandler = (method, args, future, consumer, timeoutRunnables) -> {
            waitNoInterruption(2000L);
            future.complete("Bob");
        };
        final CompletionInvocationHandler<SampleInterface> handler =
                new CompletionInvocationHandler<>(timeoutScheduler, new EndpointName("Sample"), SampleInterface.class, transportInvocationHandler, 500L);
        final Object irrelevantProxy = null;

        // then
        thrown.expect(MethodInvocationTimeoutException.class);
        thrown.expectMessage("Method call [Sample] 'getName' timed out after 500ms");

        // when
        handler.invoke(irrelevantProxy, getNameMethod, noArgs);

        waitNoInterruption(3000L);
    }

    @Test(timeout = 4000L)
    public void canRunCustomHandlerOnTimeout() throws NoSuchMethodException {
        BasicConfigurator.configure();

        final boolean[] wasRun = new boolean[] { false };
        assertThat(wasRun[0], equalTo(false));

        logger.info("creating test objects");
        // given
        transportInvocationHandler = (method, args, future, consumer, timeoutHandlers) -> {
            logger.info("transport invocation handler, adding timeout handler");
            timeoutHandlers.setTimeoutTransportHandler((f, en, m) -> {
                logger.info("running timeout handler");
                wasRun[0] = true;
            });
            logger.info("transport invocation handler taking a long time");
            waitNoInterruption(2000L);
            logger.info("finished transport invocation handler");
        };
        final CompletionInvocationHandler<SampleInterface> completionInvocationHandler =
                new CompletionInvocationHandler<>(timeoutScheduler, new EndpointName("Sample"), SampleInterface.class, transportInvocationHandler, 500L);
        final Object irrelevantProxy = null;

        // when
        try {
            logger.info("invoking handler");
            completionInvocationHandler.invoke(irrelevantProxy, getNameMethod, noArgs);
            fail("A timeout exception should have been thrown");
        } catch (final Exception e) {
            logger.info("exception caught: " + e.getMessage());
            assertThat(e, instanceOf(MethodInvocationTimeoutException.class));
            assertThat(e.getMessage(), equalTo("Method call [Sample] 'getName' timed out after 500ms"));
        }

        logger.info("end of test wait");
        waitNoInterruption(200L);

        // then
        logger.info("end of test");
        assertThat(wasRun[0], equalTo(true));
    }

    @Test(timeout = 4000L)
    public void timeoutIsCancelledOnSuccessfulCompletion() throws NoSuchMethodException, InterruptedException {
        BasicConfigurator.configure();
        
        logger.info("creating test objects");
        final AtomicReference<MethodCallTimeoutHandlers> handlers = new AtomicReference<>();
        final CountDownLatch stored = new CountDownLatch(1);
        // given
        transportInvocationHandler = (method, args, future, consumer, timeoutHandlers) -> {
            logger.info("transport invocation handler, caching timeout handlers " + timeoutHandlers);
            handlers.set(timeoutHandlers);

            logger.info("storing result in future, simulating completion");
            future.complete("hello world");

            ThreadUtils.waitNoInterruption(250); // give completion invocation handler some time to unblock

            logger.info("letting test complete and check timeout handler");
            stored.countDown();
            logger.info("finished transport invocation handler");
        };
        final CompletionInvocationHandler<SampleInterface> completionInvocationHandler =
                new CompletionInvocationHandler<>(timeoutScheduler, new EndpointName("Sample"), SampleInterface.class, transportInvocationHandler, 500L);
        final Object irrelevantProxy = null;

        // when
        logger.info("invoking handler");
        completionInvocationHandler.invoke(irrelevantProxy, getNameMethod, noArgs);

        stored.await();
        // then the timeout occurred handler has been removed from the handlers
        assertThat(handlers.get().getTimeoutOccurredHandler().isPresent(), equalTo(false));

        logger.info("end of test wait");
        waitNoInterruption(250L);

        logger.info("end of test");
    }

    @Test(timeout = 4000L)
    public void explodingTimeoutTransportHandlerDoesNotPreventExecutionOfTimeoutOccurredHandler() throws NoSuchMethodException {
        final boolean[] wasRun = new boolean[]{false, false};

        // given
        transportInvocationHandler = (method, args, future, consumer, timeoutHandlers) -> {
            timeoutHandlers.setTimeoutTransportHandler((f, en, m) -> {
                wasRun[1] = true;
                throw new IllegalStateException("boom");
            });
            // need to chain the original timeout occurred handler
            final MethodCallTimeoutHandler timeoutOccurredHandler = timeoutHandlers.getTimeoutOccurredHandler().get();
            timeoutHandlers.setTimeoutOccurredHandler((f, en, m) -> {
                wasRun[0] = true;
                timeoutOccurredHandler.timeoutOccurred(f, en, m);
            });
            waitNoInterruption(2000L);
        };
        final CompletionInvocationHandler<SampleInterface> completionInvocationHandler =
                new CompletionInvocationHandler<>(timeoutScheduler, new EndpointName("Sample"), SampleInterface.class, transportInvocationHandler, 500L);
        final Object irrelevantProxy = null;

        // when
        try {
            completionInvocationHandler.invoke(irrelevantProxy, getNameMethod, noArgs);
            fail("A timeout exception should have been thrown");
        } catch (final Exception e) {
            assertThat(e, instanceOf(MethodInvocationTimeoutException.class));
            assertThat(e.getMessage(), equalTo("Method call [Sample] 'getName' timed out after 500ms"));
        }

        waitNoInterruption(200L);

        // then
        assertThat(wasRun[0], equalTo(true));
        assertThat(wasRun[1], equalTo(true));
        assertTrue(getLoggingEvents().stream().anyMatch(
                e -> e.getMessage().equals("Method call timeout handler threw exception: boom") && e.getLevel().equals(Level.WARN)
        ));
    }

    @Test(timeout = 4000L)
    public void orderOfTimeoutHandlers() throws NoSuchMethodException {
        final List<String> wasRun = new ArrayList<>();

        // given
        transportInvocationHandler = (method, args, future, consumer, timeoutHandlers) -> {
            timeoutHandlers.setTimeoutTransportHandler((f, en, m) -> wasRun.add("transport"));
            // need to chain the original timeout occurred handler
            final MethodCallTimeoutHandler timeoutOccurredHandler = timeoutHandlers.getTimeoutOccurredHandler().get();
            timeoutHandlers.setTimeoutOccurredHandler((f, en, m) -> {
                wasRun.add("occurred");
                timeoutOccurredHandler.timeoutOccurred(f, en, m);
            });
            waitNoInterruption(2000L);
        };
        final CompletionInvocationHandler<SampleInterface> completionInvocationHandler =
                new CompletionInvocationHandler<>(timeoutScheduler, new EndpointName("Sample"), SampleInterface.class, transportInvocationHandler, 500L);
        final Object irrelevantProxy = null;

        // when
        try {
            completionInvocationHandler.invoke(irrelevantProxy, getNameMethod, noArgs);
            fail("A timeout exception should have been thrown");
        } catch (final Exception e) {
            assertThat(e, instanceOf(MethodInvocationTimeoutException.class));
            assertThat(e.getMessage(), equalTo("Method call [Sample] 'getName' timed out after 500ms"));
        }

        waitNoInterruption(200L);

        // then
        assertThat(wasRun, hasSize(2));
        assertThat(wasRun.get(0), equalTo("transport"));
        assertThat(wasRun.get(1), equalTo("occurred"));
    }

    @Test(timeout = 4000L)
    public void transportHandlerExceptionIsStoredInTheFutureAsAFailure() throws NoSuchMethodException {

        // given
        transportInvocationHandler = (method, args, future, consumer, timeoutRunnables) -> {
            throw new IllegalStateException("boom");
        };
        final CompletionInvocationHandler<SampleInterface> completionInvocationHandler =
                new CompletionInvocationHandler<>(timeoutScheduler, new EndpointName("Sample"), SampleInterface.class, transportInvocationHandler, 500L);
        final Object irrelevantProxy = null;

        // then
        thrown.expect(InvocationException.class);
        thrown.expectMessage("boom");

        completionInvocationHandler.invoke(irrelevantProxy, getNameMethod, noArgs);
    }

    @Test(timeout = 4000L)
    public void transportHandlerExceptionRemovesTimeoutHandler() throws NoSuchMethodException {
        // given
        transportInvocationHandler = (method, args, future, consumer, timeoutRunnables) -> {
            throw new IllegalStateException("boom");
        };
        final CompletionInvocationHandler<SampleInterface> completionInvocationHandler =
                new CompletionInvocationHandler<>(timeoutScheduler, new EndpointName("Sample"), SampleInterface.class, transportInvocationHandler, 500L);
        final Object irrelevantProxy = null;

        // then
        try {
            completionInvocationHandler.invoke(irrelevantProxy, getNameMethod, noArgs);
            fail("Expected an exception");
        } catch (final InvocationException ie) {
            assertThat(ie.getMessage(), Matchers.containsString("boom"));
        } catch (final Exception e) {
            fail("Caught the wrong exception: " + e);
        }

        ThreadUtils.waitNoInterruption(1000); // if the timeout handler has not been removed, give it time to run

        // now sense its log output. not an ideal way of testing this, but it'll do
        assertTrue(getLoggingEvents().stream().noneMatch(
                e -> e.getMessage().toString().contains("method call [Sample] 'getName' timed out")));
    }
}
