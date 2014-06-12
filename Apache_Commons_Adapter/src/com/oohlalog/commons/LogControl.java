package com.oohlalog.commons;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.Gson;

public class LogControl {
	private ExecutorService executorService = null;
	private long timeBuffer;
	private long lastFlush = System.currentTimeMillis();
	private final AtomicBoolean flushing = new AtomicBoolean( false );
	private final AtomicBoolean shutdown = new AtomicBoolean( false );

	// Config options
	private int maxBuffer = 150;
	private long statsInterval = 60000;
	private int submissionThreadPool = 1;

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
		//		this.executorService = Executors.newFixedThreadPool(this.submissionThreadPool);
	}



	/**
	 * Flushes the queue of log entries if the queue is of size greater than buffer threshold.
	 */
	protected void checkThreshold()
	{
		Queue<LogEntry> buffer = this.logger.getQueue();
		if (buffer.size() > maxBuffer && !flushing.get()) {
			if (logger.getDebug()) System.out.println( ">>>Above Threshold" );
			flushQueue(buffer);		}
	}



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
						flushQueue( logger.getQueue(), maxBuffer );
						return;
					}

					// Sleep the thread
					try {
						Thread.sleep(timeBuffer);
					}
					catch ( InterruptedException ie ) {
						// Ignore, and continue
						//						return;
					}
				}
			}
		});

		t.start();

	}

	protected void startStatsTimer() {
		final OohLaLogLogger logger = this.logger;
		Thread t = new Thread( new Runnable() {
			public void run() {
				// If appender closes, let thread die
				while (!shutdown.get() ) {
					if (logger.getStats()) {
						if (logger.getDebug()) System.out.println( ">>Stats Timer" );
						// If timeout, flush queue
						OutputStream os = null;
						BufferedReader rd  = null;
						StringBuilder sb = null;
						String line = null;
						HttpURLConnection con = null;

						try {
							Map<String,Object> payload = new HashMap<String, Object>();
							payload.put("metrics", StatsUtils.getStats(logger));
							String h = logger.getHostName();
							if (h == null ){
								try { h = java.net.InetAddress.getLocalHost().getHostName(); }
								catch (java.net.UnknownHostException uh) {}
							}
							payload.put("host", h);
							String json = new Gson().toJson( payload );

							URL url = new URL( (logger.getSecure() ? "https" : "http"), logger.getHost(), logger.getPort(), logger.getStatsPath()+"?apiKey="+logger.getAuthToken() );

							if (logger.getDebug()) System.out.println( ">>>>>>>>>>>Submitting to: " + url.toString() );
							if (logger.getDebug()) System.out.println( ">>>>>>>>>>>JSON: " + json.toString() );
							con = (HttpURLConnection) url.openConnection();
							con.setDoOutput(true);
							con.setDoInput(true);
							con.setInstanceFollowRedirects(false);
							con.setRequestMethod("POST");
							con.setRequestProperty("Content-Type", "application/json");
							con.setRequestProperty("Content-Length", "" + json.getBytes().length);
							con.setUseCaches(false);

							// Get output stream and write json
							os = con.getOutputStream();
							os.write( json.getBytes() );

							rd  = new BufferedReader(new InputStreamReader(con.getInputStream()));
							sb = new StringBuilder();

							while ((line = rd.readLine()) != null){
								sb.append(line + '\n');
							}
							if (logger.getDebug()) System.out.println( ">>>>>>>>>>>Received: " + sb.toString() );

						} catch (Exception e) {
							if (logger.getDebug()) e.printStackTrace();
							System.out.println("Unable to send stats: "+e.getMessage());
						} finally {
							if ( os != null ) {
								try {
									con.disconnect();
									os.flush();
									os.close();
									con = null;
								}
								catch ( Throwable t ) {
								}
							}
						}
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
		
		if(queue.isEmpty())
			return;
		//		executorService.execute(new Runnable() {
		Thread t = new Thread( new Runnable() {
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
		startFlushTimer();
	}

	/**
	 * flush queue completely
	 * @param queue
	 */
	protected void flushQueue( final Queue<LogEntry> queue ) {
		final OohLaLogLogger logger = this.logger;
		if (logger.getDebug()) System.out.println( ">>>>>>Flushing Queue Completely" );
		//		executorService.execute(new Runnable() {
		Thread t = new Thread( new Runnable() {
			public void run() {
				int size = queue.size();
				System.out.println("size =" + size);
				List<LogEntry> logs = new ArrayList<LogEntry>(size);
				for (int i = 0; i < size; i++) {
					LogEntry log;
					if ((log = queue.poll()) == null)
						break;

					logs.add(log);
				}

				if(logs.size() == 0) {
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
