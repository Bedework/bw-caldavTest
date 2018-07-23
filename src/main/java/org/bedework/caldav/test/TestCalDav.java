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

import org.bedework.util.http.HttpUtil;
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
import java.util.ArrayList;
import java.util.TreeSet;

import static org.bedework.util.http.HttpUtil.findMethod;
import static org.bedework.util.http.HttpUtil.setContent;

/** Try to fire requests at this thing.
 *
 * <p>Also occasionally used to test other components
 */
public class TestCalDav {
  private static boolean debug = true;

  private static String host = "localhost";

  private static int port = 8080;

  private static boolean secure = false;

  private static String user = "douglm";
  private static String pw = "bedework";

  private static String urlPrefix;

  private static String fileRepository = "../../../../tests/caldavTestData/eg/";

  private static boolean list = false;

  /* Name of file containing list of test names - located in dir */
  private static String testListName;

  private static String dirName = fileRepository;

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

    TestResult(final String testName, final boolean ok, final int responseCode, final boolean exception, final String reason) {
      this.testName = testName;
      this.ok = ok;
      this.responseCode = responseCode;
      this.exception = exception;
      this.reason = reason;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      pad(sb, testName, 10);
      sb.append(" ");
      pad(sb, String.valueOf(ok), 4);
      sb.append(" ");
      pad(sb, String.valueOf(responseCode), 4);
      sb.append(" Exc=");
      pad(sb, String.valueOf(exception), 4);
      if (reason != null) {
        sb.append(" ");
        sb.append(reason);
      }

      return sb.toString();
    }

    private static final String padding = "                           ";

