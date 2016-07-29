/*
 * (C) Copyright IBM Corp. 2012, 2016
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ibm.jaggr.core.util;

import java.text.Format;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CompletionService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wrappers for signaling methods that wait indefinitely. The wrappers add logging for threads that
 * appear to be stuck. Stuck threads will periodically output warning messages to the logs
 * indicating how long the thread has been waiting. Logging for a stuck thread will be quiesced if
 * the thread remains stuck for an extended period (to avoid overwhelming the logs). If a thread has
 * been reported as stuck, then a warning level message is logged if and when the stuck thread
 * finally awakes.
 * <p>
 * This can help diagnose deadlocks by identifying the threads involved.
 */
public class SignalUtil {
	private static final String sourceClass = SignalUtil.class.getName();
	private static final Logger log = Logger.getLogger(sourceClass);

	public static final long SIGNAL_LOG_INTERVAL_SECONDS = 60;	// 1 minute
	public static final long SIGNAL_LOG_QUIESCE_TIMEOUT_MINUTES = 120;	// 2 hours

	/**
	 * Formats the specified string using the specified arguments. If the argument array contains
	 * more elements than the format string can accommodate, then the additional arguments are
	 * appended to the end of the formatted string.
	 *
	 * @param fmt
	 *            the format string
	 * @param args
	 *            the argument array for the replaceable parameters in the format string
	 * @return the formatted string
	 */
	static String formatMessage(String fmt, Object[] args) {
		MessageFormat formatter = new MessageFormat(fmt);
		Format[] formats = formatter.getFormatsByArgumentIndex();
		StringBuffer msg = new StringBuffer();
		formatter.format(args, msg, null);
		if (args.length > formats.length) {
			// We have extra arguements that were not included in the format string.
			// Append them to the result
			for (int i = formats.length; i < args.length; i++) {
				msg.append(i == formats.length ? ": " : ", ") //$NON-NLS-1$ //$NON-NLS-2$
				   .append(args[i].toString());
			}
		}
		return msg.toString();
	}

	/**
	 * Logs a warning message. If the elapsed time is greater than
	 * {@link #SIGNAL_LOG_QUIESCE_TIMEOUT_MINUTES} then the log message will indicate that wait
	 * logging for the thread is being quiesced, and a value of true is returned. Otherwise, false
	 * is returned.
	 * <p>
	 * This is a convenience method to call
	 * {@link #logWaiting(Logger, String, String, Object, long, Object...)} with the default logger.
	 *
	 * @param callerClass
	 *            the class name of the caller
	 * @param callerMethod
	 *            the method name of the caller
	 * @param waitObj
	 *            the object that is being waited on
	 * @param start
	 *            the time that the wait began
	 * @param extraArgs
	 *            caller provided extra arguments
	 * @return true if the elapsed time is greater than {@link #SIGNAL_LOG_QUIESCE_TIMEOUT_MINUTES}
	 */
	static boolean logWaiting(String callerClass, String callerMethod, Object waitObj, long start, Object... extraArgs) {
		return logWaiting(log, callerClass, callerMethod, waitObj, start, extraArgs);
	}

	/**
	 * Logs a warning message. If the elapsed time is greater than
	 * {@link #SIGNAL_LOG_QUIESCE_TIMEOUT_MINUTES} then the log message will indicate that wait
	 * logging for the thread is being quiesced, and a value of true is returned. Otherwise, false
	 * is returned.
	 *
	 * @param log
	 *            the logger (for unit testing)
	 * @param callerClass
	 *            the class name of the caller
	 * @param callerMethod
	 *            the method name of the caller
	 * @param waitObj
	 *            the object that is being waited on
	 * @param start
	 *            the time that the wait began
	 * @param extraArgs
	 *            caller provided extra arguments
	 * @return true if the elapsed time is greater than {@link #SIGNAL_LOG_QUIESCE_TIMEOUT_MINUTES}
	 */
	static boolean logWaiting(Logger log, String callerClass, String callerMethod, Object waitObj, long start, Object... extraArgs) {
		long elapsed = (System.currentTimeMillis() - start)/1000;
		boolean quiesced = false;
		if (elapsed <= SIGNAL_LOG_QUIESCE_TIMEOUT_MINUTES * 60) {
			ArrayList<Object> args = new ArrayList<Object>();
			args.add(Thread.currentThread().getId());
			args.add(elapsed);
			args.add(waitObj);
			if (extraArgs != null) {
				args.addAll(Arrays.asList(extraArgs));
			}
			String msg = SignalUtil.formatMessage(Messages.SignalUtil_0, args.toArray(new Object[args.size()]));
			log.logp(Level.WARNING, callerClass, callerMethod, msg.toString());
		} else {
			if (!quiesced) {
				quiesced = true;
				log.logp(Level.WARNING, callerClass, callerMethod,
						MessageFormat.format(
								Messages.SignalUtil_1,
								new Object[]{Thread.currentThread().getId(), elapsed, waitObj}
						)
				);
			}
		}
		return quiesced;
	}

