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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.casbin.config.WatcherConfig;
import org.casbin.jcasbin.model.Model;
import org.casbin.jcasbin.persist.WatcherEx;
import org.casbin.jcasbin.util.Util;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Postgres implementation of jCasbin Watcher interface, supporting WatcherEx
 */
public class JCasbinPostgresWatcher implements WatcherEx, AutoCloseable {
    private final PostgresWatcher pgWatcher;
    private Runnable runnableCallback;
    private Consumer<String> consumerCallback;
    private final String channel;
    private final String localId;
    private final boolean verbose;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public JCasbinPostgresWatcher(String url, String user, String password, String channel) throws SQLException {
        this(url, user, password, createDefaultConfig(channel));
    }

    public JCasbinPostgresWatcher(String url, String user, String password, WatcherConfig config) throws SQLException {
        this.pgWatcher = new PostgresWatcher(url, user, password);
        this.pgWatcher.connect();
        this.channel = config.getChannel();
        this.localId = config.getLocalId();
        this.verbose = config.isVerbose();
        this.pgWatcher.listen(channel, this::onMessageReceived);
    }

    private void onMessageReceived(String rawMessage) {
        // Avoid processing notifications sent by self
        String[] parts = rawMessage.split("::", 2);
        if (parts.length == 2 && parts[0].equals(localId)) {
            if (verbose) {
                Util.logPrintfInfo("[jcasbin-postgres-watcher] Ignoring message from own instance ({})", localId);
            }
            return;
        }
        final String message = parts.length == 2 ? parts[1] : rawMessage;

        if (consumerCallback != null) {
            consumerCallback.accept(message);
        }
        if (runnableCallback != null) {
            runnableCallback.run();
        }
    }

    private static WatcherConfig createDefaultConfig(String channel) {
        WatcherConfig config = new WatcherConfig();
        config.setChannel(channel);
        return config;
    }

    @Override
    public void setUpdateCallback(Runnable runnable) {
        this.runnableCallback = runnable;
    }

    @Override
    public void setUpdateCallback(Consumer<String> func) {
        this.consumerCallback = func;
    }

    @Override
    public void update() {
        sendMessage("update", new HashMap<>());
    }

    @Override
    public void updateForAddPolicy(String sec, String ptype, String... params) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("sec", sec);
        msg.put("ptype", ptype);
        msg.put("params", params);
        sendMessage("updateForAddPolicy", msg);
    }

    @Override
    public void updateForRemovePolicy(String sec, String ptype, String... params) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("sec", sec);
        msg.put("ptype", ptype);
        msg.put("params", params);
        sendMessage("updateForRemovePolicy", msg);
    }

    @Override
    public void updateForRemoveFilteredPolicy(String sec, String ptype, int fieldIndex, String... fieldValues) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("sec", sec);
        msg.put("ptype", ptype);
        msg.put("fieldIndex", fieldIndex);
        msg.put("fieldValues", fieldValues);
        sendMessage("updateForRemoveFilteredPolicy", msg);
    }

    @Override
    public void updateForSavePolicy(Model model) {
        // Model is not easily serializable by default. For this generic watcher,
        // we'll send a simple notification. A more advanced implementation might
        // serialize the model to a specific format if needed.
        sendMessage("updateForSavePolicy", new HashMap<>());
    }

    @Override
    public void updateForAddPolicies(String sec, String ptype, List<List<String>> rules) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("sec", sec);
        msg.put("ptype", ptype);
        msg.put("rules", rules);
        sendMessage("updateForAddPolicies", msg);
    }

    @Override
    public void updateForRemovePolicies(String sec, String ptype, List<List<String>> rules) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("sec", sec);
        msg.put("ptype", ptype);
        msg.put("rules", rules);
        sendMessage("updateForRemovePolicies", msg);
    }

    private void sendMessage(String method, Map<String, Object> params) {
        Map<String, Object> messageMap = new HashMap<>();
        messageMap.put("method", method);
        messageMap.put("params", params);

        try {
            String jsonMessage = objectMapper.writeValueAsString(messageMap);
            String fullMessage = localId + "::" + jsonMessage;
            pgWatcher.notify(channel, fullMessage);
        } catch (JsonProcessingException e) {
            Util.logPrintfError("Failed to serialize message to JSON: " + e.getMessage());
        } catch (SQLException e) {
            Util.logPrintfError("Failed to send notification: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        pgWatcher.close();
    }
}
