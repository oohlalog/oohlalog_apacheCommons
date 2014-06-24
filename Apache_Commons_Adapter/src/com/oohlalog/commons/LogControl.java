package com.oohlalog.commons;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class LogControl {
	// The time interval between automatic flushes of logs
	private long timeBuffer;
	// The time interval between automatic flushes of statistical data
	private long statsInterval;
	// Time of last flush
	private long lastFlush = System.currentTimeMillis();
	// Time of last failed flush
	private long lastFailedFlush = System.currentTimeMillis();
	// Time to wait between failed flushes
	private long failedFlushWait = 2000;
	// Is a flushing process currently happening? 
	private final AtomicBoolean flushing = new AtomicBoolean( false );
	// Maximum size of the deque before we automatically flush it
	private int threshold;

	// The logger instance belonging to this LogControl
	private OohLaLogLogger logger;


	/**
	 * Constructor that creates our LogControl object.
	 * 
	 * @param logger the OohLaLogLogger that this LogControl is tied to
	 * @param threshold the amount of logs to be buffered before a flush
	 * @param timeBuffer the amount of time to wait before flushes
	 * @param statsInterval the amount of time to wait before gathering and sending usage statistics
	 */
	public LogControl(OohLaLogLogger logger, int threshold, long timeBuffer, long statsInterval) {
		this.logger = logger;
		this.threshold = threshold;
		this.timeBuffer = timeBuffer;
		this.statsInterval = statsInterval;
	}


	/**
	 * Initializes the Log Control object.  It starts the thread that checks for, and handles three events:
	 * 1. Event: Deque of logs reaches threshold	Action: Flush threshold value of logs to OLL server
	 * 2. Event: Log timer goes off					Action: Flush all logs in the deque to the OLL server
	 * 3. Event: Stats timer goes off				Action: Flush stats to the OLL server
	 */
	protected void init() {
		// Starts the thread that checks to see if the size of the deque is greater than 150
		startThresholdCheck();
		
		// Only start the stats thread if the user specified
		if (this.logger.getShowStats())
			startStatsTimer();
		
		// Only start the flush timer if there is something in the deque.
		if (this.logger.getLogEntryBuffer().size() > 0)
			startFlushTimer();
	}
	
	
	/**
	 * Flushes the deque of log entries if the deque is of size greater than buffer threshold.
	 */
	protected void startThresholdCheck() {
		final OohLaLogLogger logger = this.logger;
		Thread t = new Thread( new Runnable() {
			public void run() {
				while (true) {
					LogEntryBuffer buffer = logger.getLogEntryBuffer();
					if ( (buffer.size() >= threshold) && !flushing.get() && 
							(System.currentTimeMillis() - lastFailedFlush > failedFlushWait) ) {
						if (logger.getDebug()) System.out.println( ">>>Above Threshold" );
						flush(threshold);		
					}
				}
			}
		});
		// If the JVM exits, we don't want this thread to prevent us from doing so as well
		t.setDaemon(true);
		t.start();
	}

	
	/**
	 * Starts the timer that will cause logs to be flushed at the set interval.  This thread runs to completion
	 * when the deque is empty and get re-instantiated on first add to the deque.  This keeps the thread from running
	 * forever while making sure that even when JVM finishes, all remaining logs are logged.
	 */
	protected void startFlushTimer() {
		final OohLaLogLogger logger = this.logger;
		Thread t = new Thread( new Runnable() {
			public void run() {
				// If appender closes, let thread die
				while ( logger.getLogEntryBuffer().size() != 0 ) {
					if (logger.getDebug()) System.out.println( ">>Timer Cycle" );

					// If timeout, flush deque
					if ( (System.currentTimeMillis() - lastFlush > timeBuffer) && !flushing.get() ) {
						if (logger.getDebug()) System.out.println( ">>>Flushing from timer expiration" );
						flush(Integer.MAX_VALUE );
						
						// This thread is done after flushing
						break;
					}

					// Wait for a time interval
					try {
						Thread.sleep(timeBuffer);
					}
					catch ( InterruptedException ie ) {
						// Ignore, and continue
					}
				}
			}
		});
//		t.setDaemon(true);
		t.start();
	}


	/**
	 * Starts the timer that will cause statistics to be flushed at the set interval.
	 */
	protected void startStatsTimer() {
		final OohLaLogLogger logger = this.logger;
		Thread t = new Thread( new Runnable() {
			public void run() {
				// If appender closes, let thread die
				while (true ) {
					if (logger.getShowStats()) {
						if (logger.getDebug()) System.out.println( ">>Stats Timer" );
						Map<String,Double> metrics = StatsUtils.getStats(logger);
						StatsPayload pl= new StatsPayload.Builder()
						.metrics(metrics)
						.authToken(logger.getAuthToken())
						.host(logger.getHost())
						.agent(logger.getAgent())
						.path(logger.getStatsPath())
						.port(logger.getPort())
						.secure(logger.getSecure())
						.debug(logger.getDebug())
						.build();
						StatsPayload.send( pl );
					}

					// Sleep the thread
					try {
						Thread.sleep(statsInterval);
					}
					catch ( InterruptedException ie ) {
						// Ignore, and continue
					}
				}
			}
		});

		// If the JVM exits, we don't want this thread to prevent us from doing so as well
		t.setDaemon(true);
		t.start();
	}


	/**
	 * Flush at most amtToFlush items from the deque.
	 * 
	 * @param maxAmtToFlush the maximum amount of log entries to flush from buffer
	 */
	protected void flush(final int maxAmtToFlush ) {
		final OohLaLogLogger logger = this.logger;
		if (logger.getDebug()) System.out.println( ">>>>>>Flushing Deque Completely" );
		flushing.set( true );
		Thread t = new Thread( new Runnable() {
			public void run() {
				boolean success = logger.getLogEntryBuffer().flushLogEntryBuffer(logger, maxAmtToFlush);
				// Payload successfully delivered so we can remove the logs that we already sent.
				if (success) {
					lastFlush = System.currentTimeMillis();
				}
				
				else {
					lastFailedFlush = System.currentTimeMillis();
				}
				flushing.set( false );
				return;
			}
		});
		t.start();
	}

}
