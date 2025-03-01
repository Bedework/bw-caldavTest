/* ********************************************************************
    Licensed to Jasig under one or more contributor license
    agreements. See the NOTICE file distributed with this work
    for additional information regarding copyright ownership.
    Jasig licenses this file to you under the Apache License,
    Version 2.0 (the "License"); you may not use this file
    except in compliance with the License. You may obtain a
    copy of the License at:

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on
    an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied. See the License for the
    specific language governing permissions and limitations
    under the License.
*/
package org.bedework.caldav.test;

import org.bedework.util.args.Args;
import org.bedework.util.http.HttpUtil;
import org.bedework.util.logging.BwLogger;
import org.bedework.base.ToString;
import org.bedework.util.misc.Util;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.InputStream;
import java.io.LineNumberReader;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.TreeSet;

import static org.bedework.util.http.HttpUtil.findMethod;
import static org.bedework.util.http.HttpUtil.setContent;

/** Try to fire requests at this thing.
 *
 * <p>Also occasionally used to test other components
 */
public class TestCalDav {
  private static final BwLogger logger =
          new BwLogger().setLoggedClass(TestCalDav.class);

  private static String host = "localhost";

  private static int port = 8080;

  private static boolean secure = false;

  private static String user = "douglm";
  private static String pw = "bedework";

  private static String urlPrefix;

  private static boolean list = false;

  /* Name of file containing list of test names - located in dir */
  private static String testListName;

  private static String dirName;

  private static String resourcedirName;
  private static Path resourcedirPath;

  private static String fileName;

  private static CloseableHttpClient cio;

  private static class TestFilter implements FileFilter {
    @Override
    public boolean accept(final File f) {
      return f.getName().endsWith(".test");
    }
  }

  private static class TestResult {
    String testName;
    boolean ok;
    int responseCode;
    boolean exception;
    String reason;

    TestResult(final String testName, final boolean ok,
               final int responseCode, final boolean exception,
               final String reason) {
      this.testName = testName;
      this.ok = ok;
      this.responseCode = responseCode;
      this.exception = exception;
      this.reason = reason;
    }

    @Override
    public String toString() {
      return new ToString(this)
              .append("testName", testName)
              .append(ok)
              .append(responseCode)
              .append("Exc", exception)
              .append(reason)
              .toString();
    }
  }

  private static final ArrayList<TestResult> results = new ArrayList<>();

  /** Main method
   *
   * @param args runtime arguments
   */
  public static void main(final String[] args) {
    try {
      if (!processArgs(args)) {
        return;
      }

      String scheme = "http";
      if (secure) {
    	  scheme = "http";
      }

      final HttpClientBuilder clb = HttpClients.custom();

      if (user != null) {
        final CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT),
                new UsernamePasswordCredentials(user,
                                                pw));

        clb.setDefaultCredentialsProvider(credsProvider);
      }

      cio = clb.build();

      final File dir = new File(dirName);

      if (!dir.isDirectory()) {
        info(dirName + " is not a directory.");
        usage();
        return;
      }

