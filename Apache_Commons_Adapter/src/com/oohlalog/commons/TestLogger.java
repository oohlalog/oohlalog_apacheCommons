package com.oohlalog.commons;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class TestLogger {

	public static void main(String[] args) throws InterruptedException {
		Log m_log = LogFactory.getLog(TestLogger.class);
		System.out.println("---Beginning---");
		for (int i = 0; i < 151; i++) {
//			m_log.warn("i"+String.valueOf(i), new Throwable("throwable"));
			m_log.info("i"+String.valueOf(i));
		}
		Thread.sleep(2000);
		for (int i = 151; i < 352; i++) {
			m_log.error("z"+String.valueOf(i));
		}
		System.out.println("end");
		m_log = null;
	}
}
