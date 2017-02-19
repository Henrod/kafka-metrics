/*
 * Copyright 2015 Michal Harish, michal.harish@gmail.com
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.amient.kafka.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MalformedObjectNameException;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.amient.kafka.metrics.JMXScannerTask.JMXScannerConfig;

public class JMXScanner {

    static private final Logger log = LoggerFactory.getLogger(JMXScanner.class);

    final private ScheduledExecutorService jmxScanExecutor;

    public JMXScanner(Properties props, MeasurementPublisher publisher)
            throws IOException, MalformedObjectNameException, InterruptedException {
        if (publisher == null) log.warn("ProducerPublisher not configured, will only scan..");
        Map<String, JMXScannerConfig> jmxConfigs = new HashMap<String, JMXScannerConfig>();
        for (Enumeration<Object> e = props.keys(); e.hasMoreElements(); ) {
            String propKey = (String) e.nextElement();
            String propVal = props.get(propKey).toString();
            if (propKey.startsWith("jmx.")) {
                propKey = propKey.substring(4);
                int idLen = propKey.indexOf('.') + 1;
                String id = propKey.substring(0, idLen - 1);
                if (!jmxConfigs.containsKey(id)) jmxConfigs.put(id, new JMXScannerConfig());
                JMXScannerConfig jmxConfig = jmxConfigs.get(id);
                propKey = propKey.substring(idLen);
                log.info(propKey + "=" + propVal);
                if (propKey.startsWith("tag.")) {
                    propKey = propKey.substring(4);
                    jmxConfig.setTag(propKey, propVal);
                } else if (propKey.equals("address")) {
                    jmxConfig.setAddress(propVal);
                } else if (propKey.equals("query.scope")) {
                    jmxConfig.setQueryScope(propVal);
                } else if (propKey.equals("query.interval.s")) {
                    jmxConfig.setQueryInterval(Long.parseLong(propVal));
                }
            }
        }

        jmxScanExecutor = Executors.newScheduledThreadPool(jmxConfigs.size());
        for (JMXScannerConfig jmxConfig : jmxConfigs.values()) {
            log.info("Starting JMXScannerTask for " + jmxConfig.getAddress()
                    + " every " + jmxConfig.getQueryIntervalSeconds() + " seconds");
            JMXScannerTask jmxScanner = new JMXScannerTask(jmxConfig, publisher);
            jmxScanExecutor.scheduleAtFixedRate(jmxScanner, 0, jmxConfig.getQueryIntervalSeconds(), TimeUnit.SECONDS);
        }
    }

    public Boolean isTerminated() {
        return jmxScanExecutor.isTerminated();
    }


}
