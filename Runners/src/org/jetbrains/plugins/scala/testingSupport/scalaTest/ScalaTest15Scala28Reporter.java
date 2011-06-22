package org.jetbrains.plugins.scala.testingSupport.scalaTest;

import org.scalatest.Reporter;
import org.scalatest.events.*;
import scala.Option;
import scala.Some;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;

import static org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil.*;

/**
 * @author Alexander Podkhalyuzin
 */
public class ScalaTest15Scala28Reporter implements Reporter {
  // IDEA 107.199 gives this error when parsing a Message service message.
  //  Caused by: java.lang.RuntimeException: java.lang.NoClassDefFoundError: jetbrains/buildServer/messages/Status
  //        at jetbrains.buildServer.messages.serviceMessages.ServiceMessage.doParse(ServiceMessage.java:380)
  // TODO enable output after http://youtrack.jetbrains.net/issue/IDEA-71145
  public static final boolean OUTPUT_STATUS_MESSAGE = false;
  
  private String getStackTraceString(Throwable throwable) {
    StringWriter writer = new StringWriter();
    throwable.printStackTrace(new PrintWriter(writer));
    return writer.getBuffer().toString();
  }

  public void apply(Event event) {
    if (event instanceof RunStarting) {
      RunStarting r = (RunStarting) event;
      int testCount = r.testCount();
      System.out.println("##teamcity[testCount count='" + testCount + "']");
    } else if (event instanceof TestStarting) {
      String testName = ((TestStarting) event).testName();
      System.out.println("\n##teamcity[testStarted name='" + escapeString(testName) +
            "' captureStandardOutput='true']");
    } else if (event instanceof TestSucceeded) {
      Option<Long> durationOption = ((TestSucceeded) event).duration();
      long duration = 0;
      if (durationOption instanceof Some) {
        duration = durationOption.get().longValue();
      }
      String testName = ((TestSucceeded) event).testName();
      System.out.println("\n##teamcity[testFinished name='" + escapeString(testName) +
          "' duration='"+ duration +"']");
    } else if (event instanceof TestFailed) {
      boolean error = true;
      Option<Throwable> throwableOption = ((TestFailed) event).throwable();
      String detail = "";
      if (throwableOption instanceof Some) {
        if (throwableOption.get() instanceof AssertionError) error = false;
        detail = getStackTraceString(throwableOption.get());
      }
      Option<Long> durationOption = ((TestFailed) event).duration();
      long duration = 0;
      if (durationOption instanceof Some) {
        duration = durationOption.get().longValue();
      }
      String testName = ((TestFailed) event).testName();
      String message = ((TestFailed) event).message();
      long timeStamp = event.timeStamp();
      String res = "\n##teamcity[testFailed name='" + escapeString(testName) + "' message='" + escapeString(message) +
          "' details='" + escapeString(detail) + "'";
      if (error) res += "error = '" + error + "'";
      res += "timestamp='" + escapeString(formatTimestamp(new Date(timeStamp))) + "']";
      System.out.println(res);
      System.out.println("\n##teamcity[testFinished name='" + escapeString(testName) +
          "' duration='" + duration +"']");
    } else if (event instanceof TestIgnored) {
      String testName = ((TestIgnored) event).testName();
      System.out.println("\n##teamcity[testIgnored name='" + escapeString(testName) + "' message='" +
          escapeString("") + "']");
    } else if (event instanceof TestPending) {

    } else if (event instanceof SuiteStarting) {
      String suiteName = ((SuiteStarting) event).suiteName();
      System.out.println("\n##teamcity[testSuiteStarted name='" + escapeString(suiteName) + "']");
    } else if (event instanceof SuiteCompleted) {
      String suiteName = ((SuiteCompleted) event).suiteName();
      System.out.println("\n##teamcity[testSuiteFinished name='" + escapeString(suiteName) + "']");
    } else if (event instanceof SuiteAborted) {
      String message = ((SuiteAborted) event).message();
      Option<Throwable> throwableOption = ((SuiteAborted) event).throwable();
      String throwableString = "";
      if (throwableOption instanceof Some) {
        throwableString = " errorDetails='" + escapeString(getStackTraceString(throwableOption.get())) + "'";
      }
      String statusText = "ERROR";
      if (OUTPUT_STATUS_MESSAGE) {
        System.out.println("\n##teamcity[message text='" + escapeString(message) + "' status='" + statusText + "'" +
            throwableString + "]");
      }
    } else if (event instanceof InfoProvided) {
      String message = ((InfoProvided) event).message();
      if (OUTPUT_STATUS_MESSAGE) {
        System.out.println("\n##teamcity[message text='" + escapeString(message + "\n") + "' status='WARNING'" + "]");
      }
    } else if (event instanceof RunStopped) {

    } else if (event instanceof RunAborted) {
      String message = ((RunAborted) event).message();
      Option<Throwable> throwableOption = ((RunAborted) event).throwable();
      String throwableString = "";
      if (throwableOption instanceof Some) {
        throwableString = " errorDetails='" + escapeString(getStackTraceString(throwableOption.get())) + "'";
      }
      if (OUTPUT_STATUS_MESSAGE) {
        System.out.println("\n##teamcity[message text='" + escapeString(message) + "' status='ERROR'" +
            throwableString + "]");
      }
    } else if (event instanceof RunCompleted) {

    }
  }
}