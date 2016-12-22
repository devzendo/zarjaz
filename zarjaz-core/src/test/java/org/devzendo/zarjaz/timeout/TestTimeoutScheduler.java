package org.devzendo.zarjaz.timeout;

import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;
import org.devzendo.commoncode.concurrency.ThreadUtils;
import org.devzendo.zarjaz.logging.LoggingUnittestCase;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import static java.lang.System.currentTimeMillis;
import static java.lang.Thread.currentThread;
import static org.devzendo.commoncode.concurrency.ThreadUtils.waitNoInterruption;
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
public class TestTimeoutScheduler extends LoggingUnittestCase {
    private static final Logger logger = LoggerFactory.getLogger(TestTimeoutScheduler.class);

    private final TimeoutScheduler ts = new TimeoutScheduler();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @After
    public void stopScheduler() {
        if (ts.isStarted()) {
            ts.stop();
        }
    }

    @Test(timeout = 2000)
    public void timeoutCanBeScheduled() {
        ts.start();

        long[] when = new long[] { 0 };

        long start = currentTimeMillis();
        ts.schedule(500, () -> when[0] = currentTimeMillis());

        waitNoInterruption(750L);

        assertThat(when[0], not(equalTo(0L)));
        long delay = when[0] - start;
        assertThat(delay, greaterThanOrEqualTo(500L)); // timeout happened no sooner than it was scheduled
        assertThat(delay, Matchers.lessThan(750L)); // but happened no greater than 250ms after it should have
    }

    @Test(timeout = 2000)
    public void timeoutIsTriggeredOnSchedulerThreadAndThatsALivingDaemon() {
        ts.start();

        Thread[] thread = new Thread[] { null };

        ts.schedule(500, () -> thread[0] = currentThread());

        waitNoInterruption(750L);

        assertThat(thread[0], not(equalTo(currentThread())));
        assertThat(thread[0].isDaemon(), equalTo(true));
        assertThat(thread[0].isAlive(), equalTo(true));
    }

    @Test(timeout = 2000)
    public void exceptionsInTimeoutsAreLoggedAndDontCauseTheSchedulerToTerminate() {
        ts.start();

        boolean[] subsequentHandlersRun = new boolean[] { false };

        ts.schedule(250, () -> {
            throw new IllegalStateException("Boom");
        });

        ts.schedule(300, () -> subsequentHandlersRun[0] = true);

        waitNoInterruption(1000L);

        // Copy to arraylist to prevent concurrent modification exceptions
        assertThat(new ArrayList<>(capturingAppender.getEvents()), hasSize(1));
        // TODO use the IsLoggingEvent matcher in the zarjaz.logging package here...?
        final LoggingEvent loggingEvent = capturingAppender.getEvents().get(0);
        assertThat(loggingEvent.getLevel(), equalTo(Level.WARN));
        assertThat(loggingEvent.getMessage().toString(), Matchers.containsString("Timeout handler threw IllegalStateException: Boom"));

        assertThat(subsequentHandlersRun[0], equalTo(true));
    }

    @Test(timeout = 2000)
    public void longDelayInTimeoutHandlerDoesNotStarveHandlingOfOtherRequests() {
        ts.start();

        long[] when = new long[] { 0 };

        long start = currentTimeMillis();
        // Handler A waits for a long while....
        ts.schedule(100, () -> ThreadUtils.waitNoInterruption(500L));
        // But shortly after A starts, handler B should be triggered. A shouldn't hold up B's triggering.
        ts.schedule(150, () -> when[0] = currentTimeMillis());

        waitNoInterruption(1000L);

        assertThat(when[0], not(equalTo(0L)));
        long delay = when[0] - start;
        assertThat(delay, greaterThanOrEqualTo(150L)); // timeout happened no sooner than it was scheduled
        assertThat(delay, Matchers.lessThan(300L)); // but happened no greater than 250ms after it should have
        // and certainly, not delayed by 500ms - which would be the case if handlers were called sequentially on a
        // single thread.
    }

    @Test(timeout = 2000)
    public void timeoutCanBeCancelled() {
        ts.start();

        long[] when = new long[] { 0 };

        final TimeoutId timeoutId = ts.schedule(500, () -> when[0] = currentTimeMillis());

        waitNoInterruption(100L);

        final boolean wasCancelled = ts.cancel(timeoutId);

        waitNoInterruption(750L);

        assertThat(when[0], equalTo(0L)); // was not triggered
        assertThat(wasCancelled, equalTo(true));
    }

    @Test(timeout = 2000)
    public void timeoutCanBeCancelledTwice() {
        ts.start();

        long[] when = new long[] { 0 };

        final TimeoutId timeoutId = ts.schedule(500, () -> when[0] = currentTimeMillis());

        waitNoInterruption(100L);

        final boolean wasCancelledFirstTime = ts.cancel(timeoutId);
        final boolean wasCancelledSecondTime = ts.cancel(timeoutId);

        waitNoInterruption(750L);

        assertThat(when[0], equalTo(0L)); // was not triggered
        assertThat(wasCancelledFirstTime, equalTo(true));
        assertThat(wasCancelledSecondTime, equalTo(false)); // we don't store history of what was cancelled
    }

    @Test(timeout = 2000)
    public void timeoutCannotBeCancelledAfterScheduled() {
        ts.start();

        long[] when = new long[] { 0 };

        final TimeoutId timeoutId = ts.schedule(500, () -> when[0] = currentTimeMillis());

        waitNoInterruption(750L); // triggered during this delay

        final boolean wasCancelled = ts.cancel(timeoutId);

        assertThat(when[0], not(equalTo(0L))); // was triggered
        assertThat(wasCancelled, equalTo(false)); // the time has passed. cancellation is futile.
    }

    @Test(timeout = 2000)
    public void nonExistantTimeoutCannotBeCancelled() {
        ts.start();

        final TimeoutId nexistpas = new TimeoutId(17L);
        assertThat(ts.cancel(nexistpas), equalTo(false));
    }

    @Test
    public void cannotScheduleIfNotStarted() {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Cannot schedule when scheduler is stopped");

        ts.schedule(500, () -> {});
    }

    @Test
    public void cannotScheduleIfStopped() {
        ts.start();
        waitNoInterruption(100);
        ts.stop();
        waitNoInterruption(100);

        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Cannot schedule when scheduler is stopped");

        ts.schedule(500, () -> {});
    }

    @Test
    public void cannotCancelIfNotStarted() {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Cannot cancel when scheduler is stopped");

        final TimeoutId irrelevant = new TimeoutId(17L);
        ts.cancel(irrelevant);
    }

    @Test
    public void cannotCancelIfStopped() {
        ts.start();
        waitNoInterruption(100);
        ts.stop();
        waitNoInterruption(100);

        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Cannot cancel when scheduler is stopped");

        final TimeoutId irrelevant = new TimeoutId(17L);
        ts.cancel(irrelevant);
    }

    @Test
    public void cannotStopIfNotStarted() {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Cannot stop scheduler if it has not been started");

        ts.stop();
        waitNoInterruption(100);
    }

    @Test(timeout = 2000)
    public void scheduledTaskIsNotRunAfterStop() {
        ts.start();

        long[] when = new long[] { 0 };

        long start = currentTimeMillis();
        ts.schedule(500, () -> when[0] = currentTimeMillis());

        ts.stop();

        waitNoInterruption(750L);

        assertThat(when[0], equalTo(0L)); // did not trigger
    }
}
