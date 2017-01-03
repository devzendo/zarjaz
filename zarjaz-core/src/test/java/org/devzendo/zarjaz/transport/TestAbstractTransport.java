package org.devzendo.zarjaz.transport;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Copyright (C) 2008-2015 Matt Gumbley, DevZendo.org http://devzendo.org
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
public class TestAbstractTransport {
    @Test
    public void emptyObjectsToClasses() {
        assertThat(AbstractTransport.objectsToClasses(new Object[0]).length, equalTo(0));
    }

    @Test
    public void nullObjectsToClasses() {
        assertThat(AbstractTransport.objectsToClasses(null), nullValue());
    }

    @Test
    public void objectsToClasses() {
        final Class[] classes = AbstractTransport.objectsToClasses(new Object[] {"hello", Integer.valueOf(3), Boolean.TRUE, Character.valueOf('a')});
        assertThat(classes, arrayContaining(String.class, Integer.class, Boolean.class, Character.class));
    }

    @Test
    public void emptyClassesToClassNames() {
        assertThat(AbstractTransport.classesToClassNames(new Class[0]).length, equalTo(0));
    }

    @Test
    public void nullClassesToClassNames() {
        assertThat(AbstractTransport.classesToClassNames(null), nullValue());
    }

    @Test
    public void classesToClassNames() {
        final String[] names = AbstractTransport.classesToClassNames(new Class[] { String.class, Integer.class, Boolean.class, Character.class });
        assertThat(names, arrayContaining(new String[] { "String", "Integer", "Boolean", "Character" }));
    }

    @Test
    public void joinedClassesToClassNamesNull() {
        final String joined = AbstractTransport.joinedClassNames(null);
        assertThat(joined, equalTo("()"));
    }

    @Test
    public void joinedClassesToClassNamesEmpty() {
        final String joined = AbstractTransport.joinedClassNames(new Class[0]);
        assertThat(joined, equalTo("()"));
    }

    @Test
    public void joinedClassesToClassNamesOne() {
        final String joined = AbstractTransport.joinedClassNames(new Class[] {String.class});
        assertThat(joined, equalTo("(String)"));
    }

    @Test
    public void joinedClassesToClassNamesTwo() {
        final String joined = AbstractTransport.joinedClassNames(new Class[] {String.class, Integer.class});
        assertThat(joined, equalTo("(String, Integer)"));
    }

    @Test
    public void joinedClassesToClassNamesThree() {
        final String joined = AbstractTransport.joinedClassNames(new Class[] {String.class, Integer.class, Void.class});
        assertThat(joined, equalTo("(String, Integer, Void)"));
    }
}
