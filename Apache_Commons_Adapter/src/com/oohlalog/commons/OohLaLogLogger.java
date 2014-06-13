package com.oohlalog.commons;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogConfigurationException;


public class OohLaLogLogger implements Log{
	
	
	// ---------------------------------------------------- Log Level Constants

    public static final int LOG_LEVEL_TRACE  = 1;
    public static final int LOG_LEVEL_DEBUG  = 2;
    public static final int LOG_LEVEL_INFO   = 3;
    public static final int LOG_LEVEL_WARN   = 4;
    public static final int LOG_LEVEL_ERROR  = 5;
    public static final int LOG_LEVEL_FATAL  = 6;
    public static final int LOG_LEVEL_ALL    = LOG_LEVEL_TRACE - 1;
    public static final int LOG_LEVEL_OFF    = LOG_LEVEL_FATAL + 1;

    // ---------------------------------------------------- Static Logger Configurations
    
    // All system properties used by <code>OohLaLogLogger</code> start with this 
    static protected final String systemPrefix = "com.oohlalog.commons.";

    // Properties loaded from oohlalog.properties 
    static protected final Properties oohlalogLoggingProps = new Properties();
    
    // The default format to use when formating dates
    static protected final String DEFAULT_DATE_TIME_FORMAT = "yyyy/MM/dd HH:mm:ss:SSS zzz";

    //Include the instance name in the log message? 
    static volatile protected boolean showLogName = false;

    // Include the short name ( last component ) of the logger in the log message. Defaults to true. 
    static volatile protected boolean showShortName = true;

    // Include the current time in the log message 
    static volatile protected boolean showDateTime = false;

    // The date and time format to use in the log message 
//    static volatile protected String dateTimeFormat = DEFAULT_DATE_TIME_FORMAT;

    /**
     * Used to format times.
     * <p>
     * Any code that accesses this object should first obtain a lock on it,
     * ie use synchronized(dateFormatter); this requirement was introduced
     * in 1.1.1 to fix an existing thread safety bug (SimpleDateFormat.format
     * is not thread-safe).
     */
//    static protected DateFormat dateFormatter = null;

	// ------------------------------------------------------------ Instance Variables
	// Holds all of the Logs until reaching a time threshold when they are then emptied out in batches
    private Queue<LogEntry> queue = new ArrayDeque<LogEntry>();
	
    // The time threshold controlling how often uploads to the OLL server are made
    private long timeBuffer = 1000;
    
    // Batch size logs are released in
	private int maxBuffer = 150;//5;
	
	// For congifuring the URL
	private String host = "localhost";
	private String path = "/api/logging/save.json";
	private String statsPath = "/api/timeSeries/save.json";
	private int port = 8196;
	
	
	private String authToken = null;
	private String agent = "commons";
	private boolean secure = false;
	private boolean debug = true;
	private String hostName = null;
	private boolean stats = true;
	private boolean memoryStats = true;
	private boolean fileSystemStats = true;
	private boolean cpuStats = true;
	private long statsInterval = 60000; // 1 minute
	Object previousCpuUsage;

	// The object that controls when requests are actually sent to the OLL server
	private final LogControl logControl;

    // The name of this OohLaLogLogger instance 
    protected volatile String logName = null;
    
    // The current log level
    protected volatile int currentLogLevel;
    
    // The short name of this simple log instance 
    private volatile String logShortName = null;
    
    
    //----------------------------------------------------------------------------------------
    
    /**
     * This section handles loading properties from the properties file.  Specifically, it loads
     * the static properties for all instances of OohLaLogLogger including the boolean fields showLogName, 
     * showShortName, and showDateTime.  
     * 
     * This code has been adapted from org.apache.commons.logging.simplelog.
     */

    private static String getStringProperty(String name) {
        String prop = null;
        try {
            prop = System.getProperty(name);
        } catch (SecurityException e) {
            // Ignore
        }
        return prop == null ? oohlalogLoggingProps.getProperty(name) : prop;
    }

