package org.devzendo.zarjaz.transport;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

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
public class TestNamedInterface {
    public interface Foo {

    }

    public interface Bar {

    }

    @Test
    public void equalInterfaces() {
        final NamedInterface v1 = new NamedInterface(new EndpointName("foo"), Foo.class);
        final NamedInterface v2 = new NamedInterface(new EndpointName("foo"), Foo.class);
        assertThat(v1.equals(v2), equalTo(true));
        assertThat(v2.equals(v1), equalTo(true));
    }

    @Test
    public void unequalInterfacesByName() {
        final NamedInterface v1 = new NamedInterface(new EndpointName("foo"), Foo.class);
        final NamedInterface v2 = new NamedInterface(new EndpointName("bar"), Foo.class);
        assertThat(v1.equals(v2), equalTo(false));
        assertThat(v2.equals(v1), equalTo(false));
    }

    @Test
    public void unequalInterfacesByInterface() {
        final NamedInterface v1 = new NamedInterface(new EndpointName("xxx"), Foo.class);
        final NamedInterface v2 = new NamedInterface(new EndpointName("xxx"), Bar.class);
        assertThat(v1.equals(v2), equalTo(false));
        assertThat(v2.equals(v1), equalTo(false));
    }

    @Test
    public void unequalInterfacesByNameAndInterface() {
        final NamedInterface v1 = new NamedInterface(new EndpointName("foo"), Foo.class);
        final NamedInterface v2 = new NamedInterface(new EndpointName("bar"), Bar.class);
        assertThat(v1.equals(v2), equalTo(false));
        assertThat(v2.equals(v1), equalTo(false));
    }

}
