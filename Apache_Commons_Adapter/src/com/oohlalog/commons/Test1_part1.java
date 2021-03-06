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
			m_log.trace("TRACE:"+i); // Won't get printed because default level is info
			m_log.debug("DEBUG:"+i); // Won't get printed because default level is info
			m_log.info("INFO:"+i);
			Thread.sleep(20);
			m_log.warn("WARN:"+i);
			Thread.sleep(20);
			m_log.error("ERROR:"+i);
			Thread.sleep(20);
			m_log.fatal("FATAL:"+i);
			Thread.sleep(20);
		}
		
		for (int i = 150; i < 300; i++) {
			m_log.trace("TRACE:"+i, new Throwable("throwable")); // Won't get printed because default level is info
			m_log.debug("DEBUG:"+i, new Throwable("throwable")); // Won't get printed because default level is info
			m_log.info("INFO:"+i, new Throwable("throwable"));
			Thread.sleep(20);
			m_log.warn("WARN:"+i, new Throwable("throwable"));
			Thread.sleep(20);
			m_log.error("ERROR:"+i, new Throwable("throwable"));
			Thread.sleep(20);
			m_log.fatal("FATAL:"+i, new Throwable("throwable"));
			Thread.sleep(20);
		}
	}
}
