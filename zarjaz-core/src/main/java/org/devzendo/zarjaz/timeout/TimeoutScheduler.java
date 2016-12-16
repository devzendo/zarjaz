package org.devzendo.zarjaz.timeout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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
public class TimeoutScheduler {
    private static final Logger logger = LoggerFactory.getLogger(TimeoutScheduler.class);

    static class DaemonThreadFactory implements ThreadFactory {
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        DaemonThreadFactory() {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() :
                    Thread.currentThread().getThreadGroup();
            namePrefix = "zarjaz-timeout-scheduler-thread-";
        }

        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r,
                    namePrefix + threadNumber.getAndIncrement(),
                    0);
            if (!t.isDaemon())
                t.setDaemon(true);
            if (t.getPriority() != Thread.NORM_PRIORITY)
                t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final Map<TimeoutId, ScheduledFuture<?>> activeTimeouts = new ConcurrentHashMap<>();
    private final AtomicLong timeoutIdCount = new AtomicLong(0);
    private final ScheduledThreadPoolExecutor executor;

    public TimeoutScheduler() {
        executor = new ScheduledThreadPoolExecutor(10, new DaemonThreadFactory());
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    }

    public TimeoutId schedule(final long millisecondsFromNow, final Runnable runnable) {
        if (!started.get()) {
            throw new IllegalStateException("Cannot schedule when scheduler is stopped");
        }

        final long thisTimeoutId = timeoutIdCount.incrementAndGet();
        final TimeoutId timeoutId = new TimeoutId(thisTimeoutId);
        final Runnable exceptionLoggingRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    runnable.run();
                } catch (final Exception e) {
                    logger.warn("Timeout handler threw " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
                }
            }
        };
        final ScheduledFuture<?> schedule = executor.schedule(exceptionLoggingRunnable, millisecondsFromNow, TimeUnit.MILLISECONDS);
        activeTimeouts.put(timeoutId, schedule);
        return timeoutId;
    }

    public boolean cancel(final TimeoutId timeoutId) {
        if (!started.get()) {
            throw new IllegalStateException("Cannot cancel when scheduler is stopped");
        }

        final ScheduledFuture<?> schedule = activeTimeouts.get(timeoutId);
        if (schedule == null) {
            return false;
        }
        activeTimeouts.remove(timeoutId);
        return schedule.cancel(false); // not sure whether to interrupt or not.
    }

    public void start() {
        started.set(true);
    }

    public boolean isStarted() {
        return started.get();
    }

    public void stop() {
        if (!started.get()) {
            throw new IllegalStateException("Cannot stop scheduler if it has not been started");
        }

        started.set(false);
        executor.shutdown();
    }
}