    private static String getStringProperty(String name, String dephault) {
        String prop = getStringProperty(name);
        return prop == null ? dephault : prop;
    }

    private static boolean getBooleanProperty(String name, boolean dephault) {
        String prop = getStringProperty(name);
        return prop == null ? dephault : "true".equalsIgnoreCase(prop);
    }

    // Initialize class attributes.
    // Load properties file, if found.
    // Override with system properties.
    static {
        // Add props from the resource simplelog.properties
        InputStream in = getResourceAsStream("oohlalog.properties");
        if(null != in) {
            try {
            	oohlalogLoggingProps.load(in);
                in.close();
            } catch(java.io.IOException e) {
                // ignored
            }
        }

        showLogName = getBooleanProperty(systemPrefix + "showlogname", showLogName);
        showShortName = getBooleanProperty(systemPrefix + "showshortname", showShortName);
        showDateTime = getBooleanProperty(systemPrefix + "showdatetime", showDateTime);

//        if(showDateTime) {
//            dateTimeFormat = getStringProperty(systemPrefix + "dateTimeFormat",
//                                               dateTimeFormat);
//            try {
//                dateFormatter = new SimpleDateFormat(dateTimeFormat);
//            } catch(IllegalArgumentException e) {
//                // If the format pattern is invalid - use the default format
//                dateTimeFormat = DEFAULT_DATE_TIME_FORMAT;
//                dateFormatter = new SimpleDateFormat(dateTimeFormat);
//            }
//        }
    }
    
    // ------------------------------------------------------------ Constructor

    /**
     * Construct and starts up an OohLaLogLogger with a given name.  It also calls a functions that sets the 
     * authToken and the currentLevel.
     */
    public OohLaLogLogger(String name) {
    	setAuthToken();
    	setCurrentLevel();
    	logName = name;
    	logControl = new LogControl(this, this.maxBuffer, this.timeBuffer);
    	start();
    }



    /**
     * Starts up the OohLaLogLogger by initalizing the logControl belonging to this instance.
     * This is both a public method and is implicitly called when creating an OohLaLogger instance.
     */
	public void start()
    {
		logControl.init();
    	logControl.startFlushTimer();
    	logControl.startStatsTimer();
    }
	
	
//	/**
//	 * Shuts down the automatic logging of the OohLaLogLogger.
//	 */
//	public void stop() {
//		logControl.shutdown();
//	}
    
    
    // -------------------------------------------------------- Logging Methods

