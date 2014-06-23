package com.oohlalog.commons;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;


/**
 * This class is mostly a wrapper for a Queue.  It's purpose is to provide thread safe access
 * to the buffer holding all of the logs.
 */
public class LogEntryBuffer {
	// Maximum allowed size of the buffer
	private final int maxBuffer;

	// Holds all of the Logs 
	private Queue<LogEntry> deque; 

	
	/**
	 * Constructor that creates a LogEntry Buffer with a maximum size.
	 * 
	 * @param maxBuffer the maximum size of the LogEntry Buffer
	 */
	public LogEntryBuffer(int maxBuffer) {
		this.maxBuffer = maxBuffer;
		deque = new ArrayDeque<LogEntry>(maxBuffer);
	}


	/**
	 * Adds a log record to the buffer.  If the buffer is full, the oldest log in the buffer is discarded
	 * so that there becomes room for the new one.
	 * 
	 * @param le the log record to add to the buffer
	 */
	public synchronized void addLogToBuffer(LogEntry le) {
		Queue<LogEntry> buff = getDeque();
		if (!buff.offer(le)) {

			buff.poll();
			buff.offer(le);
		}
	}

	
	/**
	 * Removes a certain number of logs from the buffer.
	 * 
	 * @param num number of logs to remove form head of queue
	 */
	private synchronized void removeLogsFromBuffer(int num) {
		Queue<LogEntry> buff = getDeque();
		for (int i = 0; i < num; i++) {
			buff.poll();
		}
	}

	
	/**
	 * Flush at most amtToFlush items from the buffer.
	 * 
	 * @param handler the OohLaLogHandler object 
	 * @param maxAmtToFlush the maximum number to flush
	 * @return was the payload sent successfully?
	 */
	protected synchronized boolean flushLogEntryBuffer(final OohLaLogLogger logger, final int maxAmtToFlush ) {		
		// Creates a copy because we don't want to remove logs from deque
		// unless payload is successfully delivered
		int size = size();
		if(size == 0) return false;
		int numToFlush = (maxAmtToFlush < size) ? maxAmtToFlush : size;

		// Creates a copy because we don't want to remove logs from deque
		// unless payload is successfully delivered
		List<LogEntry> logs = new ArrayList<LogEntry>(size);
		logs.addAll(getDeque());
		logs = logs.subList(0, numToFlush);
		

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
	 * 
	 * @return the number of logs in the queue
	 */
	protected synchronized int size() {
		Queue<LogEntry> buff = getDeque();
		return buff.size();
	}

	
	/**
	 * Returns the maximum allowed size of the Log Record Buffer.
	 * 
	 * @return the maximum allowed size of the queue
	 */
	protected int getMaxBuffer() {
		return maxBuffer;
	}

	
	/**
	 * Returns the underlying Queue structure that holds all of the logs.
	 * 
	 * @return the Queue used for holding the logs
	 */
	private synchronized Queue<LogEntry> getDeque() {
		return deque;
	}

}
