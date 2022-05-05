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

import org.bedework.util.dav.DavUtil;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.LineNumberReader;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;

class Req {
  String user;
  String pw;
  String description;
  List<Header> hdrs;
  String contentType;
  String[] content;

  boolean fromFile;
  String contentFileName;
  byte[] contentBytes;

  /* true if we should apply one of the following prefixes.
   * Set false if url starts with "/"
   */
  boolean prefixUrls = true;
  String authUserUrlPrefix = "/ucaldav/user";
  String authPublicUrlPrefix = "/pubcaldav/public";

  // Supplied prefix
  String urlPrefix;

  private int expectedResp = -1;

  String method;
  String url;

  static final String descHdr = "DESCRIPTION: ";
  static final String exprespHdr = "EXPECT-RESPONSE: ";
  static final String methHdr = "METHOD: ";
  static final String depthHdr = "DEPTH: ";
  static final String authHdr = "AUTH: ";
  static final String urlHdr = "URL: ";
  static final String hdrHdr = "HEADER: ";
  static final String ctypeHdr = "CONTENTTYPE: ";
  static final String contHdr = "CONTENT:";
  static final String contFileHdr = "CONTENTFILE: ";
  static final String resFileHdr = "RESOURCEFILE: ";

  private static final DateFormat isoDateTimeUTCFormat =
      new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");

  Req(final String user,
      final String pw,
      final String urlPrefix,
      final String testFileName,
      final Path resourcedirPath) throws Throwable {
    this.urlPrefix = urlPrefix;

    FileReader frdr = null;

    boolean auth = false;

    try {
      final File testFile = new File(testFileName);
      frdr = new FileReader(testFile);
      final LineNumberReader lnr = new LineNumberReader(frdr);

      List<Header> headers = null;
      Collection<String> cont = null;

      do {
        final String ln = lnr.readLine();

        if (ln == null) {
          break;
        }

        if (ln.trim().length() == 0) {
          continue;
        }

        if (cont != null) {
          /*
              swallow remaining as content
           */
          cont.add(ln);
        } else if ((description == null) && (ln.startsWith(descHdr))) {
          /*
                DESCRIPTION
           */
          description = ln.substring(descHdr.length());
        } else if (ln.startsWith(exprespHdr)) {
          /*
                EXPECT-RESPONSE
           */
          expectedResp = Integer.parseInt(ln.substring(exprespHdr.length()));
        } else if ((method == null) && (ln.startsWith(methHdr))) {
          /*
                METHOD
           */
          method = ln.substring(methHdr.length());
        } else if (ln.startsWith(depthHdr)) {
          /*
                DEPTH
           */
          if (headers == null) {
            headers = new ArrayList<>();
          }

          final String depth = ln.substring(depthHdr.length());
          switch (depth) {
            case "0":
              headers.add(DavUtil.depth0);
              break;
            case "1":
              headers.add(DavUtil.depth1);
              break;
            case "infinity":
              headers.add(DavUtil.depthinf);
              break;
            default:
              System.out.println("Bad depth at line " + ln);
              throw new Exception(
                      "Bad test data file " + testFileName);
          }
        } else if (ln.startsWith(authHdr)) {
          /*
                AUTH
           */
          auth = "true".equals(ln.substring(authHdr.length()));
        } else if ((url == null) && (ln.startsWith(urlHdr))) {
          /*
                URL
           */
          url = ln.substring(urlHdr.length());
          if (url.startsWith("/")) {
            prefixUrls = false;
          }
        } else if (ln.startsWith(hdrHdr)) {
          /*
                HEADER
           */
          if (headers == null) {
            headers = new ArrayList<>();
          }

          final String hdr = ln.substring(hdrHdr.length());
          final int colonPos = hdr.indexOf(": ");
          if (colonPos < 0) {
            throw new Exception("Bad header in test data file " + testFileName);
          }

          headers.add(new BasicHeader(hdr.substring(0, colonPos),
                                 hdr.substring(colonPos + 2)));
        } else if ((contentType == null) && (ln.startsWith(ctypeHdr))) {
          /*
                CONTENTTYPE
           */
          contentType = ln.substring(ctypeHdr.length());
        } else if (ln.startsWith(resFileHdr)) {
          fromFile = true;
          final var fname = ln.substring(resFileHdr.length());

          final File contentFile =
                  resourcedirPath.resolve(fname).toFile();

          contentFileName = contentFile.getAbsolutePath();
          System.out.println("Load content from file " + contentFileName);
        } else if (ln.startsWith(contFileHdr)) {
          /*
                CONTENTFILE
           */
          fromFile = true;
          contentFileName = ln.substring(contFileHdr.length());
          final File contentFile = new File(contentFileName);

          if (!contentFile.isAbsolute()) {
            contentFileName = testFile.getParentFile().getAbsolutePath() + "/" + contentFileName;
          }

          System.out.println("Load content from file " + contentFileName);
        } else if (!fromFile && ln.startsWith(contHdr)) {
          /*
                CONTENT
           */
          if (cont == null) {
            cont = new ArrayList<String>();
          }
        } else {
          System.out.println("Bad line " + ln);
          throw new Exception("Bad test data file " + testFileName);
        }
      } while (true);

      hdrs = headers;

      if (cont != null) {
        content = cont.toArray(new String[cont.size()]);
      }

      if (auth) {
        this.user = user;
        this.pw = pw;
      }
    } finally {
      if (frdr != null) {
        try {
          frdr.close();
        } catch (Throwable t) {}
      }
    }
  }