    private void pad(final StringBuilder sb, final String val, final int padlen) {
      int len = val.length();

      if (len <= padlen) {
        sb.append(padding.substring(0, padlen - len));
      }

      sb.append(val);
    }
  }

  private static ArrayList<TestResult> results = new ArrayList<TestResult>();

  /** Main method
   *
   * @param args
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

      File dir = new File(dirName);

      if (!dir.isDirectory()) {
        System.out.println(dirName + "is not a directory.");
        usage();
        return;
      }

      if (fileName == null) {
        // Either use the test list or the sorted directory contents.

        ArrayList<File> tests = new ArrayList<File>();

        if (testListName != null) {
          File testList = new File(dirName + "/" + testListName);
          FileReader frdr = null;

          try {
            frdr = new FileReader(testList);
            LineNumberReader lnr = new LineNumberReader(frdr);

            do {
              String ln = lnr.readLine();

              if (ln == null) {
                break;
              }

              ln = ln.trim();

              if (ln.startsWith("#") || (ln.length() == 0)) {
                continue;
              }

              tests.add(new File(dirName + "/" + ln + ".test"));
            } while (true);
          } finally {
            if (frdr != null) {
              try {
                frdr.close();
              } catch (Throwable t) {}
            }
          }
        } else {
          File[] dirfiles = dir.listFiles(new TestFilter());
          TreeSet<File> ts = new TreeSet<File>();

          for (int i = 0; i < dirfiles.length; i++) {
            ts.add(dirfiles[i]);
          }

          for (File f: ts) {
            tests.add(f);
          }
        }

        for (File testfile: tests) {
          String fname = testfile.getName();
          String tname = fname.substring(0, fname.length() - 5);

          Req r = new Req(user, pw, urlPrefix,
                          testfile.getCanonicalPath());

          System.out.println("Test " + tname + ": " + r.description);

          if (!list) {
            runTest(r, tname);
          }
        }
      } else {
        Req r = new Req(user, pw, urlPrefix,
                        dirName + "/" + fileName + ".test");

        System.out.println("Test " + fileName + ": " + r.description);
        if (!list) {
          runTest(r, fileName);
        }
      }
    } catch (Throwable t) {
      System.out.println("********************************************");
      System.out.println("********************************************");
      t.printStackTrace();
      System.out.println("********************************************");
      System.out.println("********************************************");
    }

    System.out.println("--------------------------------------------------------------");
    int num = 0;
    int ok = 0;
    for (TestResult tr: results) {
      System.out.println(tr);
      num++;
      if (tr.ok) {
        ok++;
      }
    }
    System.out.println("--------------------------------------------------------------");
    System.out.println("Ran " + num + " tests with " + ok + " successful");
    System.out.println("--------------------------------------------------------------");
  }

  static boolean processArgs(final String[] args) throws Throwable {
    if (args == null) {
      return false;
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-debug")) {
        debug = true;
      } else if (args[i].equals("-ndebug")) {
        debug = false;
      } else if ("-list".equals(args[i])) {
        list = true;
      } else if (argpar("-dir", args, i)) {
        i++;
        dirName = args[i];
      } else if (argpar("-urlPrefix", args, i)) {
        i++;
        urlPrefix = args[i];
      } else if (argpar("-host", args, i)) {
        i++;
        host = args[i];
      } else if (argpar("-port", args, i)) {
        i++;
        port = Integer.parseInt(args[i]);
      } else if (args[i].equals("-secure")) {
        secure = true;
      } else if (argpar("-user", args, i)) {
        i++;
        user = args[i];
      } else if (argpar("-pw", args, i)) {
        i++;
        pw = args[i];
      } else if (argpar("-testlist", args, i)) {
        i++;
        testListName = args[i];
      } else if ((fileName == null) && argpar("-test", args, i)) {
        i++;
        fileName = args[i];
      } else {
        System.out.println("Illegal argument: " + args[i]);
        usage();
        return false;
      }
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
    System.out.println("Usage:");
    System.out.println("args   -debug");
    System.out.println("       -ndebug");
    System.out.println("       -host hostname");
    System.out.println("       -port int");
    System.out.println("       -user username");
    System.out.println("       -pw pwstring");
    System.out.println("       -urlPrefix string");
    System.out.println("            Prefix for relative urls");
    System.out.println("       -dir dirname");
    System.out.println("            set location of tests");
    System.out.println("       -list");
    System.out.println("            Just list test file[s]");
    System.out.println("       -test testname");
    System.out.println("            run given test [in given directory]");
    System.out.println("");
    System.out.println("For example");
    System.out.println("   -dir mytestdir");
    System.out.println("             Run all the tests in given directory");
    System.out.println("   -dir mytestdir -testlist mylist");
    System.out.println("             Run all the test in given directory named in the");
    System.out.println("             file mylist in that directory.");
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
        System.out.println("No method " + r.getMethod());
        return false;
      }

      if (!Util.isEmpty(r.getHdrs())) {
        for (final Header hdr: r.getHdrs()) {
          req.addHeader(hdr);
        }
      }

      System.out.println("About to exec " + req.getMethod() + " on " + uri);
      setContent(req,
                 r.getContentBytes(),
                 r.getContentType());

      try (CloseableHttpResponse resp = cio.execute(req)) {
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
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    int len = 0;

    if (debug) {
      System.out.println("Read content - expected=" + expectedLen);
    }

    boolean hadLf = false;
    boolean hadCr = false;

    while ((expectedLen < 0) || (len < expectedLen)) {
      int ich = in.read();
      if (ich < 0) {
        break;
      }

      len++;

      if (ich == '\n') {
        if (hadLf) {
          System.out.println("");
          hadLf = false;
          hadCr = false;
        } else {
          hadLf = true;
        }
      } else if (ich == '\r') {
        if (hadCr) {
          System.out.println("");
          hadLf = false;
          hadCr = false;
        } else {
          hadCr = true;
        }
      } else if (hadCr || hadLf) {
        hadLf = false;
        hadCr = false;

        if (baos.size() > 0) {
          String ln = new String(baos.toByteArray(), charset);
          System.out.println(ln);
        }

        baos.reset();
        baos.write(ich);
      } else {
        baos.write(ich);
      }
    }

    if (baos.size() > 0) {
      String ln = new String(baos.toByteArray(), charset);

      System.out.println(ln);
    }
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