    /**
     * Creates a LogEntry and then adds it to the logger's queue.
     */
    protected void log(int type, Object message, Throwable t) {
        // Append date-time if so configured
        Long timeStamp = null;
        if(showDateTime) {
            final Date now = new Date();
            timeStamp = now.getTime();
//            synchronized(dateFormatter) {
//                timeStamp = dateFormatter.format(now);
//            }
        }
        
     // Append the name of the log instance if so configured
        String logShortName = null;
        if(showShortName) {
            if(logShortName == null) {
                // Cut all but the last component of the name for both styles
                final String slName = logName.substring(logName.lastIndexOf(".") + 1);
                logShortName = slName.substring(slName.lastIndexOf("/") + 1);
            }
        } 
   
        // Details
		StringBuilder sbDetails = new StringBuilder();
		if (showLogName || showShortName)
			sbDetails.append("Logger: ");
		if(showLogName)
			sbDetails.append(getLogName()).append(" ");
		if(showShortName)
			sbDetails.append(logShortName).append(" ");
		if(t != null)
			sbDetails.append(t);
		
        String details = sbDetails.toString();
        String category = null;
        
        final LogEntry log = new LogEntry(type, (String)message, logName, logShortName, timeStamp, hostName, details, category);
        queue.add(log);
        this.logControl.checkThreshold();
    }
   
	
    /**
     * Sets the authToken by reading from the properties file.
     */
    private void setAuthToken() {
    	authToken = getStringProperty(systemPrefix + "authToken", authToken);	
    }
    
    
    /**
     * Sets the level of this logger by reading from the properties file.
     */
    private void setCurrentLevel() {
    	// Default
    	setLevel(OohLaLogLogger.LOG_LEVEL_INFO);

        // Set log level from properties
        String lvl = getStringProperty(systemPrefix + "log." + logName);
        int i = String.valueOf(logName).lastIndexOf(".");

        while(null == lvl && i > -1) {
            logName = logName.substring(0,i);
            lvl = getStringProperty(systemPrefix + "log." + logName);
            i = String.valueOf(logName).lastIndexOf(".");
        }

        if(null == lvl) {
            lvl =  getStringProperty(systemPrefix + "defaultlog");
        }

        if("all".equalsIgnoreCase(lvl)) {
            setLevel(OohLaLogLogger.LOG_LEVEL_ALL);
        } else if("trace".equalsIgnoreCase(lvl)) {
            setLevel(OohLaLogLogger.LOG_LEVEL_TRACE);
        } else if("debug".equalsIgnoreCase(lvl)) {
            setLevel(OohLaLogLogger.LOG_LEVEL_DEBUG);
        } else if("info".equalsIgnoreCase(lvl)) {
            setLevel(OohLaLogLogger.LOG_LEVEL_INFO);
        } else if("warn".equalsIgnoreCase(lvl)) {
            setLevel(OohLaLogLogger.LOG_LEVEL_WARN);
        } else if("error".equalsIgnoreCase(lvl)) {
            setLevel(OohLaLogLogger.LOG_LEVEL_ERROR);
        } else if("fatal".equalsIgnoreCase(lvl)) {
            setLevel(OohLaLogLogger.LOG_LEVEL_FATAL);
        } else if("off".equalsIgnoreCase(lvl)) {
            setLevel(OohLaLogLogger.LOG_LEVEL_OFF);
        }
    }
    
    
    
	protected Map<String,Double> getRuntimeStats() {
		Map<String, Double> map = new HashMap<String, Double>();
		Runtime runtime = Runtime.getRuntime();
		map.put("maxMemory", new Double(runtime.maxMemory()));
		map.put("freeMemory", new Double(runtime.freeMemory()));
		map.put("totalMemory", new Double(runtime.totalMemory()));
		map.put("usedMemory", new Double(runtime.totalMemory() - runtime.freeMemory()));
		return map;
	}

//------------------------------------------------------------------------------------Getters/Setters
    
    /**
     * Getter method for returning the logging level associated with this instance of OohLaLogLogger.
     */
    public int getLevel() {
        return currentLogLevel;
    }
	
    
    /**
     * Setter method for setting the logging level associated with this instance of OohLaLogLogger.
     */
    public void setLevel(int currentLogLevel) {
        this.currentLogLevel = currentLogLevel;
    }
    
    
	/**
	 * Getter method for returning the Queue belonging to this OohLaLogLogger instance.
	 */
	public Queue<LogEntry> getQueue() {
		return queue;
	}

	
	/**
	 * Getter method for returning the max buffer size belonging to this OohLaLogLogger instance.
	 */
	public int getMaxBuffer() {
		return maxBuffer;
	}

	
	/**
	 * Setter method for setting the max buffer size belonging to this OohLaLogLogger instance.
	 */
	public void setMaxBuffer(int maxBuffer) {
		this.maxBuffer = maxBuffer;
	}

	
	/**
	 * Getter method for returning the host portion of the URL used for connecting to OohLaLog.
	 */
	public String getHost() {
		return host;
	}

	
	/**
	 * Setter method for setting the host portion of the URL used for connecting to OohLaLog.
	 */
	public void setHost(String host) {
		this.host = host;
	}

	
	/**
	 * Getter method for returning the host name belonging to this OohLaLogLogger instance.
	 */
	public String getHostName() {
		return hostName;
	}

	
	/**
	 * Setter method for setting the host name belonging to this OohLaLogLogger instance.
	 */
	public void setHostName(String hostName) {
		this.hostName = hostName;
	}

	
	/**
	 * Getter method for returning the path portion of the URL used for connecting to OohLaLog.
	 */
	public String getPath() {
		return path;
	}

	
	/**
	 * Getter method for returning the base logging framework used.
	 */
	public String getAgent() {
		return agent;
	}


