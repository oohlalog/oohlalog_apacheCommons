package com.oohlalog.commons;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;



/**
 * TODO: Make this a legit test.
 *
 */
public class TestLogger {

	public static void main(String[] args) throws InterruptedException {
		Log m_log = LogFactory.getLog(TestLogger.class);
		System.out.println("---Beginning---");
		for (int i = 0; i < 151; i++) {
			m_log.warn(String.valueOf(i), new Throwable("throwable"));
		}
		for (int i = 151; i < 302; i++) {
			m_log.error(String.valueOf(i));
		}
		System.out.println("end");
	}
}
