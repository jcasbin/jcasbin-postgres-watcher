# jcasbin-postgres-watcher

[![GitHub Actions](https://github.com/jcasbin/jcasbin-postgres-watcher/actions/workflows/ci.yml/badge.svg)](https://github.com/jcasbin/jcasbin-postgres-watcher/actions/workflows/ci.yml)
![License](https://img.shields.io/github/license/jcasbin/jcasbin-postgres-watcher)
[![Javadoc](https://javadoc.io/badge2/org.casbin/jcasbin-postgres-watcher/javadoc.svg)](https://javadoc.io/doc/org.casbin/jcasbin-postgres-watcher)
[![Maven Central](https://img.shields.io/maven-central/v/org.casbin/jcasbin-postgres-watcher.svg)](https://mvnrepository.com/artifact/org.casbin/jcasbin-postgres-watcher/latest)
[![Release](https://img.shields.io/github/release/jcasbin/jcasbin-postgres-watcher.svg)](https://github.com/jcasbin/jcasbin-postgres-watcher/releases/latest)
[![Discord](https://img.shields.io/discord/1022748306096537660?logo=discord&label=discord&color=5865F2)](https://discord.gg/S5UjpzGZjN)

jCasbin PostgreSQL Watcher is a [PostgreSQL](https://www.postgresql.org/) watcher for [jCasbin](https://github.com/casbin/jcasbin).

## Installation

**For Maven**

 ```
<dependency>
    <groupId>org.casbin</groupId>
    <artifactId>jcasbin-postgres-watcher</artifactId>
    <version>1.0.0</version>
</dependency>
 ```

## Simple Example

if you have two casbin instances A and B

**A:**  **Producer**

```java
// Initialize PostgreSQL Watcher
String channel = "casbin_channel";
JCasbinPostgresWatcher watcher = new JCasbinPostgresWatcher(
    "jdbc:postgresql://localhost:5432/your_db",
    "postgres",
    "your_password",
    channel
);
// Support for advanced configuration with WatcherConfig
// WatcherConfig config = new WatcherConfig();
// config.setChannel(channel);
// config.setVerbose(true);
// config.setLocalId("instance-1");
// JCasbinPostgresWatcher watcher = new JCasbinPostgresWatcher(url, user, password, config);

Enforcer enforcer = new SyncedEnforcer("examples/rbac_model.conf", "examples/rbac_policy.csv");
enforcer.setWatcher(watcher);

// The following code is not necessary and generally does not need to be written unless you understand what you want to do
/*
Runnable updateCallback = () -> {
    // Custom behavior
};
watcher.setUpdateCallback(updateCallback);
*/

// Modify policy, it will notify B
enforcer.addPolicy(...);

// Using WatcherEx specific methods for fine-grained policy updates
// Add a policy
enforcer.addPolicy(...);
watcher.updateForAddPolicy(...);

```

**B:** **Consumer**

````Java
// Initialize PostgreSQL Watcher with same channel
String channel = "casbin_channel";
JCasbinPostgresWatcher watcher = new JCasbinPostgresWatcher(
    "jdbc:postgresql://localhost:5432/your_db",
    "postgres",
    "your_password",
    channel
);

Enforcer enforcer = new SyncedEnforcer("examples/rbac_model.conf", "examples/rbac_policy.csv");
enforcer.setWatcher(watcher);
// B set watcher and subscribe to the same channel, then it will receive the notification of A, and then call LoadPolicy to reload policy
````

## Getting Help

- [jCasbin](https://github.com/casbin/jCasbin)
- [pgjdbc](https://github.com/pgjdbc/pgjdbc)

## License

This project is under Apache 2.0 License. See the [LICENSE](https://github.com/jcasbin/redis-watcher/blob/master/LICENSE) file for the full license text.