	/**
	 * Getter method for returning the stats path portion of the URL used for connecting to OohLaLog.
	 */
	public String getStatsPath() {
		return statsPath;
	}

	
	/**
	 * Getter method for returning the port portion of the URL used for connecting to OohLaLog.
	 */
	public int getPort() {
		return port;
	}


	/**
	 * Getter method for returning the authToken used by this instance of OohLaLogLogger
	 * for connecting to an OohLaLog project. 
	 */
	public String getAuthToken() {
		return authToken;
	}

	
	/**
	 * Setter method for setting the authToken used by this instance of OohLaLogLogger
	 * for connecting to an OohLaLog project. 
	 */
	public void setAuthToken(String authToken) {
		this.authToken = authToken;
	}

	
	/**
	 * Getter method for returning the timeBuffer used by this instance of OohLaLogLogger. 
	 * TimeBuffer is the amount of time the logger will wait before issuing requests to
	 * the OohLaLog server.
	 */
	public long getTimeBuffer() {
		return timeBuffer;
	}

	
	/**
	 * Setter method for setting the timeBuffer used by this instance of OohLaLogLogger. 
	 * TimeBuffer is the amount of time the logger will wait before issuing requests to
	 * the OohLaLog server.
	 */
	public void setTimeBuffer(long timeBuffer) {
		this.timeBuffer = timeBuffer;
	}

	
	/**
	 * Getter method for returning whether or not the connection to the OohLaLog server
	 * is secure.
	 */
	public boolean getSecure() {
		return secure;
	}

	
	/**
	 * Setter method for setting whether or not the connection to the OohLaLog server
	 * is secure.
	 */
	public void setSecure(boolean secure) {
		this.secure = secure;
	}

	
	/**
	 * Getter method for returning whether or not the logger is in debug mode.  Debug mode
	 * will cause many debug messages to be printed to StdOut.
	 */
	public boolean getDebug() {
		return debug;
	}

	
	/**
	 * Setter method for setting whether or not the logger is in debug mode.  Debug mode
	 * will cause many debug messages to be printed to StdOut.
	 */
	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	
	/**
	 * Getter method for returning a boolean indicating whether or not logging stats 
	 * associated with this instance of OohLaLogLogger will be sent to the OohLaLog server.
	 */
	public boolean getStats() {
		return stats;
	}

	
	/**
	 * Setter method for setting a boolean indicating whether or not logging stats 
	 * associated with this instance of OohLaLogLogger will be sent to the OohLaLog server.
	 */
	public void setStats(boolean stats) {
		this.stats = stats;
	}

	
	/**
	 * Getter method for returning a long representing the time in milliseconds this instance of 
	 * OohLaLogLogger will wait before sending stats to the OohLaLog server.
	 */
	public long getStatsInterval() {
		return statsInterval;
	}

	
	/**
	 * Setter method for setting a long representing the time in milliseconds this instance of 
	 * OohLaLogLogger will wait before sending stats to the OohLaLog server.
	 */
	public void setStatsInterval(long statsInterval) {
		this.statsInterval = statsInterval;
	}

	
	/**
	 * Getter method for returning a boolean indicating whether or not memory stats 
	 * associated with this instance of OohLaLogLogger will be sent to the OohLaLog server.
	 */
	public boolean getMemoryStats() {
		return memoryStats;
	}

	
	/**
	 * Setter method for setting a boolean indicating whether or not memory stats 
	 * associated with this instance of OohLaLogLogger will be sent to the OohLaLog server.
	 */
	public void setMemoryStats(boolean memoryStats) {
		this.memoryStats = memoryStats;
	}

	
	/**
	 * Getter method for returning a boolean indicating whether or not cpu stats 
	 * associated with this instance of OohLaLogLogger will be sent to the OohLaLog server.
	 */
	public boolean getCpuStats() {
		return cpuStats;
	}

	
	/**
	 * Setter method for setting a boolean indicating whether or not cpu stats 
	 * associated with this instance of OohLaLogLogger will be sent to the OohLaLog server.
	 */
	public void setCpuStats(boolean cpuStats) {
		this.cpuStats = cpuStats;
	}

	
	/**
	 * Getter method for returning a boolean indicating whether or not file system stats 
	 * associated with this instance of OohLaLogLogger will be sent to the OohLaLog server.
	 */
	public boolean getFileSystemStats() {
		return fileSystemStats;
	}

	
	/**
	 * Setter method for setting a boolean indicating whether or not file system stats 
	 * associated with this instance of OohLaLogLogger will be sent to the OohLaLog server.
	 */
	public void setFileSystemStats(boolean fileSystemStats) {
		this.fileSystemStats = fileSystemStats;
	}

	
	/**
     * Is the given log level currently enabled?
     */
    protected boolean isLevelEnabled(int logLevel) {
        // log level are numerically ordered so can use simple numeric
        // comparison
        return logLevel >= currentLogLevel;
    }

