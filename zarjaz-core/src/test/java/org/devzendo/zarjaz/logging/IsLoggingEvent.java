package org.devzendo.zarjaz.logging;

import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;
import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

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
public class IsLoggingEvent extends TypeSafeMatcher<LoggingEvent> {
    private final Level level;
    private final String message;

    public IsLoggingEvent(final Level level, final String message) {
        this.level = level;
        this.message = message;
    }

    @Override
    protected boolean matchesSafely(final LoggingEvent item) {
        return item.getLevel().equals(level) && item.getMessage().equals(message);
    }

    @Override
    public void describeMismatchSafely(final LoggingEvent item, Description mismatchDescription) {
        mismatchDescription.appendText("has level ")
                .appendValue(item.getLevel())
                .appendText(" and message '")
                .appendValue(item.getMessage())
                .appendText("'");
    }

    @Override
    public void describeTo(final Description description) {
        description.appendText("loggingEvent(")
                .appendValue(level)
                .appendText(", '")
                .appendValue(message)
                .appendText("')");
    }

    @Factory
    public static Matcher<LoggingEvent> loggingEvent(final Level level, final String message) {
        return new IsLoggingEvent(level, message);
    }
}
