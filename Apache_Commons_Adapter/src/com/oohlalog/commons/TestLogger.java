package com.oohlalog.commons;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.oohlalog.commons.OohLaLogLogger;

public class TestLogger {

	public static void main(String[] args) throws InterruptedException {
		Log m_log = LogFactory.getLog(TestLogger.class);
		System.out.println("---Beginning---");
		for (int i = 0; i < 151; i++) {
			m_log.error(String.valueOf(i), new Throwable("throwable"));
		}
		System.out.println("end");
	}
}