      if (fileName == null) {
        // Either use the test list or the sorted directory contents.

        final ArrayList<File> tests = new ArrayList<>();

        if (testListName != null) {
          final File testList = new File(dirName + "/" + testListName);

          try (final FileReader frdr = new FileReader(testList)) {
            final LineNumberReader lnr = new LineNumberReader(frdr);

            do {
              String ln = lnr.readLine();

              if (ln == null) {
                break;
              }

              ln = ln.trim();

              if (ln.startsWith("#") || (ln.isEmpty())) {
                continue;
              }

              tests.add(new File(dirName + "/" + ln + ".test"));
            } while (true);
          }
        } else {
          final File[] dirfiles = dir.listFiles(new TestFilter());
          if (dirfiles == null) {
            error("No test files in " + dirName);
          } else {
            final TreeSet<File> ts = new TreeSet<>();

            Collections.addAll(ts, dirfiles);

            tests.addAll(ts);
          }
        }

        for (final File testfile: tests) {
          final String fname = testfile.getName();
          final String tname = fname.substring(0, fname.length() - 5);

          final Req r = new Req(user, pw, urlPrefix,
                                testfile.getCanonicalPath(),
                                resourcedirPath);

          info("Test " + tname + ": " + r.description);

          if (!list) {
            runTest(r, tname);
          }
        }
      } else {
        final Req r = new Req(user, pw, urlPrefix,
                              dirName + "/" + fileName + ".test",
                              resourcedirPath);

        info("Test " + fileName + ": " + r.description);
        if (!list) {
          runTest(r, fileName);
        }
      }
    } catch (final Throwable t) {
      info("********************************************");
      info("********************************************");
      t.printStackTrace();
      info("********************************************");
      info("********************************************");
    }

    info("--------------------------------------------------------------");
    int num = 0;
    int ok = 0;
    for (final TestResult tr: results) {
      info(tr.toString());
      num++;
      if (tr.ok) {
        ok++;
      }
    }
    info("----------------------------------------------------------");
    info("Ran " + num + " tests with " + ok + " successful");
    info("--------------------------------------------------------------");
  }

  static boolean processArgs(final String[] args) throws Throwable {
    if (args == null) {
      return false;
    }

    final Args pargs = new Args(args);

    while (pargs.more()) {
      if (pargs.ifMatch("-list")) {
        list = true;
        continue;
      }

      if (pargs.ifMatch("-dir")) {
        dirName = pargs.next();
        continue;
      }

      if (pargs.ifMatch("-resourcedir")) {
        resourcedirName = pargs.next();
        continue;
      }

      if (pargs.ifMatch("-urlPrefix")) {
        urlPrefix = pargs.next();
        continue;
      }

      if (pargs.ifMatch("-host")) {
        host = pargs.next();
        continue;
      }

      if (pargs.ifMatch("-port")) {
        port = Integer.parseInt(pargs.next());
        continue;
      }

      if (pargs.ifMatch("-secure")) {
        secure = true;
        continue;
      }

      if (pargs.ifMatch("-user")) {
        user = pargs.next();
        continue;
      }

      if (pargs.ifMatch("-pw")) {
        pw = pargs.next();
        continue;
      }

      if (pargs.ifMatch("-testlist")) {
        testListName = pargs.next();
        continue;
      }

      if ((fileName == null) && pargs.ifMatch("-test")) {
        fileName = pargs.next();
        continue;
      }

      info("Illegal argument: " +
                                 pargs.current());
      usage();
      return false;
    }

    if (resourcedirName == null) {
      resourcedirName = dirName;
    }

    resourcedirPath = Paths.get(resourcedirName);
    if (!resourcedirPath.toFile().isDirectory()) {
      info(resourcedirName + " is not a directory");
      return false;
    }

    return true;
  }

  static boolean argpar(final String n, final String[] args, final int i) throws Exception {
    if (!args[i].equals(n)) {
      return false;
    }

    if ((i + 1) == args.length) {
      throw new Exception("Invalid args");
    }
    return true;
  }

  static void usage() {
    info("Usage:");
    info("args   -debug");
    info("       -ndebug");
    info("       -host hostname");
    info("       -port int");
    info("       -user username");
    info("       -pw pwstring");
    info("       -urlPrefix string");
    info("            Prefix for relative urls");
    info("       -dir dirname");
    info("            set location of tests");
    info("       -list");
    info("            Just list test file[s]");
    info("       -test testname");
    info("            run given test [in given directory]");
    info();
    info("For example");
    info("   -dir mytestdir");
    info("             Run all the tests in given directory");
    info("   -dir mytestdir -testlist mylist");
    info("             Run all the test in given directory named in the");
    info("             file mylist in that directory.");
  }

  private static boolean runTest(final Req r, final String tname) {
    try {
      int respCode;

      /*
      if (r.user != null) {
        HttpClientContext context = HttpClientContext.create();
        final CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT),
                new UsernamePasswordCredentials(r.user,
                                                r.pw));
        context.setCredentialsProvider(credsProvider);
      }
*/
      final String scheme;

      if (secure) {
        scheme = "https";
      } else {
        scheme = "http";
      }

      final URI uri = new URIBuilder()
              .setScheme(scheme)
              .setHost(host)
              .setPort(port)
              .setPath(r.getPrefixedUrl())
              .build();

      final HttpRequestBase req = findMethod(r.getMethod(), uri);

      if (req == null) {
        info("No method " + r.getMethod());
        return false;
      }

      if (!Util.isEmpty(r.getHdrs())) {
        for (final Header hdr: r.getHdrs()) {
          req.addHeader(hdr);
        }
      }

      info("About to exec " + req.getMethod() + " on " + uri);
      setContent(req,
                 r.getContentBytes(),
                 r.getContentType());

      try (final CloseableHttpResponse resp = cio.execute(req)) {
        final HttpEntity ent = resp.getEntity();

        if (ent != null) {
          final InputStream in = ent.getContent();

          if (in != null) {
            readContent(in, ent.getContentLength(),
                        ContentType.getOrDefault(ent).getCharset()
                                   .toString());
          }
        }

        final int status = HttpUtil.getStatus(resp);

        final int expected = r.getExpectedResponse();

        final boolean ok = (expected < 0) || (expected == status);

        results.add(new TestResult(tname, ok, status, false, null));

        return ok;
      }
    } catch (final Throwable t) {
      results.add(new TestResult(tname, false, 0, true, t.getMessage()));
      t.printStackTrace();
      return false;
    }
  }

  static void readContent(final InputStream in, final long expectedLen,
                          final String charset) throws Throwable {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    int len = 0;

    if (logger.debug()) {
      info("Read content - expected=" + expectedLen);
    }

    boolean hadLf = false;
    boolean hadCr = false;

    while ((expectedLen < 0) || (len < expectedLen)) {
      final int ich = in.read();
      if (ich < 0) {
        break;
      }

      len++;

      if (ich == '\n') {
        if (hadLf) {
          info();
          hadLf = false;
          hadCr = false;
        } else {
          hadLf = true;
        }
      } else if (ich == '\r') {
        if (hadCr) {
          info();
          hadLf = false;
          hadCr = false;
        } else {
          hadCr = true;
        }
      } else if (hadCr || hadLf) {
        hadLf = false;
        hadCr = false;

        if (baos.size() > 0) {
          info(baos.toString(charset));
        }

        baos.reset();
        baos.write(ich);
      } else {
        baos.write(ich);
      }
    }

    if (baos.size() > 0) {
      info(baos.toString(charset));
    }
  }

  private static void error(final String msg) {
    System.err.println(msg);
  }

  private static void info() {
    System.out.println();
  }

  private static void info(final String msg) {
    System.out.println(msg);
  }

  /*
  private static class CaldavAuthenticator extends Authenticator {
    private String user;
    private char[] pw;

    CaldavAuthenticator(String user, String pw) {
      this.user = user;
      this.pw = pw.toCharArray();
    }

    protected PasswordAuthentication getPasswordAuthentication() {
      return new PasswordAuthentication(user, pw);
    }
  }

  private static void calInfo(Calendar cal) throws Throwable {
    ComponentList clist = cal.getComponents();

    Iterator it = clist.iterator();

    while (it.hasNext()) {
      Object o = it.next();

      msg("Got component " + o.getClass().getName());

      if (o instanceof VEvent) {
        VEvent ev = (VEvent)o;

        eventInfo(ev);
        / *
      } else if (o instanceof VTimeZone) {
        VTimeZone vtz = (VTimeZone)o;

        debugMsg("Got timezone: \n" + vtz.toString());
        * /
      }
    }
  }

  private static void eventInfo(VEvent ev) throws Throwable {
    String desc = IcalUtil.getPropertyVal(ev, Property.DESCRIPTION);

    if (desc != null) {
      msg(desc);
      msg("");
    }

    Date start = ev.getStartDate().getDate();
    Date until = VEventUtil.getLatestRecurrenceDate(ev, true);
    if (until == null) {
      msg("Unlimited recurrence");
    } else {
      msg("Latest recurrence at " + until);

      PeriodList pl = ev.getConsumedTime(start, until);

      Iterator it = pl.iterator();

      while (it.hasNext()) {
        Period p = (Period)it.next();

        msg("period - start=" + p.getStart() + " end=" + p.getEnd());
      }
    }
  }

  /* * Convert the given string representation of an Icalendar object to a
   * Calendar object
   *
   * @param rdr
   * @return Calendar
   * @throws Throwable
   * /
  public static Calendar getCalendar(Reader rdr) throws Throwable {
    System.setProperty("ical4j.unfolding.relaxed", "true");
    CalendarBuilder bldr = new CalendarBuilder(new CalendarParserImpl());

    return bldr.build(new UnfoldingReader(rdr));
  }

  private static void msg(String msg) {
    System.out.println(msg);
  }*/
}

