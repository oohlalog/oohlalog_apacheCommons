package com.oohlalog.commons;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

public class LogControl {
	private long timeBuffer;
	private long lastFlush = System.currentTimeMillis();
	private final AtomicBoolean flushing = new AtomicBoolean( false );

	// Config options
	private int maxBuffer;
	private long statsInterval;

	private OohLaLogLogger logger;

	public LogControl(OohLaLogLogger logger, int maxBuffer, long timeBuffer) {
		this.logger = logger;
		this.maxBuffer = maxBuffer;
		this.timeBuffer = timeBuffer;
	}


	/**
	 * Starts the timing processes.
	 */
	protected void init() {
		startFlushTimer();
		if (logger.getStats()) startStatsTimer();
	}


	/**
	 * Flushes the queue of log entries if the queue is of size greater than buffer threshold.
	 */
	protected void checkThreshold()
	{
		Queue<LogEntry> buffer = this.logger.getQueue();
		if (buffer.size() > maxBuffer && !flushing.get()) {
			if (logger.getDebug()) System.out.println( ">>>Above Threshold" );
			flushQueue(buffer);		
		}
	}



	/**
	 * Starts the timer that will cause logs to be flushed at the set interval.
	 */
	protected void startFlushTimer() {
		final OohLaLogLogger logger = this.logger;
		Thread t = new Thread( new Runnable() {
			public void run() {
				// If appender closes, let thread die
				while ( true ) {
					if (logger.getDebug()) System.out.println( ">>Timer Cycle" );

					// If timeout, flush queue
					if ( (System.currentTimeMillis() - lastFlush > timeBuffer) && !flushing.get() ) {
						if (logger.getDebug()) System.out.println( ">>>Flushing from timer expiration" );
						flushQueue( logger.getQueue() );
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
		
		// If the JVM exits, we don't want this thread to prevent us from doing so as well
		t.setDaemon(true);
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
					if (logger.getStats()) {
						if (logger.getDebug()) System.out.println( ">>Stats Timer" );
						Map<String,Double> metrics = StatsUtils.getStats(logger);
						StatsPayload pl= new StatsPayload.Builder()
						.metrics(metrics)
						.authToken(logger.getAuthToken())
						.host(logger.getHost())
						.agent(logger.getAgent())
						.path(logger.getPath())
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
	 * Flush queue completely.
	 */
	protected void flushQueue( final Queue<LogEntry> queue ) {
		final OohLaLogLogger logger = this.logger;
		if (logger.getDebug()) System.out.println( ">>>>>>Flushing Queue Completely" );
		flushing.set( true );
		Thread t = new Thread( new Runnable() {
			public void run() {
				int size = queue.size();
				List<LogEntry> logs = new ArrayList<LogEntry>(size);
				for (int i = 0; i < size; i++) {
					LogEntry log;
					if ((log = queue.poll()) == null)
						break;

					logs.add(log);
				}

				if(logs.size() == 0) {
					flushing.set(false);
					return;
				}
				Payload pl = new Payload.Builder()
				.messages(logs)
				.authToken(logger.getAuthToken())
				.host(logger.getHost())
				.agent(logger.getAgent())
				.path(logger.getPath())
				.port(logger.getPort())
				.secure(logger.getSecure())
				.debug(logger.getDebug())
				.build();

				Payload.send( pl );

				lastFlush = System.currentTimeMillis();
				flushing.set( false );
			}
		});
				t.start();
	}



	public long getTimeBuffer() {
		return timeBuffer;
	}


	public void setTimeBuffer(long timeBuffer) {
		this.timeBuffer = timeBuffer;
	}


	public long getLastFlush() {
		return lastFlush;
	}


	public void setLastFlush(long lastFlush) {
		this.lastFlush = lastFlush;
	}



	public int getMaxBuffer() {
		return maxBuffer;
	}


	public void setMaxBuffer(int maxBuffer) {
		this.maxBuffer = maxBuffer;
	}




	public long getStatsInterval() {
		return statsInterval;
	}


	public void setStatsInterval(long statsInterval) {
		this.statsInterval = statsInterval;
	}



	public OohLaLogLogger getLogger() {
		return logger;
	}


	public void setLogger(OohLaLogLogger logger) {
		this.logger = logger;
	}


	public AtomicBoolean getFlushing() {
		return flushing;
	}



}