	/**
	 * Logs a warning level message indicating that a thread which has previously been reported
	 * as stuck is now resuming.
	 *
	 * @param callerClass
	 *            the class name of the caller
	 * @param callerMethod
	 *            the method name of the caller
	 * @param waitObj
	 *            the object that is being waited on
	 * @param start
	 *            the time that the wait began
	 */
	static void logResuming(String callerClass, String callerMethod, Object waitObj, long start) {
		long elapsed = (System.currentTimeMillis() - start)/1000;
		log.logp(Level.WARNING, callerClass, callerMethod,
				Messages.SignalUtil_2, new Object[]{Thread.currentThread().getId(), elapsed, waitObj});
	}

	/**
	 * Implements the same semantics as {@link Lock#lock()}, except that the wait will periodically
	 * time out within this method to log a warning message that the thread appears stuck. This
	 * cycle will be repeated until the lock is obtained. If the thread has waited for an extended
	 * period, then wait logging is quiesced to avoid overwhelming the logs. If and when a thread
	 * that has been reported as being stuck finally does obtain the lock, then a warning level
	 * message is logged to indicate that the thread is resuming.
	 *
	 * @param lock
	 *            the Lock object to wait on
	 * @param callerClass
	 *            the caller class
	 * @param callerMethod
	 *            the caller method
	 * @param args
	 *            extra arguments to be appended to the log message
	 */
	public static void lock(Lock lock, String callerClass, String callerMethod, Object... args) {
		final String sourceMethod = "lock"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[]{Thread.currentThread(), lock, callerClass, callerMethod, args});
		}
		long start = System.currentTimeMillis();
		boolean quiesced = false, logged = false;
		try {
			while (!lock.tryLock(SIGNAL_LOG_INTERVAL_SECONDS, TimeUnit.SECONDS)) {
				if (!quiesced) {
					quiesced = logWaiting(callerClass, callerMethod, lock, start, args);
					logged = true;
				}
			}
		} catch (InterruptedException ex) {
			// InterruptedException is not thrown by Lock.lock() so convert to a
			// RuntimeException.
			throw new RuntimeException(ex);
		}
		if (logged) {
			logResuming(callerClass, callerMethod, lock, start);
		}
		if (isTraceLogging) {
			log.exiting(sourceClass, sourceMethod, Arrays.asList(Thread.currentThread(), lock, callerClass, callerMethod));
		}
		return;
	}

	/**
	 * Implements the same semantics as {@link CompletionService#take()}, except that the wait will
	 * periodically time out within this method to log a warning message that the thread appears
	 * stuck. This cycle will be repeated until a {@link Future} is obtained. If the thread has
	 * waited for an extended period, then wait logging is quiesced to avoid overwhelming the logs.
	 * If and when a thread that has been reported as being stuck finally does obtain a
	 * {@link Future}, then a warning level message is logged to indicate that the thread is
	 * resuming.
	 *
	 * @param <T>
	 *            Generic type of the Future result
	 * @param cs
	 *            the CompletionService to wait on
	 * @param callerClass
	 *            the caller class
	 * @param callerMethod
	 *            the caller method
	 * @param args
	 *            extra arguments to be appended to the log message
	 * @return the Future<T> obtained from the CompletionService
	 * @throws InterruptedException
	 */
	public static <T> Future<T> take(CompletionService<T> cs, String callerClass, String callerMethod, Object... args)
	throws InterruptedException {
		final String sourceMethod = "take"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[]{Thread.currentThread(), cs, callerClass, callerMethod, args});
		}
		long start = System.currentTimeMillis();
		boolean quiesced = false, logged = false;
		Future<T> future = null;
		while ((future = cs.poll(SIGNAL_LOG_INTERVAL_SECONDS, TimeUnit.SECONDS)) == null) {
			if (!quiesced) {
				quiesced = logWaiting(callerClass, callerMethod, cs, start, args);
				logged = true;
			}
		}
		if (logged) {
			logResuming(callerClass, callerMethod, cs, start);
		}
		if (isTraceLogging) {
			log.exiting(sourceClass, sourceMethod, Arrays.asList(Thread.currentThread(), cs, callerClass, callerMethod, future));
		}
		return future;
	}

	/**
	 * Implements the same semantics as {@link CountDownLatch#await()}, except that the wait will
	 * periodically time out within this method to log a warning message that the thread appears
	 * stuck. This cycle will be repeated until the latch has counted down to zero. If the thread
	 * has waited for an extended period, then wait logging is quiesced to avoid overwhelming the
	 * logs. If and when a thread that has been reported as being stuck and the latch is finally
	 * counted down to zero, then a warning level message is logged to indicate that the thread is
	 * resuming.
	 *
	 * @param latch
	 *            the CountDownLatch object to wait on
	 * @param callerClass
	 *            the caller class
	 * @param callerMethod
	 *            the caller method
	 * @param args
	 *            extra arguments to be appended to the log message
	 * @throws InterruptedException
	 */
	public static void await(CountDownLatch latch, String callerClass, String callerMethod, Object... args)
	throws InterruptedException {
		final String sourceMethod = "await"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[]{Thread.currentThread(), latch, callerClass, callerMethod, args});
		}
		long start = System.currentTimeMillis();
		boolean quiesced = false, logged = false;
		while (!latch.await(SignalUtil.SIGNAL_LOG_INTERVAL_SECONDS, TimeUnit.SECONDS)) {
			if (!quiesced) {
				quiesced = logWaiting(callerClass, callerMethod, latch, start, args);
				logged = true;
			}
		}
		if (logged) {
			logResuming(callerClass, callerMethod, latch, start);
		}
		if (isTraceLogging) {
			log.exiting(sourceClass, sourceMethod, Arrays.asList(Thread.currentThread(), latch, callerClass, callerMethod));
		}
	}

	/**
	 * Implements the same semantics as {@link Future#get()}, except that the wait will periodically
	 * time out within this method to log a warning message that the thread appears stuck. This
	 * cycle will be repeated until the result is available. If the thread has waited for an extended
	 * period, then wait logging is quiesced to avoid overwhelming the logs. If and when a thread
	 * that has been reported as being stuck finally does obtain the result, then a warning level
	 * message is logged to indicate that the thread is resuming.
	 *
	 * @param <T>
	 *            Generic for the type of the result
	 * @param future
	 *            the Future to obtain the result from
	 * @param callerClass
	 *            the caller class
	 * @param callerMethod
	 *            the caller method
	 * @param args
	 *            extra arguments to be appended to the log message
	 * @return the result from the future
	 * @throws InterruptedException
	 * @throws ExecutionException
	 */
	public static <T> T get(Future<T> future, String callerClass, String callerMethod, Object... args)
	throws InterruptedException, ExecutionException {
		final String sourceMethod = "get"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[]{Thread.currentThread(), future, callerClass, callerMethod, args});
		}
		T result = null;
		long start = System.currentTimeMillis();
		boolean quiesced = false, logged = false;
		while (true) {
			try {
				result = future.get(SignalUtil.SIGNAL_LOG_INTERVAL_SECONDS, TimeUnit.SECONDS);
				break;
			} catch (TimeoutException tex) {
				if (!quiesced) {
					quiesced = logWaiting(callerClass, callerMethod, future, start, args);
					logged = true;
				}
			}
		}
		if (logged) {
			logResuming(callerClass, callerMethod, future, start);
		}
		if (isTraceLogging) {
			log.exiting(sourceClass, sourceMethod, Arrays.asList(Thread.currentThread(), future, callerClass, callerMethod, result));
		}
		return result;
	}

	/**
	 * Implements the same semantics as {@link Semaphore#acquire()}, except that the wait will
	 * periodically time out within this method to log a warning message that the thread appears
	 * stuck. This cycle will be repeated until the semaphore is acquired. If the thread has waited
	 * for an extended period, then wait logging is quiesced to avoid overwhelming the logs. If and
	 * when a thread that has been reported as being stuck and the semaphore is finally acquired,
	 * then a warning level message is logged to indicate that the thread is resuming.
	 *
	 * @param sem
	 *            the semaphore to wait on
	 * @param callerClass
	 *            the caller class
	 * @param callerMethod
	 *            the caller method
	 * @param args
	 *            extra arguments to be appended to the log message
	 * @throws InterruptedException
	 */
	public static void aquire(Semaphore sem, String callerClass, String callerMethod, Object... args)
	throws InterruptedException {
		final String sourceMethod = "aquire"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[]{Thread.currentThread(), sem, callerClass, callerMethod, args});
		}
		long start = System.currentTimeMillis();
		boolean quiesced = false, logged = false;
		while (!sem.tryAcquire(SignalUtil.SIGNAL_LOG_INTERVAL_SECONDS, TimeUnit.SECONDS)) {
			if (!quiesced) {
				quiesced = logWaiting(callerClass, callerMethod, sem, start, args);
				logged = true;
			}
		}
		if (logged) {
			logResuming(callerClass, callerMethod, sem, start);
		}
		if (isTraceLogging) {
			log.exiting(sourceClass, sourceMethod, Arrays.asList(Thread.currentThread(), sem, callerClass, callerMethod));
		}
	}
}