    // -------------------------------------------------------- Log Implementation

    public String getLogName() {
		return logName;
	}

    
	public void setLogName(String logName) {
		this.logName = logName;
	}

	
	public String getLogShortName() {
		return logShortName;
	}

	
	public void setShortLogName(String logShortName) {
		this.logShortName = logShortName;
	}

	
	public final void debug(Object message) {
        if (isLevelEnabled(OohLaLogLogger.LOG_LEVEL_DEBUG)) {
            log(OohLaLogLogger.LOG_LEVEL_DEBUG, message, null);
        }
    }


    public final void debug(Object message, Throwable t) {
        if (isLevelEnabled(OohLaLogLogger.LOG_LEVEL_DEBUG)) {
            log(OohLaLogLogger.LOG_LEVEL_DEBUG, message, t);
        }
    }


    public final void trace(Object message) {
        if (isLevelEnabled(OohLaLogLogger.LOG_LEVEL_TRACE)) {
            log(OohLaLogLogger.LOG_LEVEL_TRACE, message, null);
        }
    }


    public final void trace(Object message, Throwable t) {
        if (isLevelEnabled(OohLaLogLogger.LOG_LEVEL_TRACE)) {
            log(OohLaLogLogger.LOG_LEVEL_TRACE, message, t);
        }
    }


    public final void info(Object message) {
        if (isLevelEnabled(OohLaLogLogger.LOG_LEVEL_INFO)) {
            log(OohLaLogLogger.LOG_LEVEL_INFO,message,null);
        }
    }


    public final void info(Object message, Throwable t) {
        if (isLevelEnabled(OohLaLogLogger.LOG_LEVEL_INFO)) {
            log(OohLaLogLogger.LOG_LEVEL_INFO, message, t);
        }
    }


    public final void warn(Object message) {
        if (isLevelEnabled(OohLaLogLogger.LOG_LEVEL_WARN)) {
            log(OohLaLogLogger.LOG_LEVEL_WARN, message, null);
        }
    }


    public final void warn(Object message, Throwable t) {
        if (isLevelEnabled(OohLaLogLogger.LOG_LEVEL_WARN)) {
            log(OohLaLogLogger.LOG_LEVEL_WARN, message, t);
        }
    }


    public final void error(Object message) {
        if (isLevelEnabled(OohLaLogLogger.LOG_LEVEL_ERROR)) {
            log(OohLaLogLogger.LOG_LEVEL_ERROR, message, null);
        }
    }


    public final void error(Object message, Throwable t) {
        if (isLevelEnabled(OohLaLogLogger.LOG_LEVEL_ERROR)) {
            log(OohLaLogLogger.LOG_LEVEL_ERROR, message, t);
        }
    }


