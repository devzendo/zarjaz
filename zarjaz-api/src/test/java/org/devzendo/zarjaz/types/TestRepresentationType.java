package org.devzendo.zarjaz.types;

import org.hamcrest.Matchers;
import org.junit.Test;


import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
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
public class TestRepresentationType {

    public class SampleRepresentationZero extends RepresentationType<String> {
        public SampleRepresentationZero(String value) {
            super(value);
        }
    }

    public class SampleRepresentationOne extends RepresentationType<String> {
        public SampleRepresentationOne(String value) {
            super(value);
        }
    }

    public class SampleRepresentationTwo extends RepresentationType<Integer> {
        public SampleRepresentationTwo(Integer value) {
            super(value);
        }
    }

    @Test
    public void equalityHappyPath() {
        final SampleRepresentationOne s1 = new SampleRepresentationOne("str1");
        final SampleRepresentationOne s2 = new SampleRepresentationOne("str1");
        assertThat(s1.equals(s2), Matchers.equalTo(true));
        assertThat(s2.equals(s1), Matchers.equalTo(true));
    }

    @Test
    public void equalRepresentationTypesUnequalValueTypes() {
        final SampleRepresentationOne s1 = new SampleRepresentationOne("str1");
        final SampleRepresentationOne s2 = new SampleRepresentationOne("waaaah");
        assertThat(s1, not(equalTo(s2)));
        assertThat(s2, not(equalTo(s1)));
    }

    @Test
    public void unequalRepresentationTypesEqualValueTypes() {
        final SampleRepresentationZero s1 = new SampleRepresentationZero("str1");
        final SampleRepresentationOne s2 = new SampleRepresentationOne("str1");
        assertThat(s1.equals(s2), Matchers.equalTo(false));
        assertThat(s2.equals(s1), Matchers.equalTo(false));
    }

    @Test
    public void unequalRepresentationTypesUnequalValueTypes() {
        final SampleRepresentationZero s1 = new SampleRepresentationZero("str1");
        final SampleRepresentationTwo s2 = new SampleRepresentationTwo(Integer.valueOf(34));
        assertThat(s1.equals(s2), Matchers.equalTo(false));
        assertThat(s2.equals(s1), Matchers.equalTo(false));
    }

}
