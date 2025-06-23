// Copyright 2025 The casbin Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.casbin.watcher;

import org.junit.jupiter.api.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.List;

public class JCasbinPostgresWatcherTest {
    private static final String URL = "jdbc:postgresql://localhost:5432/testdb";
    private static final String USER = "postgres";
    private static final String PASSWORD = "postgres";
    private static final String CHANNEL = "test_channel";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testWatcherCallback() throws Exception {
        try (JCasbinPostgresWatcher watcher1 = new JCasbinPostgresWatcher(URL, USER, PASSWORD, CHANNEL);
             JCasbinPostgresWatcher watcher2 = new JCasbinPostgresWatcher(URL, USER, PASSWORD, CHANNEL)) {
            AtomicBoolean called = new AtomicBoolean(false);
            watcher2.setUpdateCallback(() -> called.set(true));
            // watcher1 triggers update
            watcher1.update();
            Thread.sleep(1000);
            Assertions.assertTrue(called.get(), "watcher2 should receive notification from watcher1 and trigger callback");
        }
    }

    @Test
    public void testWatcherCallbackRunnable() throws Exception {
        try (JCasbinPostgresWatcher watcher1 = new JCasbinPostgresWatcher(URL, USER, PASSWORD, CHANNEL);
             JCasbinPostgresWatcher watcher2 = new JCasbinPostgresWatcher(URL, USER, PASSWORD, CHANNEL)) {
            AtomicBoolean called = new AtomicBoolean(false);
            watcher2.setUpdateCallback((Runnable) () -> called.set(true));
            // watcher1 triggers update
            watcher1.update();
            Thread.sleep(1000);
            Assertions.assertTrue(called.get(), "Policy change should trigger callback (Runnable)");
        }
    }

    @Test
    public void testWatcherCallbackConsumer() throws Exception {
        try (JCasbinPostgresWatcher watcher1 = new JCasbinPostgresWatcher(URL, USER, PASSWORD, CHANNEL);
             JCasbinPostgresWatcher watcher2 = new JCasbinPostgresWatcher(URL, USER, PASSWORD, CHANNEL)) {
            AtomicBoolean called = new AtomicBoolean(false);
            watcher2.setUpdateCallback((Consumer<String>) msg -> {
                try {
                    Map<String, Object> messageMap = objectMapper.readValue(msg, Map.class);
                    Assertions.assertEquals("update", messageMap.get("method"));
                    called.set(true);
                } catch (Exception e) {
                    Assertions.fail("JSON parsing failed", e);
                }
            });
            // watcher1 triggers update
            watcher1.update();
            Thread.sleep(1000);
            Assertions.assertTrue(called.get(), "Policy change should trigger callback (Consumer<String>)");
        }
    }

    @Test
    public void testWatcherEx() throws Exception {
        try (JCasbinPostgresWatcher watcher1 = new JCasbinPostgresWatcher(URL, USER, PASSWORD, CHANNEL);
             JCasbinPostgresWatcher watcher2 = new JCasbinPostgresWatcher(URL, USER, PASSWORD, CHANNEL)) {

            final AtomicBoolean called = new AtomicBoolean(false);
            final String[] receivedParams = new String[3];

            watcher2.setUpdateCallback((Consumer<String>) msg -> {
                try {
                    Map<String, Object> messageMap = objectMapper.readValue(msg, Map.class);
                    String method = (String) messageMap.get("method");
                    Assertions.assertEquals("updateForAddPolicy", method);

                    Map<String, Object> params = (Map<String, Object>) messageMap.get("params");
                    Assertions.assertEquals("p", params.get("sec"));
                    Assertions.assertEquals("p", params.get("ptype"));

                    List<String> policy = (List<String>) params.get("params");
                    receivedParams[0] = policy.get(0);
                    receivedParams[1] = policy.get(1);
                    receivedParams[2] = policy.get(2);

                    called.set(true);
                } catch (Exception e) {
                    e.printStackTrace();
                    Assertions.fail("JSON parsing failed");
                }
            });

            Thread.sleep(200); // Wait for listener to be ready

            watcher1.updateForAddPolicy("p", "p", "alice", "data1", "read");

            Thread.sleep(1000);

            Assertions.assertTrue(called.get(), "WatcherEx callback should be called");
            Assertions.assertArrayEquals(new String[]{"alice", "data1", "read"}, receivedParams);
        }
    }
}
