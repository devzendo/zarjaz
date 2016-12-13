package org.devzendo.zarjaz.sample.primes;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

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
public class DefaultPrimeGenerator implements PrimeGenerator
{
    final int[] primes = { 2, 3, 5, 7, 9, 11, 13, 17, 19 }; // etc., etc...
    int primeIndex = 8;

    public synchronized String generateNextPrimeMessage(String userName) {
        if (primeIndex == 8) {
            primeIndex = 0;
        } else {
            primeIndex++;
        }
        return "Hello " + userName + ", the next prime is " + primes[primeIndex];
    }

    @Override
    public Future<String> generateNextPrimeMessageAsynchronously(String userName) {
        return CompletableFuture.completedFuture(generateNextPrimeMessage(userName));
    }
}