    public final void fatal(Object message) {
        if (isLevelEnabled(OohLaLogLogger.LOG_LEVEL_FATAL)) {
            log(OohLaLogLogger.LOG_LEVEL_FATAL, message, null);
        }
    }


    public final void fatal(Object message, Throwable t) {
        if (isLevelEnabled(OohLaLogLogger.LOG_LEVEL_FATAL)) {
            log(OohLaLogLogger.LOG_LEVEL_FATAL, message, t);
        }
    }


    public final boolean isDebugEnabled() {
        return isLevelEnabled(OohLaLogLogger.LOG_LEVEL_DEBUG);
    }


    public final boolean isErrorEnabled() {
        return isLevelEnabled(OohLaLogLogger.LOG_LEVEL_ERROR);
    }


    public final boolean isFatalEnabled() {
        return isLevelEnabled(OohLaLogLogger.LOG_LEVEL_FATAL);
    }


    public final boolean isInfoEnabled() {
        return isLevelEnabled(OohLaLogLogger.LOG_LEVEL_INFO);
    }


    public final boolean isTraceEnabled() {
        return isLevelEnabled(OohLaLogLogger.LOG_LEVEL_TRACE);
    }


    public final boolean isWarnEnabled() {
        return isLevelEnabled(OohLaLogLogger.LOG_LEVEL_WARN);
    }

    
    //----------------------------------------------------------------------------------------
    
    /**
     * Return the thread context class loader if available.
     * Otherwise return null.
     *
     * The thread context class loader is available for JDK 1.2
     * or later, if certain security conditions are met.
     *
     * This code has been adapted from org.apache.commons.logging.simplelog.
     * @exception LogConfigurationException if a suitable class loader
     * cannot be identified.
     */
    private static ClassLoader getContextClassLoader() {
        ClassLoader classLoader = null;

        try {
            // Are we running on a JDK 1.2 or later system?
            final Method method = Thread.class.getMethod("getContextClassLoader", (Class[]) null);

            // Get the thread context class loader (if there is one)
            try {
                classLoader = (ClassLoader)method.invoke(Thread.currentThread(), (Class[]) null);
            } catch (IllegalAccessException e) {
                // ignore
            } catch (InvocationTargetException e) {
                /**
                 * InvocationTargetException is thrown by 'invoke' when
                 * the method being invoked (getContextClassLoader) throws
                 * an exception.
                 *
                 * getContextClassLoader() throws SecurityException when
                 * the context class loader isn't an ancestor of the
                 * calling class's class loader, or if security
                 * permissions are restricted.
                 *
                 * In the first case (not related), we want to ignore and
                 * keep going.  We cannot help but also ignore the second
                 * with the logic below, but other calls elsewhere (to
                 * obtain a class loader) will trigger this exception where
                 * we can make a distinction.
                 */
                if (e.getTargetException() instanceof SecurityException) {
                    // ignore
                } else {
                    // Capture 'e.getTargetException()' exception for details
                    // alternate: log 'e.getTargetException()', and pass back 'e'.
                    throw new LogConfigurationException
                        ("Unexpected InvocationTargetException", e.getTargetException());
                }
            }
        } catch (NoSuchMethodException e) {
            // Assume we are running on JDK 1.1
            // ignore
        }

        if (classLoader == null) {
            classLoader = OohLaLogLogger.class.getClassLoader();
        }

        // Return the selected class loader
        return classLoader;
    }
    
    protected static InputStream getResourceAsStream(final String name) {
        return (InputStream)AccessController.doPrivileged(
            new PrivilegedAction() {
                public Object run() {
                    ClassLoader threadCL = getContextClassLoader();

                    if (threadCL != null) {
                        return threadCL.getResourceAsStream(name);
                    } else {
                        return ClassLoader.getSystemResourceAsStream(name);
                    }
                }
            });
    }

}
