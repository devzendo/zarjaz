package org.devzendo.zarjaz.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

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
public class TestClassUtils {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void emptyObjectsToClasses() {
        assertThat(ClassUtils.objectsToClasses(new Object[0]).length, equalTo(0));
    }

    @Test
    public void nullObjectsToClasses() {
        assertThat(ClassUtils.objectsToClasses(null), nullValue());
    }

    @Test
    public void objectsToClasses() {
        final Class[] classes = ClassUtils.objectsToClasses(new Object[]{"hello", Integer.valueOf(3), Boolean.TRUE, Character.valueOf('a')});
        assertThat(classes, arrayContaining(String.class, Integer.class, Boolean.class, Character.class));
    }

    private interface Primitives {
        void method(boolean bl, byte by, char c, short s, int i, long l, double d, float f);
    }

    @Test
    public void primitiveObjectsToClasses() {
        // Can't create a set of primitive types, and pass them in to ClassUtils.objectsToClasses(new Object[]{bool, b, c, s, i, l, d, f});
        // as that autoboxes them. So, have to reflect on a classes' method (which is what the callers of this will be
        // doing).
        final Class[] classes = Primitives.class.getDeclaredMethods()[0].getParameterTypes();
        assertThat(classes, arrayContaining(Boolean.TYPE, Byte.TYPE, Character.TYPE, Short.TYPE, Integer.TYPE, Long.TYPE, Double.TYPE, Float.TYPE));
    }

    @Test
    public void primitiveTypeOf() {
        assertThat(ClassUtils.primitiveTypeOf(Boolean.class), equalTo(Boolean.TYPE));
        assertThat(ClassUtils.primitiveTypeOf(Byte.class), equalTo(Byte.TYPE));
        assertThat(ClassUtils.primitiveTypeOf(Character.class), equalTo(Character.TYPE));
        assertThat(ClassUtils.primitiveTypeOf(Short.class), equalTo(Short.TYPE));
        assertThat(ClassUtils.primitiveTypeOf(Integer.class), equalTo(Integer.TYPE));
        assertThat(ClassUtils.primitiveTypeOf(Long.class), equalTo(Long.TYPE));
        assertThat(ClassUtils.primitiveTypeOf(Double.class), equalTo(Double.TYPE));
        assertThat(ClassUtils.primitiveTypeOf(Float.class), equalTo(Float.TYPE));
        assertThat(ClassUtils.primitiveTypeOf(Void.class), equalTo(Void.TYPE));
    }

    @Test
    public void primitiveTypeOfNull() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Cannot convert null to a primitive type");
        ClassUtils.primitiveTypeOf(null);
    }

    @Test
    public void primitiveTypeOfNonPrimitive() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Cannot convert String to a primitive type");
        ClassUtils.primitiveTypeOf(String.class);
    }

    @Test
    public void emptyClassesToClassNames() {
        assertThat(ClassUtils.classesToClassNames(new Class[0]).length, equalTo(0));
    }

    @Test
    public void nullClassesToClassNames() {
        assertThat(ClassUtils.classesToClassNames(null), nullValue());
    }

    @Test
    public void classesToClassNames() {
        final String[] names = ClassUtils.classesToClassNames(new Class[]{String.class, Integer.class, Boolean.class, Character.class});
        assertThat(names, arrayContaining(new String[]{"String", "Integer", "Boolean", "Character"}));
    }

    @Test
    public void joinedClassesToClassNamesNull() {
        final String joined = ClassUtils.joinedClassNames(null);
        assertThat(joined, equalTo("()"));
    }

    @Test
    public void joinedClassesToClassNamesEmpty() {
        final String joined = ClassUtils.joinedClassNames(new Class[0]);
        assertThat(joined, equalTo("()"));
    }

    @Test
    public void joinedClassesToClassNamesOne() {
        final String joined = ClassUtils.joinedClassNames(new Class[]{String.class});
        assertThat(joined, equalTo("(String)"));
    }

    @Test
    public void joinedClassesToClassNamesTwo() {
        final String joined = ClassUtils.joinedClassNames(new Class[]{String.class, Integer.class});
        assertThat(joined, equalTo("(String, Integer)"));
    }

    @Test
    public void joinedClassesToClassNamesThree() {
        final String joined = ClassUtils.joinedClassNames(new Class[]{String.class, Integer.class, Void.class});
        assertThat(joined, equalTo("(String, Integer, Void)"));
    }

}
