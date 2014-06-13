package com.oohlalog.commons;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class LogControl {
	private ExecutorService executorService = null;
	private long timeBuffer;
	private long lastFlush = System.currentTimeMillis();
	private final AtomicBoolean flushing = new AtomicBoolean( false );
	private final AtomicBoolean shutdown = new AtomicBoolean( false );

	// Config options
	private int maxBuffer = 150;
	private long statsInterval = 6000;
	private int submissionThreadPool = 5;

	private OohLaLogLogger logger;

	public LogControl(OohLaLogLogger logger, int maxBuffer, long timeBuffer) {
		this.logger = logger;
		this.maxBuffer = maxBuffer;
		this.timeBuffer = timeBuffer;
	}


	protected void init() {
		if ( this.executorService != null ) {
			this.executorService.shutdown();
		}
		this.executorService = Executors.newFixedThreadPool(this.submissionThreadPool);
	}


	/**
	 * Flushes the queue of log entries if the queue is of size greater than buffer threshold.
	 */
	protected void checkThreshold()
	{
		//		if (logger == null) shutdownAndAwaitTermination();
		Queue<LogEntry> buffer = this.logger.getQueue();
		if (buffer.size() > maxBuffer && !flushing.get()) {
			if (logger.getDebug()) System.out.println( ">>>Above Threshold" );
			flushQueue(buffer);		
		}
	}




	protected void startFlushTimer() {
		final OohLaLogLogger logger = this.logger;
		//		executorService.execute(new Runnable() {
		Thread t = new Thread( new Runnable() {
			public void run() {
				// If appender closes, let thread die
				while ( true ) {
					if (logger.getDebug()) System.out.println( ">>Timer Cycle" );

					// If timeout, flush queue
					if ( (System.currentTimeMillis() - lastFlush > timeBuffer) && !flushing.get() ) {
						System.out.println("HERERERERERERERERER");
						if (logger.getDebug()) System.out.println( ">>>Flushing from timer expiration" );
						flushQueue( logger.getQueue(), maxBuffer );
					}

					// Sleep the thread
					try {
						Thread.sleep(timeBuffer);
					}
					catch ( InterruptedException ie ) {
						// Ignore, and continue
					}
				}
			}
		});
		t.setDaemon(true);
		t.start();

	}


	protected void startStatsTimer() {
		final OohLaLogLogger logger = this.logger;
		//		executorService.execute(new Runnable() {
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
		t.setDaemon(true);
		t.start();
	}



	/**
	 * Flush <b>count</b> number of items from queue
	 * @param queue
	 */
	protected void flushQueue( final Queue<LogEntry> queue, final int count ) {
		final OohLaLogLogger logger = this.logger;
		if (logger.getDebug()) System.out.println( ">>>>>>Flushing " + count + " items from queue");
		flushing.set( true );

		if(queue.isEmpty()) {
			flushing.set( false );
			return;
		}
		Thread t = new Thread( new Runnable() {
//		executorService.execute(new Runnable() {
			public void run() {
				List<LogEntry> logs = new ArrayList<LogEntry>(count);
				for (int i = 0; i < count; i++) {
					LogEntry log;
					if ((log = queue.poll()) == null)
						break;

					logs.add(log);
				}
				if(logs.size() > 0) {
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
				}

				lastFlush = System.currentTimeMillis();
				flushing.set( false );
			}
		});
				t.start();
	}

	/**
	 * flush queue completely
	 * @param queue
	 */
	protected void flushQueue( final Queue<LogEntry> queue ) {
		final OohLaLogLogger logger = this.logger;
		if (logger.getDebug()) System.out.println( ">>>>>>Flushing Queue Completely" );
		flushing.set( true );
		Thread t = new Thread( new Runnable() {
//		executorService.execute(new Runnable() {
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


	public ExecutorService getExecutorService() {
		return executorService;
	}


	public void setExecutorService(ExecutorService executorService) {
		this.executorService = executorService;
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

	public int getSubmissionThreadPool() {
		return submissionThreadPool;
	}

	//	public void setSubmissionThreadPool(int submissionThreadPool) {
	//		this.submissionThreadPool = submissionThreadPool;
	//		synchronized ( lock ) {
	//			init();
	//		}
	//	}


	public OohLaLogLogger getLogger() {
		return logger;
	}


	public void setLogger(OohLaLogLogger logger) {
		this.logger = logger;
	}


	public AtomicBoolean getFlushing() {
		return flushing;
	}


	public AtomicBoolean getShutdown() {
		return shutdown;
	}


}
