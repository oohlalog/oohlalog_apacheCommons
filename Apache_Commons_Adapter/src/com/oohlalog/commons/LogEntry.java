package com.oohlalog.commons;


public class LogEntry {

	
	 /** "Trace" level logging. */
    public static final int LOG_LEVEL_TRACE  = 1;
    /** "Debug" level logging. */
    public static final int LOG_LEVEL_DEBUG  = 2;
    /** "Info" level logging. */
    public static final int LOG_LEVEL_INFO   = 3;
    /** "Warn" level logging. */
    public static final int LOG_LEVEL_WARN   = 4;
    /** "Error" level logging. */
    public static final int LOG_LEVEL_ERROR  = 5;
    /** "Fatal" level logging. */
    public static final int LOG_LEVEL_FATAL  = 6;
    
    
    /** Array containing the names(as Strings) of all different logging levels.  It is these strings
     * that will appear in the log messages.
     */
    public static final String[] levelNames = {"TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL"};
    
	private int level;
	private String message;
	private String logName;
	private String logShortName;
	private Long timeStamp;
	private String hostName;
	private String details;
	private String category;
	private String levelString;
	

	/**
	 * Constructor that creates a Log Entry object.
	 * @param level the level of the Log Entry
	 * @param message the message of the Log Entry
	 * @param logName the full name of the Logger
	 * @param logShortName the shortened name of the Logger
	 * @param timeStamp the time this log was sent
	 * @param hostName the name of the hose this log was sent from 
	 * @param details details about this log
	 * @param category the category that this log falls under
	 */
	public LogEntry(int level, String message, String logName, String logShortName, Long timeStamp, String hostName, String details, String category)
	{
		this.level = level;
		this.message = message;
		this.levelString = levelNames[level - 1];
		this.logName = logName;
		this.logShortName = logShortName;
		this.timeStamp = timeStamp;
		this.hostName = hostName;
		this.details = details;
		this.category = category;
	}


	public int getLevel() {
		return level;
	}

	
	public String getMessage() {
		return message;
	}

	
	public String getLevelString() {
		return levelString;
	}


	public String getLogName() {
		return logName;
	}


	public String getLogShortName() {	
		return logShortName;
	}


	public Long getTimeStamp() {
		return timeStamp;
	}

	
	public String getHostName() {
		return hostName;
	}
	
	public String getDetails() {
		return details;
	}
	
	
	public String getCategory() {
		return category;
	}

	
}
