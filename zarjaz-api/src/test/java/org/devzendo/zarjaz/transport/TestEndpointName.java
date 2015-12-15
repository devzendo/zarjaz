package org.devzendo.zarjaz.transport;

import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;

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
public class TestEndpointName {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void notNull() {
        thrown.expect(EndpointNameValidationException.class);
        thrown.expectMessage("EndpointNames cannot be null");
        new EndpointName(null);
    }

    @Test
    public void notEmpty() {
        thrown.expect(EndpointNameValidationException.class);
        thrown.expectMessage("EndpointNames cannot be empty");
        new EndpointName("");
    }

    @Test
    public void notStartingWithSpace() {
        thrown.expect(EndpointNameValidationException.class);
        thrown.expectMessage("EndpointName ' funk' is not allowed");
        new EndpointName(" funk");
    }

    @Test
    public void notEndingWithSpace() {
        thrown.expect(EndpointNameValidationException.class);
        thrown.expectMessage("EndpointName 'funk ' is not allowed");
        new EndpointName("funk ");
    }

    @Test
    public void typicalValidName() {
        new EndpointName("update facade v1");
    }

    @Test
    public void singleWordCharOk() {
        new EndpointName("z");
    }

    @Test
    public void twoWordCharsOk() {
        new EndpointName("za");
    }

    @Test
    public void spacesInsideWordCharsOk() {
        new EndpointName("z  a");
    }

    @Test
    public void canGetNameOut() {
        assertThat(new EndpointName("fred").getValue(), equalTo("fred"));
    }

    @Test
    public void equalNames() {
        final EndpointName v1 = new EndpointName("foo");
        final EndpointName v2 = new EndpointName("foo");
        assertThat(v1, Matchers.equalTo(v2));
    }

    @Test
    public void unequalNames() {
        final EndpointName v1 = new EndpointName("foo");
        final EndpointName v2 = new EndpointName("bar");
        assertThat(v1, not(Matchers.equalTo(v2)));
    }
}