  /**
   * @return String
   */
  public String getMethod() {
    return method;
  }

  /**
   * @return String url prefixed
   */
  public String getPrefixedUrl() {
    if (!prefixUrls) {
      return url;
    }

    String pfx = urlPrefix;

    if (pfx == null) {
      if (user != null) {
        pfx = authUserUrlPrefix;
      } else {
        pfx = authPublicUrlPrefix;
      }
    }

    if (!pfx.endsWith("/")) {
      pfx += "/";
    }

    if (user != null) {
      pfx += user + "/";
    }

    if (url == null) {
      return pfx;
    }

    return pfx += url;
  }

  /**
   * @return int
   */
  public int getExpectedResponse() {
    return expectedResp;
  }

  /**
   * @return List of Header
   */
  public List<Header> getHdrs() {
    return hdrs;
  }

  /**
   * @return String
   */
  public String getContentType() {
    return contentType;
  }

  /**
   * @return int content length
   * @throws Throwable
   */
  public int getContentLength() throws Throwable {
    if (fromFile) {
      return getFileBytes().length;
    }

    if (content == null) {
      return 0;
    }

    int len  = 0;
    for (int i = 0; i < content.length; i++) {
      len += content[i].length() + 1;
    }

    return len;
  }

  /**
   * @return byte[]  content bytes
   * @throws Throwable
   */
  public byte[] getContentBytes() throws Throwable {
    if (fromFile) {
      return getFileBytes();
    }

    if (content == null) {
      return null;
    }

    return getBytes();
  }

  private byte[] getBytes() {
    if (contentBytes != null) {
      return contentBytes;
    }

    StringBuffer sb = new StringBuffer();

    for (int i = 0; i < content.length; i++) {
      sb.append(content[i]);
      sb.append("\n");
    }
    contentBytes = detokenizeContent(sb);

    return contentBytes;
  }

  private byte[] getFileBytes() throws Throwable {
    if (contentBytes != null) {
      return contentBytes;
    }

    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    FileInputStream in = new FileInputStream(contentFileName);

    do {
      int x = in.read();
      if (x < 0) {
        break;
      }
      baos.write(x);
    } while (true);

    contentBytes = detokenizeContent(new StringBuffer(baos.toString()));

    return contentBytes;
  }

  /* Replace any of our tokens with values. Pretty primitive at the moment.
   *
   */
  private byte[] detokenizeContent(final StringBuffer val) {
    replaceToken(val, "@NOW@");

    replaceToken(val, "@TOMORROW@");

    replaceToken(val, "@NEXTWEEK@");

    return val.toString().getBytes();
  }

  private void replaceToken(final StringBuffer sb, final String token) {
    int len = token.length();
    int pos = 0;
    String val = null;

    while (true) {
      pos = sb.indexOf(token, pos);
      if (pos < 0) {
        return;
      }

      if (val == null) {
        val = getTokenValue(token);
      }

      sb.replace(pos, pos + len, val);
    }
  }

  private String getTokenValue(final String token) {
    if (token.equals("@NOW@")) {
      return isoDateTimeUTC(new Date());
    }

    if (token.equals("@TOMORROW@")) {
      Calendar cal = Calendar.getInstance();
      cal.add(Calendar.DATE, 1);
      return isoDateTimeUTC(cal.getTime());
    }

    if (token.equals("@NEXTWEEK@")) {
      Calendar cal = Calendar.getInstance();
      cal.add(Calendar.WEEK_OF_YEAR, 1);
      return isoDateTimeUTC(cal.getTime());
    }

    return null;
  }

  /** Turn Date into "yyyyMMddTHHmmssZ"
   *
   * @param val date
   * @return String "yyyyMMddTHHmmssZ"
   */
  public static String isoDateTimeUTC(final Date val) {
    return isoDateTimeUTCFormat.format(val);
  }
}

