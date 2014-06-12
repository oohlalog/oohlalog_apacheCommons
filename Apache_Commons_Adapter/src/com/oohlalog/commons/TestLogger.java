package com.oohlalog.commons;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.oohlalog.commons.OohLaLogLogger;

public class TestLogger {

	public static void main(String[] args) throws InterruptedException {
		Log m_log = LogFactory.getLog(TestLogger.class);
		System.out.println("---Beginning---");
		for (int i = 0; i < 151; i++) {
			m_log.warn("h"+String.valueOf(i), new Throwable("throwable"));
//			Thread.sleep(500);
		}
		System.out.println("end");
	}
}
