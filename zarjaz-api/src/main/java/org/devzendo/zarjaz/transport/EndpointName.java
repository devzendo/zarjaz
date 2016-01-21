package org.devzendo.zarjaz.transport;

import org.devzendo.commoncode.types.RepresentationType;

import java.util.regex.Pattern;

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
public class EndpointName extends RepresentationType<String> {

    private Pattern validNamePattern = Pattern.compile("^\\w([\\w\\s]*\\w)?$");

    public EndpointName(final String name) {
        super(name);
        validate(name);
    }

    private void validate(final String name) {
        if (name == null) {
            throw new EndpointNameValidationException("EndpointNames cannot be null");
        }
        if (name.length() == 0) {
            throw new EndpointNameValidationException("EndpointNames cannot be empty");
        }
        if (!validNamePattern.matcher(name).matches()) {
            throw new EndpointNameValidationException("EndpointName '" + name + "' is not allowed");
        }
    }
}
