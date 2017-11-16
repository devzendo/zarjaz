package org.devzendo.zarjaz.transceiver;

import org.devzendo.commoncode.logging.ConsoleLoggingUnittestCase;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.List;

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
public class TestNetworkInterfaceMonitor extends ConsoleLoggingUnittestCase {
    private static final Logger logger = LoggerFactory.getLogger(TestNetworkInterfaceMonitor.class);

    @Test
    public void enumerate() throws SocketException {
        final Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
        while (networkInterfaces.hasMoreElements()) {
            final NetworkInterface networkInterface = networkInterfaces.nextElement();
            logger.info("network interface " + networkInterface + " loopback? " + networkInterface.isLoopback() +
                    " virtual? " + networkInterface.isVirtual() + " point-to-point? " + networkInterface.isPointToPoint() +
                    " up? " + networkInterface.isUp());
            final List<InterfaceAddress> interfaceAddresses = networkInterface.getInterfaceAddresses();
            for (InterfaceAddress interfaceAddress : interfaceAddresses) {
                final InetAddress broadcast = interfaceAddress.getBroadcast();
                logger.info("  interface " + interfaceAddress + " network prefix length " + interfaceAddress.getNetworkPrefixLength() + " broadcast " + broadcast);
            }
        }
    }

}
