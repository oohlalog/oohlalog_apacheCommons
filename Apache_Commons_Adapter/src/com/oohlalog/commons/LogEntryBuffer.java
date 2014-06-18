package com.oohlalog.commons;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;


/**
 * This class is mostly a wrapper for a BlockingDeque.  It's purpose is to provide thread safe access
 * to the buffer holding all of the logs.
 *
 */
public class LogEntryBuffer {
	// Maximum allowed size of the buffer
	private final int maxBuffer;

	// Holds all of the Logs 
	private BlockingDeque<LogEntry> deque; 

	public LogEntryBuffer(int maxBuffer) {
		this.maxBuffer = maxBuffer;
		deque = new LinkedBlockingDeque<LogEntry>(maxBuffer);
	}


	/**
	 * Adds a log entry to the buffer.  If the buffer is full, the oldest log in the buffer is discarded
	 * so that there becomes room for the new one.
	 * @param le
	 */
	public synchronized void addLogToBuffer(LogEntry le) {
		BlockingDeque<LogEntry> buff = getDeque();
		if (!buff.offer(le)) {

			buff.poll();
			buff.offer(le);
		}
	}

	
	/**
	 * Removes a certain number of logs from the buffer.
	 * @param num
	 */
	private synchronized void removeLogsFromBuffer(int num) {
		BlockingDeque<LogEntry> buff = getDeque();
		for (int i = 0; i < num; i++) {
			buff.poll();
		}
	}

	
	/**
	 * Flush at most amtToFlush items from the buffer.
	 */
	protected synchronized boolean flushLogEntryBuffer(final OohLaLogLogger logger, final int amtToFlush ) {		
		// Creates a copy because we don't want to remove logs from deque
		// unless payload is successfully delivered
		int size = size();
		System.out.println("size: " + size);
		List<LogEntry> logs = new ArrayList<LogEntry>(size);
		logs.addAll(getDeque());

		if(size == 0) return false;

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

		boolean success = Payload.send( pl );
		// Payload successfully delivered so we can remove the logs that we already sent.
		if (success) removeLogsFromBuffer(size);

		return success;
	}

	
	/**
	 * Returns the number of logs in the buffer.
	 * @return
	 */
	protected synchronized int size() {
		BlockingDeque<LogEntry> buff = getDeque();
		return buff.size();
	}

	
	/**
	 * Returns the maximum allowed size of the Log Entry Buffer.
	 * @return
	 */
	protected int getMaxBuffer() {
		return maxBuffer;
	}

	
	/**
	 * Returns the underlying BlockingDeque structure that holds all of the logs.
	 * @return
	 */
	private synchronized BlockingDeque<LogEntry> getDeque() {
		return deque;
	}

}
