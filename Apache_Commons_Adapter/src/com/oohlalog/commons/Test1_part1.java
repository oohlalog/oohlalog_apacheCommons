package com.oohlalog.commons;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;



/**
 * TODO: Description
 *
 */
public class Test1_part1 {

	public static void main(String[] args) throws InterruptedException {
		System.out.println("---Beginning---");
		Log m_log = LogFactory.getLog("TestLogger");
		
		for (int i = 0; i < 150; i++) {
			m_log.trace("Test Trace:"+i);
			m_log.debug("Test Debug:"+i);
			m_log.info("Test Info:"+i);
			m_log.warn("Test Warn:"+i);
			m_log.error("Test Error:"+i);
			m_log.fatal("Test Fatal:"+i);
		}
		
		for (int i = 0; i < 150; i++) {
			m_log.trace("Test Trace with throwable:"+i, new Throwable("throwable"));
			m_log.debug("Test Debug with throwable:"+i, new Throwable("throwable"));
			m_log.info("Test Info with throwable:"+i, new Throwable("throwable"));
			m_log.warn("Test Warn with throwable:"+i, new Throwable("throwable"));
			m_log.error("Test Error with throwable:"+i, new Throwable("throwable"));
			m_log.fatal("Test Fatal with throwable:"+i, new Throwable("throwable"));
		}
	}
}
