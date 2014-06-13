OohLaLog Apache Commons implementation
======================


This logging implementation for Oohlalog Cloud Logging Service will post log messages the OohLaLog using the standard Apache Commons Logging interface and configuration.

Since the Apache Commons Logging interface has direct support for Log4j, if you would like to use log4j for the underlying log implementaion, you may do so.  Just be sure 
to visit https://github.com/oohlalog/oohlalog_log4j to learn how to properly configure the appropriate OohLaLog appender.

If you wish not to use the underlying log4j implementation you can use the one inclduded here.

##Configuration

Please follow standard Commons properties-based configuration and but be sure to include the following in your commons-logging.properties file:

```
org.apache.commons.logging.Log=com.oohlalog.commons.OohLaLogLogger
```

Additional configuration options are to be placed in an alternative properties file, oohlalog.properties.  
The only required property to include is your OohLaLog authToken.  
Similarly to the Commons standard, you have the ability to specify whether you would like to see the full log name, the shortened version of the name, or both.

You also can specify the minimum loggign level for the logger.  For example, if set to "warn", only messages of equal severity or higher will be logged.

Specific to OohLaLog, you have the ability to configure how often logs are uploaded to the OohLaLog server, in addition to having control over which statistics 
you would like uploaded. 

Below is an example oohlalog.properties file.
```
# Must set the authToken.
com.oohlalog.commons.authToken=1f111a85-62c7-4f42-8dd9-8a10bb80dc6e

# Optional: Show the log name in every message. 
# Defaults = false.
com.oohlalog.commons.showLogName=false

# Optional: Show the last component of the name to be output with every message. 
# Default = true.
com.oohlalog.commons.showShortName=true

# Optional: Logging level for all instances of OohLaLogLogger.
# Must be either trace, debug, info, warn, error or fatal. 
# Default = info.
com.oohlalog.commons.defaultlog=info

# Optional: Amount of time in milliseconds before an automatic flush of all logs (lower numbers impact app performance)
# Default = 10000
com.oohlalog.commons.timeBuffer=10000

# Optional: Amount of time in milliseconds before an automatically posting usage statistics to OohLaLog (lower numbers impact app performance)
# Default = 60000
com.oohlalog.commons.statsBuffer=60000

# Optional: Number logs to buffer before posting to OohLaLog (lower numbers impact app performance)
# Default = 150
com.oohlalog.commons.maxBuffer=150

# Optional: Do you want Memory Statistics to be posted to OohLaLog
# Default = true
com.oohlalog.commons.showMemoryStats=true

# Optional: Do you want CPU Statistics to be posted to OohLaLog
# Default = true
com.oohlalog.commons.showCPUStats=true

# Optional: Do you want File System Statistics to be posted to OohLaLog
# Default = true
com.oohlalog.commons.showFileSystemStats=true

# Optional: Specifies whether or not you would like to send any usage statistics to OohLaLog.  
#           This option has greater priority than showMemoryStats, showCPUStats, and showFileSystemStats
# Default = true
com.oohlalog.commons.Stats=true

# Optional: Logging detail level for an OohLaLogLogger instance named MyClassLogger.
# Must be either trace, debug, info, warn, error or fatal. Defaults to the
# above default if not set.
com.oohlalog.commons.log.MyClassLogger=warn

```


##Dependencies

To use the OohLaLog Log4J Appender please include the following jars in your classpath:

###1. OohLaLog Jar
```
TODO
```

###2. Commons Logging JAR
```
commons-logging-1.1.3.jar
```

### 3. GSON Jar
```
gson-2.2.4.jar
```
Repository Info:

Maven Central

Maven info:
```
<dependency>
  <groupId>com.google.code.gson</groupId>
  <artifactId>gson</artifactId>
  <version>2.2.4</version>
</dependency>
```

