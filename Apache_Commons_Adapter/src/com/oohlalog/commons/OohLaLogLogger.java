package com.oohlalog.commons;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.BlockingDeque;

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

	// ------------------------------------------------------------ Instance Variables
    
    // The time threshold controlling how often uploads of logs are made to the OLL server
    private long timeBuffer = 10000;
    
    // Logs are flushed once buffer reaches this size
    private int threshold = 100;
    
    // Maximum allowed size of the buffer
	private int maxBuffer = 1000;//5;
	
	// Holds all of the Logs until reaching a time threshold when they are then emptied out in batches
	private LogEntryBuffer logEntryBuffer;
	
    // The time threshold controlling how often uploads of statistics are made to the OLL server
	private long statsBuffer = 60000; // 1 minute
	
	// For configuring the URL
	private String host = "api.oohlalog.com"; //localhost"
	private String path = "/api/logging/save.json";
	private String statsPath = "/api/timeSeries/save.json";
	private int port = 80; //8196
	
	private String authToken = null;
	private String agent = "commons";
	private boolean secure = false;
	private boolean debug = true;
	private String hostName = null;
	
	private boolean showMemoryStats = true;
	private boolean showFileSystemStats = true;
	private boolean showCPUStats = true;
	private boolean showStats = true;
	
	Object previousCpuUsage;

	// The object that controls when requests are actually sent to the OLL server
	private final LogControl logControl;

    // The name of this OohLaLogLogger instance 
    private String logName;;
    
    // The current log level
    protected volatile int currentLogLevel;
    
    // The short name of this simple log instance 
    private String logShortName;
    
    
    //----------------------------------------------------------------------------------------
    
    /**
     * This section handles loading properties from the properties file.  Specifically, it loads
     * the static properties for all instances of OohLaLogLogger including the boolean fields showLogName and
     * showShortName.  
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
    
    private static long getLongProperty(String name, long dephault) {
        Long prop = Long.parseLong(getStringProperty(name));
        return prop == null ? dephault : prop;
    }
    
    private static int getIntProperty(String name, int dephault) {
        Integer prop = Integer.parseInt(getStringProperty(name));
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
        
        showLogName = getBooleanProperty(systemPrefix + "showLogName", showLogName);
        showShortName = getBooleanProperty(systemPrefix + "showShortName", showShortName);
    }
    
    // ------------------------------------------------------------ Constructor

    /**
     * Construct and starts up an OohLaLogLogger with a given name.  It also calls a functions that sets the 
     * authToken and the currentLevel.
     */
    public OohLaLogLogger(String name) {
    	logName = name;
    	setAuthToken();
    	setCurrentLevel();
    	setShowStats();
    	setLoggingInterval();
    	String temp = logName.substring(logName.lastIndexOf(".") + 1);
        logShortName = temp.substring(temp.lastIndexOf("/") + 1);
        
        logEntryBuffer = new LogEntryBuffer(maxBuffer);
    	logControl = new LogControl(this, this.threshold, this.timeBuffer, this.statsBuffer);
    	logControl.init();
    }
	
    
    
    // -------------------------------------------------------- Logging Methods

    /**
     * Creates a LogEntry and then adds it to the logger's deque.
     */
    protected void log(int type, Object message, Throwable t) {
        // Append time stamp
    	Date now = new Date();
        Long timeStamp = now.getTime();
        
     // Append the name of the log instance if so configured
        String shortName = showShortName? logShortName : null;  
   
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
        
        final LogEntry log = new LogEntry(type, (String)message, logName, shortName, timeStamp, hostName, details, category);
        
        
        // Adds the log to the buffer, knocking off an old log if needed
        getLogEntryBuffer().addLogToBuffer(log);
        
        // Don't need to have the flushTimer going when there are no log entries in the deque. 
        // Instead, we start the timer after adding an element which increasing deque size 
        // from 0 to 1
        if (getLogEntryBuffer().size() == 1)
        	this.logControl.startFlushTimer();
    }
   
	
    /**
     * Sets the authToken by reading from the properties file.
     */
    private void setAuthToken() {
    	authToken = getStringProperty(systemPrefix + "authToken", authToken);	
    }
    
    
    /**
     * Sets the boolean values that control whether or not the different statistics should be sent to the server.
     */
    private void setShowStats() {
        showMemoryStats = getBooleanProperty(systemPrefix + "showMemoryStats", showMemoryStats);
        showFileSystemStats = getBooleanProperty(systemPrefix + "showFileSystemStats", showFileSystemStats);
        showCPUStats = getBooleanProperty(systemPrefix + "showCPUStats", showCPUStats);
        showStats = getBooleanProperty(systemPrefix + "showStats", showStats);
    }
    
    
    /**
     * Configures the frequency with which logs are sent to the OohLaLog server by reading the 
     * setting form the properties file.
     */
    private void setLoggingInterval() {
    	timeBuffer = getLongProperty(systemPrefix + "timeBuffer", timeBuffer);
    	statsBuffer = getLongProperty(systemPrefix + "statsBuffer", timeBuffer);
    	threshold = getIntProperty(systemPrefix + "threshold", threshold);
    	maxBuffer = getIntProperty(systemPrefix + "maxBuffer", maxBuffer);
    }
    
    
    /**
     * Sets the level of this logger by reading from the properties file.
     */
    private void setCurrentLevel() {
    	// Default
    	setLevel(OohLaLogLogger.LOG_LEVEL_INFO);

        // Set log level from properties
        String lvl = null;
        lvl = getStringProperty(systemPrefix + "log." + logName);
        int i = String.valueOf(logName).lastIndexOf(".") + 1;
        
        if (lvl == null) {
        	lvl = getStringProperty(systemPrefix + "log." + logName.substring(i));
        }

        if(lvl == null) {
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
	 * Getter method for returning the max buffer size belonging to this OohLaLogLogger instance.
	 */
	public int getMaxBuffer() {
		return maxBuffer;
	}

	/**
	 * Getter method for returning the LogEntryBuffer belonging to this OohLaLogLogger instance.
	 */
	protected synchronized LogEntryBuffer getLogEntryBuffer() {
		return logEntryBuffer;
	}

//	protected synchronized void setLogEntryBuffer(LogEntryBuffer logEntryBuffer) {
//		this.logEntryBuffer = logEntryBuffer;
//	}

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
	public boolean getShowStats() {
		return showStats;
	}

	
	/**
	 * Setter method for setting a boolean indicating whether or not logging stats 
	 * associated with this instance of OohLaLogLogger will be sent to the OohLaLog server.
	 */
	public void setShowStats(boolean showStats) {
		this.showStats = showStats;
	}

	
	/**
	 * Getter method for returning a long representing the time in milliseconds this instance of 
	 * OohLaLogLogger will wait before sending stats to the OohLaLog server.
	 */
	public long getstatsBuffer() {
		return statsBuffer;
	}

	
	/**
	 * Setter method for setting a long representing the time in milliseconds this instance of 
	 * OohLaLogLogger will wait before sending stats to the OohLaLog server.
	 */
	public void setstatsBuffer(long statsBuffer) {
		this.statsBuffer = statsBuffer;
	}

	
	/**
	 * Getter method for returning a boolean indicating whether or not memory stats 
	 * associated with this instance of OohLaLogLogger will be sent to the OohLaLog server.
	 */
	public boolean getShowMemoryStats() {
		return showMemoryStats;
	}

	
	/**
	 * Setter method for setting a boolean indicating whether or not memory stats 
	 * associated with this instance of OohLaLogLogger will be sent to the OohLaLog server.
	 */
	public void setShowMemoryStats(boolean showMemoryStats) {
		this.showMemoryStats = showMemoryStats;
	}

	
	/**
	 * Getter method for returning a boolean indicating whether or not cpu stats 
	 * associated with this instance of OohLaLogLogger will be sent to the OohLaLog server.
	 */
	public boolean getShowCPUStats() {
		return showCPUStats;
	}

	
	/**
	 * Setter method for setting a boolean indicating whether or not cpu stats 
	 * associated with this instance of OohLaLogLogger will be sent to the OohLaLog server.
	 */
	public void setShowCPUStats(boolean showCPUStats) {
		this.showCPUStats = showCPUStats;
	}

	
	/**
	 * Getter method for returning a boolean indicating whether or not file system stats 
	 * associated with this instance of OohLaLogLogger will be sent to the OohLaLog server.
	 */
	public boolean getShowFileSystemStats() {
		return showFileSystemStats;
	}

	
	/**
	 * Setter method for setting a boolean indicating whether or not file system stats 
	 * associated with this instance of OohLaLogLogger will be sent to the OohLaLog server.
	 */
	public void setShowFileSystemStats(boolean showFileSystemStats) {
		this.showFileSystemStats = showFileSystemStats;
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
