/*
  +---------------------------------------------------------------------------+
  | Facebook Development Platform Java Client                                 |
  +---------------------------------------------------------------------------+
  | Copyright (c) 2007 Facebook, Inc.                                         |
  | All rights reserved.                                                      |
  |                                                                           |
  | Redistribution and use in source and binary forms, with or without        |
  | modification, are permitted provided that the following conditions        |
  | are met:                                                                  |
  |                                                                           |
  | 1. Redistributions of source code must retain the above copyright         |
  |    notice, this list of conditions and the following disclaimer.          |
  | 2. Redistributions in binary form must reproduce the above copyright      |
  |    notice, this list of conditions and the following disclaimer in the    |
  |    documentation and/or other materials provided with the distribution.   |
  |                                                                           |
  | THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR      |
  | IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES |
  | OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.   |
  | IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,          |
  | INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT  |
  | NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, |
  | DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY     |
  | THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT       |
  | (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF  |
  | THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.         |
  +---------------------------------------------------------------------------+
  | For help with this library, contact developers-help@facebook.com          |
  +---------------------------------------------------------------------------+
 */

package com.facebook.api;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import com.facebook.api.schema.Listing;
import com.facebook.api.schema.MarketplaceGetCategoriesResponse;
import com.facebook.api.schema.MarketplaceGetListingsResponse;
import com.facebook.api.schema.MarketplaceGetSubCategoriesResponse;
import com.facebook.api.schema.MarketplaceSearchResponse;

/**
 * A FacebookRestClient that uses the XML result format. This means results from calls 
 * to the Facebook API are returned as XML and transformed into instances of Document. 
 * 
 * Allocate an instance of this class to make Facebook API requests.
 */
public class FacebookRestClient implements IFacebookRestClient<Document>{
  /**
   * API version to request when making calls to the server
   */
  public static final String TARGET_API_VERSION = "1.0";
  /**
   * Flag indicating an erroneous response
   */
  public static final String ERROR_TAG = "error_response";
  /**
   * Facebook API server, part 1
   */
  public static final String FB_SERVER = "api.facebook.com/restserver.php";
  /**
   * Facebook API server, part 2a
   */
  public static final String SERVER_ADDR = "http://" + FB_SERVER;
  /**
   * Facebook API server, part 2b
   */
  public static final String HTTPS_SERVER_ADDR = "https://" + FB_SERVER;
  /**
   * Facebook API server, part 3a
   */
  public static URL SERVER_URL = null;
  /**
   * Facebook API server, part 3b
   */
  public static URL HTTPS_SERVER_URL = null;
  static {
    try {
      SERVER_URL = new URL(SERVER_ADDR);
      HTTPS_SERVER_URL = new URL(HTTPS_SERVER_ADDR);
    }
    catch (MalformedURLException e) {
      System.err.println("MalformedURLException: " + e.getMessage());
      System.exit(1);
    }
  }

  protected final String _secret;
  protected final String _apiKey;
  protected final URL _serverUrl;
  protected String rawResponse;

  protected String _sessionKey; // filled in when session is established
  protected Long _expires; // also filled in when session is established
  protected boolean _isDesktop = false;
  protected String _sessionSecret; // only used for desktop apps
  protected long _userId;
  protected int _timeout;

  /**
   * number of params that the client automatically appends to every API call
   */
  public static int NUM_AUTOAPPENDED_PARAMS = 6;
  private static boolean DEBUG = false;
  protected Boolean _debug = null;

  protected File _uploadFile = null;
  protected static final String CRLF = "\r\n";
  protected static final String PREF = "--";
  protected static final int UPLOAD_BUFFER_SIZE = 512;

  public static final String MARKETPLACE_STATUS_DEFAULT     = "DEFAULT";
  public static final String MARKETPLACE_STATUS_NOT_SUCCESS = "NOT_SUCCESS";
  public static final String MARKETPLACE_STATUS_SUCCESS     = "SUCCESS";

  /**
   * Constructor
   *
   * @param apiKey the developer's API key
   * @param secret the developer's secret key
   */
  public FacebookRestClient(String apiKey, String secret) {
    this(SERVER_URL, apiKey, secret, null);
  }
  
  /**
   * Constructor
   *
   * @param apiKey the developer's API key
   * @param secret the developer's secret key
   * @param timeout the timeout to apply when making API requests to Facebook, in milliseconds
   */
  public FacebookRestClient(String apiKey, String secret, int timeout) {
    this(SERVER_URL, apiKey, secret, null, timeout);
  }

  /**
   * Constructor
   *
   * @param apiKey the developer's API key
   * @param secret the developer's secret key
   * @param sessionKey the session-id to use
   */
  public FacebookRestClient(String apiKey, String secret, String sessionKey) {
    this(SERVER_URL, apiKey, secret, sessionKey);
  }
  
  /**
   * Constructor
   *
   * @param apiKey the developer's API key
   * @param secret the developer's secret key
   * @param sessionKey the session-id to use
   * @param timeout the timeout to apply when making API requests to Facebook, in milliseconds
   */
  public FacebookRestClient(String apiKey, String secret, String sessionKey, int timeout) {
    this(SERVER_URL, apiKey, secret, sessionKey, timeout);
  }

  /**
   * Constructor
   *
   * @param serverAddr the URL of the Facebook API server to use, allows overriding of the default API server.
   * @param apiKey the developer's API key
   * @param secret the developer's secret key
   * @param sessionKey the session-id to use
   *
   * @throws MalformedURLException if the specified serverAddr is invalid
   */
  public FacebookRestClient(String serverAddr, String apiKey, String secret,
                            String sessionKey) throws MalformedURLException {
    this(new URL(serverAddr), apiKey, secret, sessionKey);
  }
  
  /**
   * Constructor
   *
   * @param serverAddr the URL of the Facebook API server to use, allows overriding of the default API server.
   * @param apiKey the developer's API key
   * @param secret the developer's secret key
   * @param sessionKey the session-id to use
   * @param timeout the timeout to apply when making API requests to Facebook, in milliseconds
   *
   * @throws MalformedURLException if the specified serverAddr is invalid
   */
  public FacebookRestClient(String serverAddr, String apiKey, String secret,
                            String sessionKey, int timeout) throws MalformedURLException {
    this(new URL(serverAddr), apiKey, secret, sessionKey, timeout);
  }

  /**
   * Constructor
   *
   * @param serverUrl the URL of the Facebook API server to use, allows overriding of the default API server.
   * @param apiKey the developer's API key
   * @param secret the developer's secret key
   * @param sessionKey the session-id to use
   */
  public FacebookRestClient(URL serverUrl, String apiKey, String secret, String sessionKey) {
    _sessionKey = sessionKey;
    _apiKey = apiKey;
    _secret = secret;
    _serverUrl = (null != serverUrl) ? serverUrl : SERVER_URL;
    _timeout = -1;
  }
  
  /**
   * Constructor
   *
   * @param serverUrl the URL of the Facebook API server to use, allows overriding of the default API server.
   * @param apiKey the developer's API key
   * @param secret the developer's secret key
   * @param sessionKey the session-id to use
   * @param timeout the timeout to apply when making API requests to Facebook, in milliseconds
   */
  public FacebookRestClient(URL serverUrl, String apiKey, String secret, String sessionKey, int timeout) {
      this(serverUrl, apiKey, secret, sessionKey);
      _timeout = timeout;
  }

  /**
   * The response format in which results to FacebookMethod calls are returned
   * @return the format: either XML, JSON, or null (API default)
   */
  public String getResponseFormat() {
      return "xml";
  }

  /**
   * Set global debugging on.
   *
   * @param isDebug true to enable debugging
   *                false to disable debugging
   */
  public static void setDebugAll(boolean isDebug) {
    FacebookRestClient.DEBUG = isDebug;
  }

  /**
   * Set debugging on for this instance only.
   *
   * @param isDebug true to enable debugging
   *                false to disable debugging
   */
  //FIXME:  do we really need both of these?
  public void setDebug(boolean isDebug) {
    _debug = isDebug;
  }

  /**
   * Check to see if debug mode is enabled.
   *
   * @return true if debugging is enabled
   *         false otherwise
   */
  public boolean isDebug() {
    return (null == _debug) ? FacebookRestClient.DEBUG : _debug.booleanValue();
  }

  /**
   * Check to see if the client is running in desktop mode.
   *
   * @return true if the client is running in desktop mode
   *         false otherwise
   */
  public boolean isDesktop() {
    return this._isDesktop;
  }

  /**
   * Enable/disable desktop mode.
   *
   * @param isDesktop true to enable desktop application mode
   *                  false to disable desktop application mode
   */
  public void setIsDesktop(boolean isDesktop) {
    this._isDesktop = isDesktop;
  }

  /**
   * Prints out the DOM tree.
   *
   * @param n the parent node to start printing from
   * @param prefix string to append to output, should not be null
   */
  public static void printDom(Node n, String prefix) {
    String outString = prefix;
    if (n.getNodeType() == Node.TEXT_NODE) {
      outString += "'" + n.getTextContent().trim() + "'";
    }
    else {
      outString += n.getNodeName();
    }
    if (DEBUG) {
        System.out.println(outString);
    }
    NodeList children = n.getChildNodes();
    int length = children.getLength();
    for (int i = 0; i < length; i++) {
      FacebookRestClient.printDom(children.item(i), prefix + "  ");
    }
  }

  private static CharSequence delimit(Collection<?> iterable) {
    // could add a thread-safe version that uses StringBuffer as well
    if (iterable == null || iterable.isEmpty())
      return null;

    StringBuilder buffer = new StringBuilder();
    boolean notFirst = false;
    for (Object item: iterable) {
      if (notFirst)
        buffer.append(",");
      else
        notFirst = true;
      buffer.append(item.toString());
    }
    return buffer;
  }

  protected static CharSequence delimit(Collection<Map.Entry<String, CharSequence>> entries,
                                        CharSequence delimiter, CharSequence equals,
                                        boolean doEncode) {
    if (entries == null || entries.isEmpty())
      return null;

    StringBuilder buffer = new StringBuilder();
    boolean notFirst = false;
    for (Map.Entry<String, CharSequence> entry: entries) {
      if (notFirst)
        buffer.append(delimiter);
      else
        notFirst = true;
      CharSequence value = entry.getValue();
      buffer.append(entry.getKey()).append(equals).append(doEncode ? encode(value) : value);
    }
    return buffer;
  }

  /**
   * Call the specified method, with the given parameters, and return a DOM tree with the results.
   *
   * @param method the fieldName of the method
   * @param paramPairs a list of arguments to the method
   * @throws Exception with a description of any errors given to us by the server.
   */
  protected Document callMethod(IFacebookMethod method,
                                Pair<String, CharSequence>... paramPairs) throws FacebookException,
                                                                                 IOException {
    return callMethod(method, Arrays.asList(paramPairs));
  }

  /**
   * Call the specified method, with the given parameters, and return a DOM tree with the results.
   *
   * @param method the fieldName of the method
   * @param paramPairs a list of arguments to the method
   * @throws Exception with a description of any errors given to us by the server.
   */
  protected Document callMethod(IFacebookMethod method,
                                Collection<Pair<String, CharSequence>> paramPairs) throws FacebookException,
                                                                                          IOException {
    this.rawResponse = null;
    HashMap<String, CharSequence> params =
      new HashMap<String, CharSequence>(2 * method.numTotalParams());

    params.put("method", method.methodName());
    params.put("api_key", _apiKey);
    params.put("v", TARGET_API_VERSION);
    if (method.requiresSession() && _sessionKey != null) {
        // some methods like setFBML can take a session key for users, but doesn't require one for pages.
        params.put("call_id", Long.toString(System.currentTimeMillis()));
        params.put("session_key", _sessionKey);
    }
    CharSequence oldVal;
    for (Pair<String, CharSequence> p: paramPairs) {
      oldVal = params.put(p.first, p.second);
      if (oldVal != null)
          System.out.println("For parameter " + p.first + ", overwrote old value " + oldVal +
                " with new value " + p.second + ".");
    }

    assert (!params.containsKey("sig"));
    String signature = generateSignature(FacebookSignatureUtil.convert(params.entrySet()), method.requiresSession());
    params.put("sig", signature);

    try {
      DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
      boolean doHttps = this.isDesktop() && FacebookMethod.AUTH_GET_SESSION.equals(method);
      InputStream data =
        method.takesFile() ? postFileRequest(method.methodName(), params) : postRequest(method.methodName(),
                                                                                        params,
                                                                                        doHttps,
                                                                                        true);
      /*int current = 0;
      StringBuffer buffer = new StringBuffer();
      while (current != -1) {
          current = data.read();
          if (current != -1) {
              buffer.append((char)current);
          }
      }*/

      BufferedReader in = new BufferedReader(new InputStreamReader(data, "UTF-8"));
      StringBuffer buffer = new StringBuffer();
      String line;
      while ((line = in.readLine()) != null) {
        buffer.append(line);
      }


      String xmlResp = new String(buffer);
      this.rawResponse = xmlResp;

      Document doc = builder.parse(new ByteArrayInputStream(xmlResp.getBytes("UTF-8")));
      doc.normalizeDocument();
      stripEmptyTextNodes(doc);

      if (isDebug())
        FacebookRestClient.printDom(doc, method.methodName() + "| "); // TEST
      NodeList errors = doc.getElementsByTagName(ERROR_TAG);
      if (errors.getLength() > 0) {
        int errorCode =
          Integer.parseInt(errors.item(0).getFirstChild().getFirstChild().getTextContent());
        String message = errors.item(0).getFirstChild().getNextSibling().getTextContent();
        // FIXME: additional printing done for debugging only
        System.out.println("Facebook returns error code " + errorCode);
        for (Map.Entry<String,CharSequence> entry : params.entrySet())
            System.out.println("  - " + entry.getKey() + " -> " + entry.getValue());
        throw new FacebookException(errorCode, message);
      }
      return doc;
    }
    catch (java.net.SocketException ex) {
        System.err.println("Socket exception when calling facebook method: " + ex.getMessage());
    }
    catch (javax.xml.parsers.ParserConfigurationException ex) {
        System.err.println("huh?");
        ex.printStackTrace();
    }
    catch (org.xml.sax.SAXException ex) {
      throw new IOException("error parsing xml");
    }
    return null;
  }

  /**
   * Returns a string representation for the last API response recieved from Facebook, exactly as sent by the API server.
   *
   * Note that calling this method consumes the data held in the internal buffer, and thus it may only be called once per API
   * call.
   *
   * @return a String representation of the last API response sent by Facebook
   */
  public String getRawResponse() {
      String result = this.rawResponse;
      this.rawResponse = null;
      return result;
  }

  /**
   * Hack...since DOM reads newlines as textnodes we want to strip out those
   * nodes to make it easier to use the tree.
   */
  private static void stripEmptyTextNodes(Node n) {
    NodeList children = n.getChildNodes();
    int length = children.getLength();
    for (int i = 0; i < length; i++) {
      Node c = children.item(i);
      if (!c.hasChildNodes() && c.getNodeType() == Node.TEXT_NODE &&
          c.getTextContent().trim().length() == 0) {
        n.removeChild(c);
        i--;
        length--;
        children = n.getChildNodes();
      }
      else {
        stripEmptyTextNodes(c);
      }
    }
  }

  private String generateSignature(List<String> params, boolean requiresSession) {
    String secret = (isDesktop() && requiresSession) ? this._sessionSecret : this._secret;
    return FacebookSignatureUtil.generateSignature(params, secret);
  }

  private static String encode(CharSequence target) {
    String result = (target != null) ? target.toString() : "";
    try {
      result = URLEncoder.encode(result, "UTF8");
    }
    catch (UnsupportedEncodingException e) {
        System.err.println("Unsuccessful attempt to encode '" + result + "' into UTF8");
    }
    return result;
  }

  private InputStream postRequest(CharSequence method, Map<String, CharSequence> params,
                                  boolean doHttps, boolean doEncode) throws IOException {
    CharSequence buffer = (null == params) ? "" : delimit(params.entrySet(), "&", "=", doEncode);
    URL serverUrl = (doHttps) ? HTTPS_SERVER_URL : _serverUrl;
    if (isDebug() && DEBUG) {
        System.out.println(method);
        System.out.println(" POST: ");
        System.out.println(serverUrl.toString());
        System.out.println("/");
        System.out.println(buffer);
    }

    HttpURLConnection conn = (HttpURLConnection) serverUrl.openConnection();
    if (this._timeout != -1) {
        conn.setConnectTimeout(this._timeout);
    }
    try {
      conn.setRequestMethod("POST");
    }
    catch (ProtocolException ex) {
        System.err.println("huh?");
        ex.printStackTrace();
    }
    conn.setDoOutput(true);
    conn.connect();
    conn.getOutputStream().write(buffer.toString().getBytes());

    return conn.getInputStream();
  }

  /**
   * Sets the FBML for a user's profile, including the content for both the profile box
   * and the profile actions.
   * @param userId - the user whose profile FBML to set
   * @param fbmlMarkup - refer to the FBML documentation for a description of the markup and its role in various contexts
   * @return a boolean indicating whether the FBML was successfully set
   * 
   * @deprecated Facebook will remove support for this version of the API call on 1/17/2008, please use the alternate version instead.
   */
  public boolean profile_setFBML(CharSequence fbmlMarkup, Long userId) throws FacebookException, IOException {

    return extractBoolean(this.callMethod(FacebookMethod.PROFILE_SET_FBML,
                          new Pair<String, CharSequence>("uid", Long.toString(userId)),
                          new Pair<String, CharSequence>("markup", fbmlMarkup)));

  }
  
  public boolean profile_setFBML(Long userId, String profileFbml, String actionFbml, String mobileFbml) throws FacebookException, IOException {
      Collection<Pair<String, CharSequence>> params = new ArrayList<Pair<String, CharSequence>>();
      params.add(new Pair<String, CharSequence>("uid", Long.toString(userId)));
      if ((profileFbml != null) && (! "".equals(profileFbml))) {
          params.add(new Pair<String, CharSequence>("profile", profileFbml));
      }
      if ((actionFbml != null) && (! "".equals(actionFbml))) {
          params.add(new Pair<String, CharSequence>("profile_action", actionFbml));
      }
      if ((mobileFbml != null) && (! "".equals(mobileFbml))) {
          params.add(new Pair<String, CharSequence>("mobile_fbml", mobileFbml));
      }
      
      return extractBoolean(this.callMethod(FacebookMethod.PROFILE_SET_FBML, params));
  }

  /**
   * Gets the FBML for a user's profile, including the content for both the profile box
   * and the profile actions.
   * @param userId - the user whose profile FBML to set
   * @return a Document containing FBML markup
   */
  public Document profile_getFBML(Long userId) throws FacebookException, IOException {
    return this.callMethod(FacebookMethod.PROFILE_GET_FBML,
                          new Pair<String, CharSequence>("uid", Long.toString(userId)));

  }

  /**
   * Recaches the referenced url.
   * @param url string representing the URL to refresh
   * @return boolean indicating whether the refresh succeeded
   */
  public boolean fbml_refreshRefUrl(String url) throws FacebookException, IOException {
    return fbml_refreshRefUrl(new URL(url));
  }

  /**
   * Recaches the referenced url.
   * @param url the URL to refresh
   * @return boolean indicating whether the refresh succeeded
   */
  public boolean fbml_refreshRefUrl(URL url) throws FacebookException, IOException {
    return extractBoolean(this.callMethod(FacebookMethod.FBML_REFRESH_REF_URL,
                                          new Pair<String, CharSequence>("url", url.toString())));
  }

  /**
   * Recaches the image with the specified imageUrl.
   * @param imageUrl String representing the image URL to refresh
   * @return boolean indicating whether the refresh succeeded
   */
  public boolean fbml_refreshImgSrc(String imageUrl) throws FacebookException, IOException {
    return fbml_refreshImgSrc(new URL(imageUrl));
  }

  /**
   * Recaches the image with the specified imageUrl.
   * @param imageUrl the image URL to refresh
   * @return boolean indicating whether the refresh succeeded
   */
  public boolean fbml_refreshImgSrc(URL imageUrl) throws FacebookException, IOException {
    return extractBoolean(this.callMethod(FacebookMethod.FBML_REFRESH_IMG_SRC,
                          new Pair<String, CharSequence>("url", imageUrl.toString())));
  }

  /**
   * Publishes a templatized action for the current user.  The action will appear in their minifeed,
   * and may appear in their friends' newsfeeds depending upon a number of different factors.  When
   * a template match exists between multiple distinct users (like "Bob recommends Bizou" and "Sally
   * recommends Bizou"), the feed entries may be combined in the newfeed (to something like "Bob and sally
   * recommend Bizou").  This happens automatically, and *only* if the template match between the two
   * feed entries is identical.<br />
   * <br />
   * Feed entries are not aggregated for a single user (so "Bob recommends Bizou" and "Bob recommends Le
   * Charm" *will not* become "Bob recommends Bizou and Le Charm").<br />
   * <br />
   * If the user's action involves one or more of their friends, list them in the 'targetIds' parameter.
   * For example, if you have "Bob says hi to Sally and Susie", and Sally's UID is 1, and Susie's UID is 2,
   * then pass a 'targetIds' paramters of "1,2".  If you pass this parameter, you can use the "{target}" token
   * in your templates.  Probably it also makes it more likely that Sally and Susie will see the feed entry
   * in their newsfeed, relative to any other friends Bob might have.  It may be a good idea to always send
   * a list of all the user's friends, and avoid using the "{target}" token, to maximize distribution of the
   * story through the newsfeed.<br />
   * <br />
   * The only strictly required parameter is 'titleTemplate', which must contain the "{actor}" token somewhere
   * inside of it.  All other parameters, options, and tokens are optional, and my be set to null if
   * being omitted.<br />
   * <br />
   * Not that stories will only be aggregated if *all* templates match and *all* template parameters match, so
   * if two entries have the same templateTitle and titleData, but a different bodyTemplate, they will not
   * aggregate.  Probably it's better to use bodyGeneral instead of bodyTemplate, for the extra flexibility
   * it provides.<br />
   * <br />
   * <br />
   * Note that this method is replacing 'feed_publishActionOfUser', which has been deprecated by Facebook.
   * For specific details, visit http://wiki.developers.facebook.com/index.php/Feed.publishTemplatizedAction
   *
   *
   * @param titleTemplate the template for the title of the feed entry, this must contain the "(actor}" token.
   *                      Any other tokens are optional, i.e. "{actor} recommends {place}".
   * @param titleData JSON-formatted values for any tokens used in titleTemplate, with the exception of "{actor}"
   *                  and "{target}", which Facebook populates automatically, i.e. "{place: "<a href='http://www.bizou.com'>Bizou</a>"}".
   * @param bodyTemplate the template for the body of the feed entry, works the same as 'titleTemplate', but
   *                     is not required to contain the "{actor}" token.
   * @param bodyData works the same as titleData
   * @param bodyGeneral non-templatized content for the body, may contain markup, may not contain tokens.
   * @param pictures a list of up to 4 images to display, with optional hyperlinks for each one.
   * @param targetIds a comma-seperated list of the UID's of any friend(s) who are involved in this feed
   *                  action (if there are any), this specifies the value of the "{target}" token.  If you
   *                  use this token in any of your templates, you must specify a value for this parameter.
   *
   * @return a Document representing the XML response returned from the Facebook API server.
   *
   * @throws FacebookException if any number of bad things happen
   * @throws IOException
   */
  public boolean feed_publishTemplatizedAction(String titleTemplate, String titleData, String bodyTemplate,
          String bodyData, String bodyGeneral, Collection<? extends IPair<? extends Object, URL>> pictures, String targetIds) throws FacebookException, IOException {

      return templatizedFeedHandler(null, FacebookMethod.FEED_PUBLISH_TEMPLATIZED_ACTION, titleTemplate, titleData, bodyTemplate,
              bodyData, bodyGeneral, pictures, targetIds, null);
  }
  
  public boolean feed_publishTemplatizedAction(String titleTemplate, String titleData, String bodyTemplate,
          String bodyData, String bodyGeneral, Collection<? extends IPair<? extends Object, URL>> pictures, String targetIds, Long pageId) throws FacebookException, IOException {

      return templatizedFeedHandler(null, FacebookMethod.FEED_PUBLISH_TEMPLATIZED_ACTION, titleTemplate, titleData, bodyTemplate,
              bodyData, bodyGeneral, pictures, targetIds, pageId);
  }

  /**
   * Publishes a templatized action for the current user.  The action will appear in their minifeed,
   * and may appear in their friends' newsfeeds depending upon a number of different factors.  When
   * a template match exists between multiple distinct users (like "Bob recommends Bizou" and "Sally
   * recommends Bizou"), the feed entries may be combined in the newfeed (to something like "Bob and sally
   * recommend Bizou").  This happens automatically, and *only* if the template match between the two
   * feed entries is identical.<br />
   * <br />
   * Feed entries are not aggregated for a single user (so "Bob recommends Bizou" and "Bob recommends Le
   * Charm" *will not* become "Bob recommends Bizou and Le Charm").<br />
   * <br />
   * If the user's action involves one or more of their friends, list them in the 'targetIds' parameter.
   * For example, if you have "Bob says hi to Sally and Susie", and Sally's UID is 1, and Susie's UID is 2,
   * then pass a 'targetIds' paramters of "1,2".  If you pass this parameter, you can use the "{target}" token
   * in your templates.  Probably it also makes it more likely that Sally and Susie will see the feed entry
   * in their newsfeed, relative to any other friends Bob might have.  It may be a good idea to always send
   * a list of all the user's friends, and avoid using the "{target}" token, to maximize distribution of the
   * story through the newsfeed.<br />
   * <br />
   * The only strictly required parameter is 'titleTemplate', which must contain the "{actor}" token somewhere
   * inside of it.  All other parameters, options, and tokens are optional, and my be set to null if
   * being omitted.<br />
   * <br />
   * Not that stories will only be aggregated if *all* templates match and *all* template parameters match, so
   * if two entries have the same templateTitle and titleData, but a different bodyTemplate, they will not
   * aggregate.  Probably it's better to use bodyGeneral instead of bodyTemplate, for the extra flexibility
   * it provides.<br />
   * <br />
   * <br />
   * Note that this method is replacing 'feed_publishActionOfUser', which has been deprecated by Facebook.
   * For specific details, visit http://wiki.developers.facebook.com/index.php/Feed.publishTemplatizedAction
   *
   *
   * @param action a TemplatizedAction instance that represents the feed data to publish
   *
   * @return a Document representing the XML response returned from the Facebook API server.
   *
   * @throws FacebookException if any number of bad things happen
   * @throws IOException
   */
  public boolean feed_PublishTemplatizedAction(TemplatizedAction action) throws FacebookException, IOException {
      return this.feed_publishTemplatizedAction(action.getTitleTemplate(), action.getTitleParams(), action.getBodyTemplate(), action.getBodyParams(), action.getBodyGeneral(), action.getPictures(), action.getTargetIds(), action.getPageActorId());
  }

  /**
   * Publish the notification of an action taken by a user to newsfeed.
   * @param title the title of the feed story
   * @param body the body of the feed story
   * @param images (optional) up to four pairs of image URLs and (possibly null) link URLs
   * @param priority
   * @return a document object containing the server response
   *
   * @deprecated Facebook will be removing this API call (it is to be replaced with feed_publishTemplatizedAction)
   */
  public boolean feed_publishActionOfUser(CharSequence title, CharSequence body,
                                           Collection<? extends IPair<? extends Object, URL>> images,
                                           Integer priority) throws FacebookException,
                                                                    IOException {
    return feedHandlerBoolean(FacebookMethod.FEED_PUBLISH_ACTION_OF_USER, title, body, images, priority);
  }

  /**
   * @see FacebookRestClient#feed_publishActionOfUser(CharSequence,CharSequence,Collection,Integer)
   *
   * @deprecated Facebook will be removing this API call (it is to be replaced with feed_publishTemplatizedAction)
   */
  public boolean feed_publishActionOfUser(String title,
                                           String body) throws FacebookException,
                                                                     IOException {
    return feed_publishActionOfUser(title, body, null, null);
  }
  
  /**
   * @see FacebookRestClient#feed_publishActionOfUser(CharSequence,CharSequence,Collection,Integer)
   *
   * @deprecated Facebook will be removing this API call (it is to be replaced with feed_publishTemplatizedAction)
   */
  public boolean feed_publishActionOfUser(CharSequence title,
                                           CharSequence body) throws FacebookException,
                                                                     IOException {
    return feed_publishActionOfUser(title, body, null, null);
  }

  /**
   * @see FacebookRestClient#feed_publishActionOfUser(CharSequence,CharSequence,Collection,Integer)
   *
   * @deprecated Facebook will be removing this API call (it is to be replaced with feed_publishTemplatizedAction)
   */
  public boolean feed_publishActionOfUser(CharSequence title, CharSequence body,
                                           Integer priority) throws FacebookException,
                                                                    IOException {
    return feed_publishActionOfUser(title, body, null, priority);
  }

  /**
   * Publish a story to the logged-in user's newsfeed.
   * @param title the title of the feed story
   * @param body the body of the feed story
   * @param images (optional) up to four pairs of image URLs and (possibly null) link URLs
   * @param priority
   * @return a Document object containing the server response
   */
  public boolean feed_publishStoryToUser(CharSequence title, CharSequence body,
                                          Collection<? extends IPair<? extends Object, URL>> images,
                                          Integer priority) throws FacebookException, IOException {
    return feedHandlerBoolean(FacebookMethod.FEED_PUBLISH_STORY_TO_USER, title, body, images, priority);
  }

  /**
   * @see FacebookRestClient#feed_publishStoryToUser(CharSequence,CharSequence,Collection,Integer)
   */
  public boolean feed_publishStoryToUser(String title,
                                          String body) throws FacebookException,
                                                                    IOException {
    return feed_publishStoryToUser(title, body, null, null);
  }

  /**
   * @see FacebookRestClient#feed_publishStoryToUser(CharSequence,CharSequence,Collection,Integer)
   */
  public boolean feed_publishStoryToUser(String title, String body,
                                          Integer priority) throws FacebookException, IOException {
    return feed_publishStoryToUser(title, body, null, priority);
  }
  
  /**
   * @see FacebookRestClient#feed_publishStoryToUser(CharSequence,CharSequence,Collection,Integer)
   */
  public boolean feed_publishStoryToUser(CharSequence title,
                                          CharSequence body) throws FacebookException,
                                                                    IOException {
    return feed_publishStoryToUser(title, body, null, null);
  }

  /**
   * @see FacebookRestClient#feed_publishStoryToUser(CharSequence,CharSequence,Collection,Integer)
   */
  public boolean feed_publishStoryToUser(CharSequence title, CharSequence body,
                                          Integer priority) throws FacebookException, IOException {
    return feed_publishStoryToUser(title, body, null, priority);
  }

  protected Document feedHandler(FacebookMethod feedMethod, CharSequence title, CharSequence body,
                                 Collection<? extends IPair<? extends Object, URL>> images,
                                 Integer priority) throws FacebookException, IOException {
    assert (images == null || images.size() <= 4);

    ArrayList<Pair<String, CharSequence>> params =
      new ArrayList<Pair<String, CharSequence>>(feedMethod.numParams());

    params.add(new Pair<String, CharSequence>("title", title));
    if (null != body)
    params.add(new Pair<String, CharSequence>("body", body));
    if (null != priority)
      params.add(new Pair<String, CharSequence>("priority", priority.toString()));
    if (null != images && !images.isEmpty()) {
      int image_count = 0;
      for (IPair image: images) {
        ++image_count;
        assert (image.getFirst() != null);
        params.add(new Pair<String, CharSequence>(String.format("image_%d", image_count),
                                                  image.getFirst().toString()));
        if (image.getSecond() != null)
          params.add(new Pair<String, CharSequence>(String.format("image_%d_link", image_count),
                                                    image.getSecond().toString()));
      }
    }
    return this.callMethod(feedMethod, params);
  }

  protected boolean feedHandlerBoolean(FacebookMethod feedMethod, CharSequence title, CharSequence body,
          Collection<? extends IPair<? extends Object, URL>> images,
          Integer priority) throws FacebookException, IOException {
      assert (images == null || images.size() <= 4);
    
      ArrayList<Pair<String, CharSequence>> params =
          new ArrayList<Pair<String, CharSequence>>(feedMethod.numParams());
    
      params.add(new Pair<String, CharSequence>("title", title));
      if (null != body)
          params.add(new Pair<String, CharSequence>("body", body));
      if (null != priority)
          params.add(new Pair<String, CharSequence>("priority", priority.toString()));
      if (null != images && !images.isEmpty()) {
          int image_count = 0;
          for (IPair image: images) {
              ++image_count;
              assert (image.getFirst() != null);
              params.add(new Pair<String, CharSequence>(String.format("image_%d", image_count),
                               image.getFirst().toString()));
              if (image.getSecond() != null)
                  params.add(new Pair<String, CharSequence>(String.format("image_%d_link", image_count),
                                 image.getSecond().toString()));
          }
      }
      this.callMethod(feedMethod, params);
      return this.rawResponse.contains(">1<"); //a code of '1' indicates success
  }
  
  
  protected boolean templatizedFeedHandler(Long actorId, FacebookMethod method, String titleTemplate, String titleData, String bodyTemplate,
          String bodyData, String bodyGeneral, Collection<? extends IPair<? extends Object, URL>> pictures, String targetIds, Long pageId) throws FacebookException, IOException {
      assert (pictures == null || pictures.size() <= 4);

      ArrayList<Pair<String, CharSequence>> params = new ArrayList<Pair<String, CharSequence>>(method.numParams());

      //these are always required parameters
      params.add(new Pair<String, CharSequence>("title_template", titleTemplate));

      //these are optional parameters
      if (titleData != null) {
          params.add(new Pair<String, CharSequence>("title_data", titleData));
      }
      if (bodyTemplate != null) {
          params.add(new Pair<String, CharSequence>("body_template", bodyTemplate));
          if (bodyData != null) {
              params.add(new Pair<String, CharSequence>("body_data", bodyData));
          }
      }
      if (bodyGeneral != null) {
          params.add(new Pair<String, CharSequence>("body_general", bodyGeneral));
      }
      if (pictures != null) {
          int count = 1;
          for (IPair picture : pictures) {
                params.add(new Pair<String, CharSequence>("image_" + count, picture.getFirst().toString()));
                if (picture.getSecond() != null) {
                    params.add(new Pair<String, CharSequence>("image_" + count + "_link", picture.getSecond().toString()));
                }
                count++;
          }
      }
      if (targetIds != null) {
          params.add(new Pair<String, CharSequence>("target_ids", targetIds));
      }
      if (pageId != null) {
          params.add(new Pair<String, CharSequence>("page_actor_id", Long.toString(pageId)));
      }
      this.callMethod(method, params);
      return this.rawResponse.contains(">1<"); //a code of '1' indicates success
  }

  /**
   * Returns all visible events according to the filters specified. This may be used to find all events of a user, or to query specific eids.
   * @param eventIds filter by these event ID's (optional)
   * @param userId filter by this user only (optional)
   * @param startTime UTC lower bound (optional)
   * @param endTime UTC upper bound (optional)
   * @return Document of events
   */
  public Document events_get(Long userId, Collection<Long> eventIds, Long startTime,
                             Long endTime) throws FacebookException, IOException {
    ArrayList<Pair<String, CharSequence>> params =
      new ArrayList<Pair<String, CharSequence>>(FacebookMethod.EVENTS_GET.numParams());

    boolean hasUserId = null != userId && 0 != userId;
    boolean hasEventIds = null != eventIds && !eventIds.isEmpty();
    boolean hasStart = null != startTime && 0 != startTime;
    boolean hasEnd = null != endTime && 0 != endTime;

    if (hasUserId)
      params.add(new Pair<String, CharSequence>("uid", Long.toString(userId)));
    if (hasEventIds)
      params.add(new Pair<String, CharSequence>("eids", delimit(eventIds)));
    if (hasStart)
      params.add(new Pair<String, CharSequence>("start_time", startTime.toString()));
    if (hasEnd)
      params.add(new Pair<String, CharSequence>("end_time", endTime.toString()));
    return this.callMethod(FacebookMethod.EVENTS_GET, params);
  }

  /**
   * Retrieves the membership list of an event
   * @param eventId event id
   * @return Document consisting of four membership lists corresponding to RSVP status, with keys
   *  'attending', 'unsure', 'declined', and 'not_replied'
   */
  public Document events_getMembers(Number eventId) throws FacebookException, IOException {
    assert (null != eventId);
    return this.callMethod(FacebookMethod.EVENTS_GET_MEMBERS,
                           new Pair<String, CharSequence>("eid", eventId.toString()));
  }


  /**
   * Retrieves the friends of the currently logged in user.
   * @return array of friends
   */
  public Document friends_areFriends(long userId1, long userId2) throws FacebookException,
                                                                      IOException {
    return this.callMethod(FacebookMethod.FRIENDS_ARE_FRIENDS,
                           new Pair<String, CharSequence>("uids1", Long.toString(userId1)),
                           new Pair<String, CharSequence>("uids2", Long.toString(userId2)));
  }

  public Document friends_areFriends(Collection<Long> userIds1,
                                     Collection<Long> userIds2) throws FacebookException,
                                                                          IOException {
    assert (userIds1 != null && userIds2 != null);
    assert (!userIds1.isEmpty() && !userIds2.isEmpty());
    assert (userIds1.size() == userIds2.size());

    return this.callMethod(FacebookMethod.FRIENDS_ARE_FRIENDS,
                           new Pair<String, CharSequence>("uids1", delimit(userIds1)),
                           new Pair<String, CharSequence>("uids2", delimit(userIds2)));
  }

  /**
   * Retrieves the friends of the currently logged in user.
   * @return array of friends
   */
  public Document friends_get() throws FacebookException, IOException {
    return this.callMethod(FacebookMethod.FRIENDS_GET);
  }

  /**
   * Retrieves the friends of the currently logged in user, who are also users
   * of the calling application.
   * @return array of friends
   */
  public Document friends_getAppUsers() throws FacebookException, IOException {
    return this.callMethod(FacebookMethod.FRIENDS_GET_APP_USERS);
  }

  /**
   * Retrieves the requested info fields for the requested set of users.
   * @param userIds a collection of user IDs for which to fetch info
   * @param fields a set of ProfileFields
   * @return a Document consisting of a list of users, with each user element
   *   containing the requested fields.
   */
  public Document users_getInfo(Collection<Long> userIds,
                                EnumSet<ProfileField> fields) throws FacebookException,
                                                                     IOException {
    // assertions test for invalid params
    assert (userIds != null);
    assert (fields != null);
    assert (!fields.isEmpty());

    return this.callMethod(FacebookMethod.USERS_GET_INFO,
                           new Pair<String, CharSequence>("uids", delimit(userIds)),
                           new Pair<String, CharSequence>("fields", delimit(fields)));
  }

  /**
   * Retrieves the requested info fields for the requested set of users.
   * @param userIds a collection of user IDs for which to fetch info
   * @param fields a set of strings describing the info fields desired, such as "last_name", "sex"
   * @return a Document consisting of a list of users, with each user element
   *   containing the requested fields.
   */
  public Document users_getInfo(Collection<Long> userIds,
                                Set<CharSequence> fields) throws FacebookException, IOException {
    // assertions test for invalid params
    assert (userIds != null);
    assert (fields != null);
    assert (!fields.isEmpty());

    return this.callMethod(FacebookMethod.USERS_GET_INFO,
                           new Pair<String, CharSequence>("uids", delimit(userIds)),
                           new Pair<String, CharSequence>("fields", delimit(fields)));
  }

  /**
   * Retrieves the user ID of the user logged in to this API session
   * @return the Facebook user ID of the logged-in user
   */
  public long users_getLoggedInUser() throws FacebookException, IOException {
    Document d = this.callMethod(FacebookMethod.USERS_GET_LOGGED_IN_USER);
    return Long.parseLong(d.getFirstChild().getTextContent());
  }

  /**
   * Retrieves an indicator of whether the logged-in user has installed the
   * application associated with the _apiKey.
   * @return boolean indicating whether the user has installed the app
   */
  public boolean users_isAppAdded() throws FacebookException, IOException {
    return extractBoolean(this.callMethod(FacebookMethod.USERS_IS_APP_ADDED));
  }

  /**
   * Used to retrieve photo objects using the search parameters (one or more of the
   * parameters must be provided).
   *
   * @param subjId retrieve from photos associated with this user (optional).
   * @param albumId retrieve from photos from this album (optional)
   * @param photoIds retrieve from this list of photos (optional)
   *
   * @return an Document of photo objects.
   */
  public Document photos_get(Long subjId, Long albumId,
                             Collection<Long> photoIds) throws FacebookException, IOException {
    ArrayList<Pair<String, CharSequence>> params =
      new ArrayList<Pair<String, CharSequence>>(FacebookMethod.PHOTOS_GET.numParams());

    boolean hasUserId = null != subjId && 0 != subjId;
    boolean hasAlbumId = null != albumId && 0 != albumId;
    boolean hasPhotoIds = null != photoIds && !photoIds.isEmpty();
    assert (hasUserId || hasAlbumId || hasPhotoIds);

    if (hasUserId)
      params.add(new Pair<String, CharSequence>("subj_id", Long.toString(subjId)));
    if (hasAlbumId)
      params.add(new Pair<String, CharSequence>("aid", Long.toString(albumId)));
    if (hasPhotoIds)
      params.add(new Pair<String, CharSequence>("pids", delimit(photoIds)));

    return this.callMethod(FacebookMethod.PHOTOS_GET, params);
  }

  public Document photos_get(Long albumId, Collection<Long> photoIds, boolean album) throws FacebookException,
                                                                             IOException {
    return photos_get(null/*subjId*/, albumId, photoIds);
  }

  public Document photos_get(Long subjId, Collection<Long> photoIds) throws FacebookException,
                                                                               IOException {
    return photos_get(subjId, null/*albumId*/, photoIds);
  }

  public Document photos_get(Long subjId, Long albumId) throws FacebookException, IOException {
    return photos_get(subjId, albumId, null/*photoIds*/);
  }

  public Document photos_get(Collection<Long> photoIds) throws FacebookException, IOException {
    return photos_get(null/*subjId*/, null/*albumId*/, photoIds);
  }

  public Document photos_get(Long albumId, boolean album) throws FacebookException, IOException {
    return photos_get(null/*subjId*/, albumId, null/*photoIds*/);
  }

  public Document photos_get(Long subjId) throws FacebookException, IOException {
    return photos_get(subjId, null/*albumId*/, null/*photoIds*/);
  }

  /**
   * Retrieves album metadata. Pass a user id and/or a list of album ids to specify the albums
   * to be retrieved (at least one must be provided)
   *
   * @param userId retrieve metadata for albums created the id of the user whose album you wish  (optional).
   * @param albumIds the ids of albums whose metadata is to be retrieved
   * @return album objects.
   */
  public Document photos_getAlbums(Long userId,
                                   Collection<Long> albumIds) throws FacebookException,
                                                                     IOException {
    boolean hasUserId = null != userId && userId != 0;
    boolean hasAlbumIds = null != albumIds && !albumIds.isEmpty();
    assert (hasUserId || hasAlbumIds); // one of the two must be provided

    if (hasUserId)
      return (hasAlbumIds) ?
             this.callMethod(FacebookMethod.PHOTOS_GET_ALBUMS, new Pair<String, CharSequence>("uid",
                                                                                              Long.toString(userId)),
                             new Pair<String, CharSequence>("aids", delimit(albumIds))) :
             this.callMethod(FacebookMethod.PHOTOS_GET_ALBUMS,
                             new Pair<String, CharSequence>("uid", Long.toString(userId)));
    else
      return this.callMethod(FacebookMethod.PHOTOS_GET_ALBUMS,
                             new Pair<String, CharSequence>("aids", delimit(albumIds)));
  }

  public Document photos_getAlbums(Long userId) throws FacebookException, IOException {
    return photos_getAlbums(userId, null /*albumIds*/);
  }

  public Document photos_getAlbums(Collection<Long> albumIds) throws FacebookException,
                                                                     IOException {
    return photos_getAlbums(null /*userId*/, albumIds);
  }

  /**
   * Retrieves the tags for the given set of photos.
   * @param photoIds The list of photos from which to extract photo tags.
   * @return the created album
   */
  public Document photos_getTags(Collection<Long> photoIds) throws FacebookException, IOException {
    return this.callMethod(FacebookMethod.PHOTOS_GET_TAGS,
                           new Pair<String, CharSequence>("pids", delimit(photoIds)));
  }

  /**
   * Creates an album.
   * @param albumName The list of photos from which to extract photo tags.
   * @return the created album
   */
  public Document photos_createAlbum(String albumName) throws FacebookException, IOException {
    return this.photos_createAlbum(albumName, null/*description*/, null/*location*/);
  }

  /**
   * Creates an album.
   * @param name The album name.
   * @param location The album location (optional).
   * @param description The album description (optional).
   * @return an array of photo objects.
   */
  public Document photos_createAlbum(String name, String description,
                                     String location) throws FacebookException, IOException {
    assert (null != name && !"".equals(name));
    ArrayList<Pair<String, CharSequence>> params =
      new ArrayList<Pair<String, CharSequence>>(FacebookMethod.PHOTOS_CREATE_ALBUM.numParams());
    params.add(new Pair<String, CharSequence>("name", name));
    if (null != description)
      params.add(new Pair<String, CharSequence>("description", description));
    if (null != location)
      params.add(new Pair<String, CharSequence>("location", location));
    return this.callMethod(FacebookMethod.PHOTOS_CREATE_ALBUM, params);
  }

  /**
   * Adds several tags to a photo.
   * @param photoId The photo id of the photo to be tagged.
   * @param tags A list of PhotoTags.
   * @return a list of booleans indicating whether the tag was successfully added.
   */
  public Document photos_addTags(Long photoId, Collection<PhotoTag> tags)
    throws FacebookException, IOException {
    assert (photoId > 0);
    assert (null != tags && !tags.isEmpty());
    String tagStr = null;
    try {
        JSONArray jsonTags=new JSONArray();
        for (PhotoTag tag : tags) {
          jsonTags.put(tag.jsonify());
        }
        tagStr = jsonTags.toString();
    }
    catch (Exception ignored) {}

    return this.callMethod(FacebookMethod.PHOTOS_ADD_TAG,
                           new Pair<String, CharSequence>("pid", photoId.toString()),
                           new Pair<String, CharSequence>("tags", tagStr));
  }

  /**
   * Adds a tag to a photo.
   * @param photoId The photo id of the photo to be tagged.
   * @param xPct The horizontal position of the tag, as a percentage from 0 to 100, from the left of the photo.
   * @param yPct The vertical position of the tag, as a percentage from 0 to 100, from the top of the photo.
   * @param taggedUserId The list of photos from which to extract photo tags.
   * @return whether the tag was successfully added.
   */
  public boolean photos_addTag(Long photoId, Long taggedUserId, Double xPct,
                               Double yPct) throws FacebookException, IOException {
    return photos_addTag(photoId, xPct, yPct, taggedUserId, null);
  }

  /**
   * Adds a tag to a photo.
   * @param photoId The photo id of the photo to be tagged.
   * @param xPct The horizontal position of the tag, as a percentage from 0 to 100, from the left of the photo.
   * @param yPct The list of photos from which to extract photo tags.
   * @param tagText The text of the tag.
   * @return whether the tag was successfully added.
   */
  public boolean photos_addTag(Long photoId, CharSequence tagText, Double xPct,
                               Double yPct) throws FacebookException, IOException {
    return photos_addTag(photoId, xPct, yPct, null, tagText);
  }

  private boolean photos_addTag(Long photoId, Double xPct, Double yPct, Long taggedUserId,
                                CharSequence tagText) throws FacebookException, IOException {
    assert (null != photoId && !photoId.equals(0));
    assert (null != taggedUserId || null != tagText);
    assert (null != xPct && xPct >= 0 && xPct <= 100);
    assert (null != yPct && yPct >= 0 && yPct <= 100);
    Pair<String, CharSequence> tagData;
    if (taggedUserId != null) {
        tagData = new Pair<String, CharSequence>("tag_uid", taggedUserId.toString());
    }
    else {
        tagData = new Pair<String, CharSequence>("tag_text", tagText.toString());
    }
    Document d =
      this.callMethod(FacebookMethod.PHOTOS_ADD_TAG, new Pair<String, CharSequence>("pid",
                                                                                    photoId.toString()),
                      tagData,
                      new Pair<String, CharSequence>("x", xPct.toString()),
                      new Pair<String, CharSequence>("y", yPct.toString()));
    return extractBoolean(d);
  }

  public Document photos_upload(File photo) throws FacebookException, IOException {
    return /* caption */ /* albumId */photos_upload(photo, null, null);
  }

  public Document photos_upload(File photo, String caption) throws FacebookException, IOException {
    return /* albumId */photos_upload(photo, caption, null);
  }

  public Document photos_upload(File photo, Long albumId) throws FacebookException, IOException {
    return /* caption */photos_upload(photo, null, albumId);
  }

  public Document photos_upload(File photo, String caption, Long albumId) throws FacebookException,
                                                                                 IOException {
    ArrayList<Pair<String, CharSequence>> params =
      new ArrayList<Pair<String, CharSequence>>(FacebookMethod.PHOTOS_UPLOAD.numParams());
    assert (photo.exists() && photo.canRead());
    this._uploadFile = photo;
    if (null != albumId)
      params.add(new Pair<String, CharSequence>("aid", Long.toString(albumId)));
    if (null != caption)
      params.add(new Pair<String, CharSequence>("caption", caption));
    return callMethod(FacebookMethod.PHOTOS_UPLOAD, params);
  }

  /**
   * Retrieves the groups associated with a user
   * @param userId Optional: User associated with groups.
   *  A null parameter will default to the session user.
   * @param groupIds Optional: group ids to query.
   *   A null parameter will get all groups for the user.
   * @return array of groups
   */
  public Document groups_get(Long userId, Collection<Long> groupIds) throws FacebookException,
                                                                               IOException {
    boolean hasGroups = (null != groupIds && !groupIds.isEmpty());
    if (null != userId)
      return hasGroups ?
             this.callMethod(FacebookMethod.GROUPS_GET, new Pair<String, CharSequence>("uid",
                                                                                       userId.toString()),
                             new Pair<String, CharSequence>("gids", delimit(groupIds))) :
             this.callMethod(FacebookMethod.GROUPS_GET,
                             new Pair<String, CharSequence>("uid", userId.toString()));
    else
      return hasGroups ?
             this.callMethod(FacebookMethod.GROUPS_GET, new Pair<String, CharSequence>("gids",
                                                                                       delimit(groupIds))) :
             this.callMethod(FacebookMethod.GROUPS_GET);
  }

  /**
   * Retrieves the membership list of a group
   * @param groupId the group id
   * @return a Document containing four membership lists of
   *  'members', 'admins', 'officers', and 'not_replied'
   */
  public Document groups_getMembers(Number groupId) throws FacebookException, IOException {
    assert (null != groupId);
    return this.callMethod(FacebookMethod.GROUPS_GET_MEMBERS,
                           new Pair<String, CharSequence>("gid", groupId.toString()));
  }

  /**
   * Retrieves the results of a Facebook Query Language query
   * @param query : the FQL query statement
   * @return varies depending on the FQL query
   */
  public Document fql_query(CharSequence query) throws FacebookException, IOException {
    assert (null != query);
    return this.callMethod(FacebookMethod.FQL_QUERY,
                           new Pair<String, CharSequence>("query", query));
  }

  /**
   * Retrieves the outstanding notifications for the session user.
   * @return a Document containing
   *  notification count pairs for 'messages', 'pokes' and 'shares',
   *  a uid list of 'friend_requests', a gid list of 'group_invites',
   *  and an eid list of 'event_invites'
   */
  public Document notifications_get() throws FacebookException, IOException {
    return this.callMethod(FacebookMethod.NOTIFICATIONS_GET);
  }

  /**
   * Send a request or invitations to the specified users.
   * @param recipientIds the user ids to which the request is to be sent
   * @param type the type of request/invitation - e.g. the word "event" in "1 event invitation."
   * @param content Content of the request/invitation. This should be FBML containing only links and the
   *   special tag &lt;fb:req-choice url="" label="" /&gt; to specify the buttons to be included in the request.
   * @param image URL of an image to show beside the request. It will be resized to be 100 pixels wide.
   * @param isInvite whether this is a "request" or an "invite"
   * @return a URL, possibly null, to which the user should be redirected to finalize
   *    the sending of the message
   *
   * @deprecated this method has been removed from the Facebook API server
   */
  public URL notifications_sendRequest(Collection<Long> recipientIds, CharSequence type,
  CharSequence content, URL image, boolean isInvite) throws FacebookException, IOException {
    assert (null != recipientIds && !recipientIds.isEmpty());
    assert (null != type);
    assert (null != content);
    assert (null != image);

    Document d =
      this.callMethod(FacebookMethod.NOTIFICATIONS_SEND_REQUEST,
                      new Pair<String, CharSequence>("to_ids", delimit(recipientIds)),
                      new Pair<String, CharSequence>("type", type),
                      new Pair<String, CharSequence>("content", content),
                      new Pair<String, CharSequence>("image", image.toString()),
                      new Pair<String, CharSequence>("invite", isInvite ? "1" : "0"));
    String url = d.getFirstChild().getTextContent();
    return (null == url || "".equals(url)) ? null : new URL(url);
  }

  /**
   * @deprecated
   */
  public URL notifications_send(Collection<Long> recipientIds,
                                CharSequence notification,
                                CharSequence email) throws FacebookException, IOException {
    this.notifications_send(recipientIds, notification);
    return null;
  }

  protected static boolean extractBoolean(Document doc) {
    String content = doc.getFirstChild().getTextContent();
    return 1 == Integer.parseInt(content);
  }

  public InputStream postFileRequest(String methodName,
                                     Map<String, CharSequence> params) {
    assert (null != _uploadFile);
    try {
      BufferedInputStream bufin = new BufferedInputStream(new FileInputStream(_uploadFile));

      String boundary = Long.toString(System.currentTimeMillis(), 16);
      URLConnection con = SERVER_URL.openConnection();
      con.setDoInput(true);
      con.setDoOutput(true);
      con.setUseCaches(false);
      con.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
      con.setRequestProperty("MIME-version", "1.0");

      DataOutputStream out = new DataOutputStream(con.getOutputStream());

      for (Map.Entry<String, CharSequence> entry: params.entrySet()) {
        out.writeBytes(PREF + boundary + CRLF);
        out.writeBytes("Content-disposition: form-data; name=\"" + entry.getKey() + "\"");
        out.writeBytes(CRLF + CRLF);
        out.writeBytes(entry.getValue().toString());
        out.writeBytes(CRLF);
      }

      out.writeBytes(PREF + boundary + CRLF);
      out.writeBytes("Content-disposition: form-data; filename=\"" + _uploadFile.getName() + "\"" +
                     CRLF);
      out.writeBytes("Content-Type: image/jpeg" + CRLF);
      // out.writeBytes("Content-Transfer-Encoding: binary" + CRLF); // not necessary

      // Write the file
      out.writeBytes(CRLF);
      byte b[] = new byte[UPLOAD_BUFFER_SIZE];
      int byteCounter = 0;
      int i;
      while (-1 != (i = bufin.read(b))) {
        byteCounter += i;
        out.write(b, 0, i);
      }
      out.writeBytes(CRLF + PREF + boundary + PREF + CRLF);

      out.flush();
      out.close();

      InputStream is = con.getInputStream();
      return is;
    }
    catch (Exception e) {
        System.err.println("caught exception: " + e);
        e.printStackTrace();
        return null;
    }
  }

  /**
   * Call this function and store the result, using it to generate the
   * appropriate login url and then to retrieve the session information.
   * @return String the auth_token string
   */
  public String auth_createToken() throws FacebookException, IOException {
    Document d = this.callMethod(FacebookMethod.AUTH_CREATE_TOKEN);
    return d.getFirstChild().getTextContent();
  }

  /**
   * Call this function to retrieve the session information after your user has
   * logged in.
   * @param authToken the token returned by auth_createToken or passed back to your callback_url.
   */
  public String auth_getSession(String authToken) throws FacebookException, IOException {
    Document d =
      this.callMethod(FacebookMethod.AUTH_GET_SESSION, new Pair<String, CharSequence>("auth_token",
                                                                                      authToken.toString()));
    this._sessionKey =
        d.getElementsByTagName("session_key").item(0).getFirstChild().getTextContent();
    this._userId =
        Long.parseLong(d.getElementsByTagName("uid").item(0).getFirstChild().getTextContent());
    this._expires =
    	Long.parseLong(d.getElementsByTagName("expires").item(0).getFirstChild().getTextContent());
    if (this._isDesktop)
      this._sessionSecret =
          d.getElementsByTagName("secret").item(0).getFirstChild().getTextContent();
    return this._sessionKey;
  }

  /**
   * Returns a JAXB object of the type that corresponds to the last API call made on the client.  Each
   * Facebook Platform API call that returns a Document object has a JAXB response object associated
   * with it.  The naming convention is generally intuitive.  For example, if you invoke the
   * 'user_getInfo' API call, the associated JAXB response object is 'UsersGetInfoResponse'.<br />
   * <br />
   * An example of how to use this method:<br />
   *  <br />
   *    FacebookRestClient client = new FacebookRestClient("apiKey", "secretKey", "sessionId");<br />
   *    client.friends_get();<br />
   *    FriendsGetResponse response = (FriendsGetResponse)client.getResponsePOJO();<br />
   *    List<Long> friends = response.getUid(); <br />
   * <br />
   * This is particularly useful in the case of API calls that return a Document object, as working
   * with the JAXB response object is generally much simple than trying to walk/parse the DOM by
   * hand.<br />
   * <br />
   * This method can be safely called multiple times, though note that it will only return the
   * response-object corresponding to the most recent Facebook Platform API call made.<br />
   * <br />
   * Note that you must cast the return value of this method to the correct type in order to do anything
   * useful with it.
   *
   * @return a JAXB POJO ("Plain Old Java Object") of the type that corresponds to the last API call made on
   *         the client.  Note that you must cast this object to its proper type before you will be able to
   *         do anything useful with it.
   */
  public Object getResponsePOJO(){
      if (this.rawResponse == null) {
          return null;
      }
      JAXBContext jc;
      Object pojo = null;
      try {
          jc = JAXBContext.newInstance("com.facebook.api.schema");
          Unmarshaller unmarshaller = jc.createUnmarshaller();
          pojo =  unmarshaller.unmarshal(new ByteArrayInputStream(this.rawResponse.getBytes("UTF-8")));
      } catch (JAXBException e) {
          System.err.println("getResponsePOJO() - Could not unmarshall XML stream into POJO");
          e.printStackTrace();
      }
      catch (NullPointerException e) {
          System.err.println("getResponsePOJO() - Could not unmarshall XML stream into POJO.");
          e.printStackTrace();
      } catch (UnsupportedEncodingException e) {
          System.err.println("getResponsePOJO() - Could not unmarshall XML stream into POJO.");
          e.printStackTrace();
      }
      return pojo;
  }

  /**
   * Lookup a single preference value for the current user.
   *
   * @param prefId the id of the preference to lookup.  This should be an integer value from 0-200.
   *
   * @return The value of that preference, or null if it is not yet set.
   *
   * @throws FacebookException if an error happens when executing the API call.
   * @throws IOException if a communication/network error happens.
   */
  public String data_getUserPreference(Integer prefId) throws FacebookException, IOException {
      if ((prefId < 0) || (prefId > 200)) {
          throw new FacebookException(ErrorCode.GEN_INVALID_PARAMETER, "The preference id must be an integer value from 0-200.");
      }
      this.callMethod(FacebookMethod.DATA_GET_USER_PREFERENCE, new Pair<String, CharSequence>("pref_id", Integer.toString(prefId)));
      this.checkError();

      if (! this.rawResponse.contains("</data_getUserPreference_response>")) {
          //there is no value set for this preference yet
          return null;
      }
      String result = this.rawResponse.substring(0, this.rawResponse.indexOf("</data_getUserPreference_response>"));
      result = result.substring(result.indexOf("facebook.xsd\">") + "facebook.xsd\">".length());

      return reconstructValue(result);
  }

  /**
   * Get a map containing all preference values set for the current user.
   *
   * @return a map of preference values, keyed by preference id.  The map will contain all
   *         preferences that have been set for the current user.  If there are no preferences
   *         currently set, the map will be empty.  The map returned will never be null.
   *
   * @throws FacebookException if an error happens when executing the API call.
   * @throws IOException if a communication/network error happens.
   */
  public Map<Integer, String> data_getUserPreferences() throws FacebookException, IOException {
      Document response = this.callMethod(FacebookMethod.DATA_GET_USER_PREFERENCES);
      this.checkError();

      Map<Integer, String> results = new HashMap<Integer, String>();
      NodeList ids = response.getElementsByTagName("pref_id");
      NodeList values = response.getElementsByTagName("value");
      for (int count = 0; count < ids.getLength(); count++) {
          results.put(Integer.parseInt(ids.item(count).getFirstChild().getTextContent()),
                  reconstructValue(values.item(count).getFirstChild().getTextContent()));
      }

      return results;
  }

  private void checkError() throws FacebookException {
      if (this.rawResponse.contains("error_response")) {
          //<error_code>xxx</error_code>
          Integer code = Integer.parseInt(this.rawResponse.substring(this.rawResponse.indexOf("<error_code>") + "<error_code>".length(),
                  this.rawResponse.indexOf("</error_code>") + "</error_code>".length()));
          throw new FacebookException(code, "The request could not be completed!");
      }
  }

  private String reconstructValue(String input) {
      if ((input == null) || ("".equals(input))) {
          return null;
      }
      if (input.charAt(0) == '_') {
          return input.substring(1);
      }
      return input;
  }

  /**
   * Set a user-preference value.  The value can be any string up to 127 characters in length,
   * while the preference id can only be an integer between 0 and 200.  Any preference set applies
   * only to the current user of the application.
   *
   * To clear a user-preference, specify null as the value parameter.  The values of "0" and "" will
   * be stored as user-preferences with a literal value of "0" and "" respectively.
   *
   * @param prefId the id of the preference to set, an integer between 0 and 200.
   * @param value the value to store, a String of up to 127 characters in length.
   *
   * @throws FacebookException if an error happens when executing the API call.
   * @throws IOException if a communication/network error happens.
   */
  public void data_setUserPreference(Integer prefId, String value) throws FacebookException, IOException {
      if ((prefId < 0) || (prefId > 200)) {
          throw new FacebookException(ErrorCode.GEN_INVALID_PARAMETER, "The preference id must be an integer value from 0-200.");
      }
      if ((value != null) && (value.length() > 127)) {
          throw new FacebookException(ErrorCode.GEN_INVALID_PARAMETER, "The preference value cannot be longer than 128 characters.");
      }

      value = normalizePreferenceValue(value);

      Collection<Pair<String, CharSequence>> params = new ArrayList<Pair<String, CharSequence>>();
      params.add(new Pair<String, CharSequence>("pref_id", Integer.toString(prefId)));
      params.add(new Pair<String, CharSequence>("value", value));
      this.callMethod(FacebookMethod.DATA_SET_USER_PREFERENCE, params);
      this.checkError();
  }

  /**
   * Set multiple user-preferences values.  The values can be strings up to 127 characters in length,
   * while the preference id can only be an integer between 0 and 200.  Any preferences set apply
   * only to the current user of the application.
   *
   * To clear a user-preference, specify null as its value in the map.  The values of "0" and "" will
   * be stored as user-preferences with a literal value of "0" and "" respectively.
   *
   * @param values the values to store, specified in a map. The keys should be preference-id values from 0-200, and
   *              the values should be strings of up to 127 characters in length.
   * @param replace set to true if you want to remove any pre-existing preferences before writing the new ones
   *                set to false if you want the new preferences to be merged with any pre-existing preferences
   *
   * @throws FacebookException if an error happens when executing the API call.
   * @throws IOException if a communication/network error happens.
   */
  public void data_setUserPreferences(Map<Integer, String> values, boolean replace) throws FacebookException, IOException {
      JSONObject map = new JSONObject();

      for (Integer key : values.keySet()) {
          if ((key < 0) || (key > 200)) {
              throw new FacebookException(ErrorCode.GEN_INVALID_PARAMETER, "The preference id must be an integer value from 0-200.");
          }
          if ((values.get(key) != null) && (values.get(key).length() > 127)) {
              throw new FacebookException(ErrorCode.GEN_INVALID_PARAMETER, "The preference value cannot be longer than 128 characters.");
          }
          try {
              map.put(Integer.toString(key), normalizePreferenceValue(values.get(key)));
          }
          catch (JSONException e) {
              FacebookException ex = new FacebookException(ErrorCode.GEN_INVALID_PARAMETER, "Error when translating {key="
                      + key + ", value=" + values.get(key) + "}to JSON!");
              ex.setStackTrace(e.getStackTrace());
              throw ex;
          }
      }

      Collection<Pair<String, CharSequence>> params = new ArrayList<Pair<String, CharSequence>>();
      params.add(new Pair<String, CharSequence>("values", map.toString()));
      if (replace) {
          params.add(new Pair<String, CharSequence>("replace", "true"));
      }

      this.callMethod(FacebookMethod.DATA_SET_USER_PREFERENCES, params);
      this.checkError();
  }

  private String normalizePreferenceValue(String input) {
      if (input == null) {
          return "0";
      }
      return "_" + input;
  }

  /**
   * Check to see if the application is permitted to send SMS messages to the current application user.
   *
   * @return true if the application is presently able to send SMS messages to the current user
   *         false otherwise
   *
   * @throws FacebookException if an error happens when executing the API call.
   * @throws IOException if a communication/network error happens.
   */
  public boolean sms_canSend() throws FacebookException, IOException {
      return sms_canSend(this.users_getLoggedInUser());
  }

  /**
   * Check to see if the application is permitted to send SMS messages to the specified user.
   *
   * @param userId the UID of the user to check permissions for
   *
   * @return true if the application is presently able to send SMS messages to the specified user
   *         false otherwise
   *
   * @throws FacebookException if an error happens when executing the API call.
   * @throws IOException if a communication/network error happens.
   */
  public boolean sms_canSend(Long userId) throws FacebookException, IOException {
      this.callMethod(FacebookMethod.SMS_CAN_SEND, new Pair<String, CharSequence>("uid", userId.toString()));
      return this.rawResponse.contains(">0<");  //a status code of "0" indicates that the app can send messages
  }

  /**
   * Send an SMS message to the current application user.
   *
   * @param message the message to send.
   * @param smsSessionId the SMS session id to use, note that that is distinct from the user's facebook session id.  It is used to
   *                     allow applications to keep track of individual SMS conversations/threads for a single user.  Specify
   *                     null if you do not want/need to use a session for the current message.
   * @param makeNewSession set to true to request that Facebook allocate a new SMS session id for this message.  The allocated
   *                       id will be returned as the result of this API call.  You should only set this to true if you are
   *                       passing a null 'smsSessionId' value.  Otherwise you already have a SMS session id, and do not need a new one.
   *
   * @return an integer specifying the value of the session id alocated by Facebook, if one was requested.  If a new session id was
   *                    not requested, this method will return null.
   *
   * @throws FacebookException if an error happens when executing the API call.
   * @throws IOException if a communication/network error happens.
   */
  public Integer sms_send(String message, Integer smsSessionId, boolean makeNewSession) throws FacebookException, IOException {
      return sms_send(this.users_getLoggedInUser(), message, smsSessionId, makeNewSession);
  }

  /**
   * Send an SMS message to the specified user.
   *
   * @param userId the id of the user to send the message to.
   * @param message the message to send.
   * @param smsSessionId the SMS session id to use, note that that is distinct from the user's facebook session id.  It is used to
   *                     allow applications to keep track of individual SMS conversations/threads for a single user.  Specify
   *                     null if you do not want/need to use a session for the current message.
   * @param makeNewSession set to true to request that Facebook allocate a new SMS session id for this message.  The allocated
   *                       id will be returned as the result of this API call.  You should only set this to true if you are
   *                       passing a null 'smsSessionId' value.  Otherwise you already have a SMS session id, and do not need a new one.
   *
   * @return an integer specifying the value of the session id alocated by Facebook, if one was requested.  If a new session id was
   *                    not requested, this method will return null.
   *
   * @throws FacebookException if an error happens when executing the API call.
   * @throws IOException if a communication/network error happens.
   */
  public Integer sms_send(Long userId, String message, Integer smsSessionId, boolean makeNewSession) throws FacebookException, IOException {
      Collection<Pair<String, CharSequence>> params = new ArrayList<Pair<String, CharSequence>>();
      params.add(new Pair<String, CharSequence>("uid", userId.toString()));
      params.add(new Pair<String, CharSequence>("message", message));
      if (smsSessionId != null) {
          params.add(new Pair<String, CharSequence>("session_id", smsSessionId.toString()));
      }
      if (makeNewSession) {
          params.add(new Pair<String, CharSequence>("req_session", "true"));
      }

      this.callMethod(FacebookMethod.SMS_SEND, params);

      //XXX:  needs testing to make sure it's correct (Facebook always gives me a code 270 permissions error no matter what I do)
      Integer response = null;
      if ((this.rawResponse.indexOf("</sms") != -1) && (makeNewSession)) {
          String result = this.rawResponse.substring(0, this.rawResponse.indexOf("</sms"));
          result = result.substring(result.lastIndexOf(">") + 1);
          response = Integer.parseInt(result);
      }

      return response;
  }

  /**
   * Check to see if the user has granted the app a specific external permission.  In order to be granted a
   * permission, an application must direct the user to a URL of the form:
   *
   * http://www.facebook.com/authorize.php?api_key=[YOUR_API_KEY]&v=1.0&ext_perm=[PERMISSION NAME]
   *
   * @param perm the permission to check for
   *
   * @return true if the user has granted the application the specified permission
   *         false otherwise
   *
   * @throws FacebookException if an error happens when executing the API call.
   * @throws IOException if a communication/network error happens.
   */
  public boolean users_hasAppPermission(Permission perm) throws FacebookException, IOException {
      this.callMethod(FacebookMethod.USERS_HAS_PERMISSION, new Pair<String, CharSequence>("ext_perm", perm.getName()));
      return this.rawResponse.contains(">1<");  //a code of '1' is sent back to indicate that the user has the request permission
  }

  /**
   * Set the user's profile status message.  This requires that the user has granted the application the
   * 'status_update' permission, otherwise the call will return an error.  You can use 'users_hasAppPermission'
   * to check to see if the user has granted your app the abbility to update their status.
   *
   * @param newStatus the new status message to set.
   * @param clear whether or not to clear the old status message.
   *
   * @return true if the call succeeds
   *         false otherwise
   *
   * @throws FacebookException if an error happens when executing the API call.
   * @throws IOException if a communication/network error happens.
   */
  public boolean users_setStatus(String newStatus, boolean clear) throws FacebookException, IOException {
      return this.users_setStatus(newStatus, clear, false);
  }

  /**
   * Associates the specified FBML markup with the specified handle/id.  The markup can then be referenced using the fb:ref FBML
   * tag, to allow a given snippet to be reused easily across multiple users, and also to allow the application to update
   * the fbml for multiple users more easily without having to make a seperate call for each user, by just changing the FBML
   * markup that is associated with the handle/id.
   *
   * @param handle the id to associate the specified markup with.  Put this in fb:ref FBML tags to reference your markup.
   * @param markup the FBML markup to store.
   *
   * @throws FacebookException if an error happens when executing the API call.
   * @throws IOException if a communication/network error happens.
   */
  public boolean fbml_setRefHandle(String handle, String markup) throws FacebookException, IOException {
      if ((handle == null) || ("".equals(handle))) {
          throw new FacebookException(ErrorCode.GEN_INVALID_PARAMETER, "The FBML handle may not be null or empty!");
      }
      if (markup == null) {
          markup = "";
      }
      Collection<Pair<String, CharSequence>> params = new ArrayList<Pair<String, CharSequence>>();
      params.add(new Pair<String, CharSequence>("handle", handle));
      params.add(new Pair<String, CharSequence>("fbml", markup));

      return extractBoolean(this.callMethod(FacebookMethod.FBML_SET_REF_HANDLE, params));
  }

  /**
   * Create a new marketplace listing, or modify an existing one.
   *
   * @param listingId the id of the listing to modify, set to 0 (or null) to create a new listing.
   * @param showOnProfile set to true to show the listing on the user's profile (Facebook appears to ignore this setting).
   * @param attributes JSON-encoded attributes for this listing.
   *
   * @return the id of the listing created (or modified).
   *
   * @throws FacebookException if an error happens when executing the API call.
   * @throws IOException if a communication/network error happens.
   */
  public Long marketplace_createListing(Long listingId, boolean showOnProfile, String attributes) throws FacebookException, IOException {
     if (listingId == null) {
         listingId = 0l;
     }
     MarketListing test = new MarketListing(attributes);
     if (!test.verify()) {
         throw new FacebookException(ErrorCode.GEN_INVALID_PARAMETER, "The specified listing is invalid!");
     }

     Collection<Pair<String, CharSequence>> params = new ArrayList<Pair<String, CharSequence>>();
     params.add(new Pair<String, CharSequence>("listing_id", listingId.toString()));
     if (showOnProfile) {
         params.add(new Pair<String, CharSequence>("show_on_profile", "true"));
     }
     params.add(new Pair<String, CharSequence>("listing_attrs", attributes));

     this.callMethod(FacebookMethod.MARKET_CREATE_LISTING, params);
     String result = this.rawResponse.substring(0, this.rawResponse.indexOf("</marketplace"));
     result = result.substring(result.lastIndexOf(">") + 1);
     return Long.parseLong(result);
  }

  /**
   * Create a new marketplace listing, or modify an existing one.
   *
   * @param listingId the id of the listing to modify, set to 0 (or null) to create a new listing.
   * @param showOnProfile set to true to show the listing on the user's profile, set to false to prevent the listing from being shown on the profile.
   * @param listing the listing to publish.
   *
   * @return the id of the listing created (or modified).
   *
   * @throws FacebookException if an error happens when executing the API call.
   * @throws IOException if a communication/network error happens.
   */
  public Long marketplace_createListing(Long listingId, boolean showOnProfile, MarketListing listing) throws FacebookException, IOException {
      return this.marketplace_createListing(listingId, showOnProfile, listing.getAttribs());
  }

  /**
   * Create a new marketplace listing.
   *
   * @param showOnProfile set to true to show the listing on the user's profile, set to false to prevent the listing from being shown on the profile.
   * @param listing the listing to publish.
   *
   * @return the id of the listing created (or modified).
   *
   * @throws FacebookException if an error happens when executing the API call.
   * @throws IOException if a communication/network error happens.
   */
  public Long marketplace_createListing(boolean showOnProfile, MarketListing listing) throws FacebookException, IOException {
      return this.marketplace_createListing(0l, showOnProfile, listing.getAttribs());
  }

  /**
   * Create a new marketplace listing, or modify an existing one.
   *
   * @param listingId the id of the listing to modify, set to 0 (or null) to create a new listing.
   * @param showOnProfile set to true to show the listing on the user's profile, set to false to prevent the listing from being shown on the profile.
   * @param listing the listing to publish.
   *
   * @return the id of the listing created (or modified).
   *
   * @throws FacebookException if an error happens when executing the API call.
   * @throws IOException if a communication/network error happens.
   */
  public Long marketplace_createListing(Long listingId, boolean showOnProfile, JSONObject listing) throws FacebookException, IOException {
      return this.marketplace_createListing(listingId, showOnProfile, listing.toString());
  }

  /**
   * Create a new marketplace listing.
   *
   * @param showOnProfile set to true to show the listing on the user's profile, set to false to prevent the listing from being shown on the profile.
   * @param listing the listing to publish.
   *
   * @return the id of the listing created (or modified).
   *
   * @throws FacebookException if an error happens when executing the API call.
   * @throws IOException if a communication/network error happens.
   */
  public Long marketplace_createListing(boolean showOnProfile, JSONObject listing) throws FacebookException, IOException {
      return this.marketplace_createListing(0l, showOnProfile, listing.toString());
  }

  /**
   * Return a list of all valid Marketplace categories.
   *
   * @return a list of marketplace categories allowed by Facebook.
   *
   * @throws FacebookException if an error happens when executing the API call.
   * @throws IOException if a communication/network error happens.
   */
  public List<String> marketplace_getCategories() throws FacebookException, IOException{
      this.callMethod(FacebookMethod.MARKET_GET_CATEGORIES);
      MarketplaceGetCategoriesResponse resp = (MarketplaceGetCategoriesResponse)this.getResponsePOJO();
      return resp.getMarketplaceCategory();
  }
  
  /**
   * Return a list of all valid Marketplace categories.
   *
   * @return a list of marketplace categories allowed by Facebook.
   *
   * @throws FacebookException if an error happens when executing the API call.
   * @throws IOException if a communication/network error happens.
   */
  public Document marketplace_getCategoriesObject() throws FacebookException, IOException{
      return this.callMethod(FacebookMethod.MARKET_GET_CATEGORIES);
  }

  /**
   * Return a list of all valid Marketplace subcategories.
   *
   * @return a list of marketplace subcategories allowed by Facebook.
   *
   * @throws FacebookException if an error happens when executing the API call.
   * @throws IOException if a communication/network error happens.
   */
  public List<String> marketplace_getSubCategories() throws FacebookException, IOException{
      this.callMethod(FacebookMethod.MARKET_GET_SUBCATEGORIES);
      MarketplaceGetSubCategoriesResponse resp = (MarketplaceGetSubCategoriesResponse)this.getResponsePOJO();
      return resp.getMarketplaceSubcategory();
  }

  /**
   * Retrieve listings from the marketplace.  The listings can be filtered by listing-id or user-id (or both).
   *
   * @param listingIds the ids of listings to filter by, only listings matching the specified ids will be returned.
   * @param uids the ids of users to filter by, only listings submitted by those users will be returned.
   *
   * @return A list of marketplace listings that meet the specified filter criteria.
   *
   * @throws FacebookException if an error happens when executing the API call.
   * @throws IOException if a communication/network error happens.
   */
  public List<Listing> marketplace_getListings(List<Long> listingIds, List<Long> uids) throws FacebookException, IOException {
      String listings = stringify(listingIds);
      String users = stringify(uids);

      Collection<Pair<String, CharSequence>> params = new ArrayList<Pair<String, CharSequence>>();
      if (listings != null) {
          params.add(new Pair<String, CharSequence>("listing_ids", listings));
      }
      if (uids != null) {
          params.add(new Pair<String, CharSequence>("uids", users));
      }

      this.callMethod(FacebookMethod.MARKET_GET_LISTINGS, params);
      MarketplaceGetListingsResponse resp = (MarketplaceGetListingsResponse)this.getResponsePOJO();
      return resp.getListing();
  }

  private String stringify(List input) {
      if ((input == null) || (input.isEmpty())) {
          return null;
      }
      String result = "";
      for (Object elem : input) {
          if (! "".equals(result)) {
              result += ",";
          }
          result += elem.toString();
      }
      return result;
  }

  /**
   * Search the marketplace listings by category, subcategory, and keyword.
   *
   * @param category the category to search in, optional (unless subcategory is specified).
   * @param subcategory the subcategory to search in, optional.
   * @param searchTerm the keyword to search for, optional.
   *
   * @return a list of marketplace entries that match the specified search parameters.
   *
   * @throws FacebookException if an error happens when executing the API call.
   * @throws IOException if a communication/network error happens.
   */
  public List<Listing> marketplace_search(MarketListingCategory category, MarketListingSubcategory subcategory, String searchTerm) throws FacebookException, IOException {
      if ("".equals(searchTerm)) {
          searchTerm = null;
      }
      if ((subcategory != null) && (category == null)) {
          throw new FacebookException(ErrorCode.GEN_INVALID_PARAMETER, "You cannot search by subcategory without also specifying a category!");
      }

      Collection<Pair<String, CharSequence>> params = new ArrayList<Pair<String, CharSequence>>();
      if (category != null) {
          params.add(new Pair<String, CharSequence>("category", category.getName()));
      }
      if (subcategory != null) {
          params.add(new Pair<String, CharSequence>("subcategory", subcategory.getName()));
      }
      if (searchTerm != null) {
          params.add(new Pair<String, CharSequence>("query", searchTerm));
      }

      this.callMethod(FacebookMethod.MARKET_SEARCH, params);
      MarketplaceSearchResponse resp = (MarketplaceSearchResponse)this.getResponsePOJO();
      return resp.getListing();
  }

  /**
   * Remove a listing from the marketplace by id.
   *
   * @param listingId the id of the listing to remove.
   * @param status the status to apply when removing the listing.  Should be one of MarketListingStatus.SUCCESS or MarketListingStatus.NOT_SUCCESS.
   *
   * @return true if the listing was successfully removed
   *         false otherwise
   *
   * @throws FacebookException if an error happens when executing the API call.
   * @throws IOException if a communication/network error happens.
   */
  public boolean marketplace_removeListing(Long listingId, MarketListingStatus status) throws FacebookException, IOException {
      if (status == null) {
          status = MarketListingStatus.DEFAULT;
      }
      if (listingId == null) {
          return false;
      }

      Collection<Pair<String, CharSequence>> params = new ArrayList<Pair<String, CharSequence>>();
      params.add(new Pair<String, CharSequence>("listing_id", listingId.toString()));
      params.add(new Pair<String, CharSequence>("status", status.getName()));
      this.callMethod(FacebookMethod.MARKET_REMOVE_LISTING, params);

      return this.rawResponse.contains(">1<"); //a code of '1' indicates success
  }
  
  /**
   * Clears the logged-in user's Facebook status.
   * Requires the status_update extended permission.
   * @return whether the status was successfully cleared
   * @see #users_hasAppPermission
   * @see FacebookExtendedPerm#STATUS_UPDATE
   * @see <a href="http://wiki.developers.facebook.com/index.php/Users.setStatus">
   *      Developers Wiki: Users.setStatus</a> 
   */
  public boolean users_clearStatus()
    throws FacebookException, IOException {
    return this.users_setStatus(null, true);
  }
  
  /**
   * Modify a marketplace listing
   * @param listingId identifies the listing to be modified
   * @param showOnProfile whether the listing can be shown on the user's profile
   * @param attrs the properties of the listing
   * @return the id of the edited listing
   * @see MarketplaceListing
   * @see <a href="http://wiki.developers.facebook.com/index.php/Marketplace.createListing">
   *      Developers Wiki: marketplace.createListing</a>
   *      
   * @deprecated provided for legacy support only.  Please use the version that takes a MarketListing instead.
   */
  public Long marketplace_editListing(Long listingId, Boolean showOnProfile, MarketplaceListing attrs)
    throws FacebookException, IOException {
    return this.marketplace_createListing(listingId, showOnProfile, attrs.getAttribs());
  }
  
  /**
   * Modify a marketplace listing
   * @param listingId identifies the listing to be modified
   * @param showOnProfile whether the listing can be shown on the user's profile
   * @param attrs the properties of the listing
   * @return the id of the edited listing
   * @see MarketplaceListing
   * @see <a href="http://wiki.developers.facebook.com/index.php/Marketplace.createListing">
   *      Developers Wiki: marketplace.createListing</a>
   */
  public Long marketplace_editListing(Long listingId, Boolean showOnProfile, MarketListing attrs)
    throws FacebookException, IOException {
    return this.marketplace_createListing(listingId, showOnProfile, attrs);
  }
  
  /**
   * Publish a story to the logged-in user's newsfeed.
   * @param title the title of the feed story
   * @param body the body of the feed story
   * @param images (optional) up to four pairs of image URLs and (possibly null) link URLs
   * @return whether the story was successfully published; false in case of permission error
   * @see <a href="http://wiki.developers.facebook.com/index.php/Feed.publishStoryToUser">
   *      Developers Wiki: Feed.publishStoryToUser</a>
   */
  public boolean feed_publishStoryToUser(CharSequence title, CharSequence body,
                                         Collection<? extends IPair<? extends Object, URL>> images)
    throws FacebookException, IOException {
    return feed_publishStoryToUser(title, body, images, null);
  }
  
  /**
   * Create a marketplace listing
   * @param showOnProfile whether the listing can be shown on the user's profile
   * @param attrs the properties of the listing
   * @return the id of the created listing
   * @see MarketplaceListing
   * @see <a href="http://wiki.developers.facebook.com/index.php/Marketplace.createListing">
   *      Developers Wiki: marketplace.createListing</a>
   *      
   * @deprecated provided for legacy support only.
   */
  public Long marketplace_createListing(Boolean showOnProfile, MarketplaceListing attrs)
    throws FacebookException, IOException {
    return this.marketplace_createListing(null, showOnProfile, attrs.getAttribs());
  }

    /* (non-Javadoc)
     * @see com.facebook.api.IFacebookRestClient#auth_getUserId(java.lang.String)
     */
    public long auth_getUserId(String authToken) throws FacebookException, IOException {
        if (null == this._sessionKey)
            auth_getSession(authToken);
        return this.users_getLoggedInUser();
    }
    
    /** 
     * @deprecated use feed_publishTemplatizedAction instead.
     */
    public boolean feed_publishActionOfUser(CharSequence title, CharSequence body, Collection<? extends IPair<? extends Object, URL>> images) throws FacebookException, IOException {
        return this.feed_publishActionOfUser(title, body, images, null);
    }
    
    /* (non-Javadoc)
     * @see com.facebook.api.IFacebookRestClient#feed_publishTemplatizedAction(java.lang.Long, java.lang.CharSequence)
     */
    public boolean feed_publishTemplatizedAction(Long actorId, CharSequence titleTemplate) throws FacebookException, IOException {
        return this.feed_publishTemplatizedAction(actorId, titleTemplate == null ? null : titleTemplate.toString(), null, null, null, null, null, null);
    }
    
    /* (non-Javadoc)
     * @see com.facebook.api.IFacebookRestClient#feed_publishTemplatizedAction(java.lang.Long, java.lang.CharSequence, java.util.Map, java.lang.CharSequence, java.util.Map, java.lang.CharSequence, java.util.Collection, java.util.Collection)
     */
    public boolean feed_publishTemplatizedAction(Long actorId, CharSequence titleTemplate, Map<String,CharSequence> titleData, CharSequence bodyTemplate, Map<String,CharSequence> bodyData, CharSequence bodyGeneral, Collection<Long> targetIds, Collection<? extends IPair<? extends Object, URL>> images) throws FacebookException, IOException {
        return this.feed_publishTemplatizedActionInternal(actorId, titleTemplate == null ? null : titleTemplate.toString(), 
                mapToJsonString(titleData), bodyTemplate == null ? null : bodyTemplate.toString(), mapToJsonString(bodyData), bodyGeneral == null ? null : bodyGeneral.toString(), images, targetIds, null);
    }
    
    private String mapToJsonString(Map<String, CharSequence> map) {
        if (null == map || map.isEmpty()) {
            return null;
        }
        JSONObject titleDataJson = new JSONObject();
        for (String key : map.keySet()) {
            try {
                titleDataJson.put(key, map.get(key));
            }
            catch (Exception ignored) {}
        }
        return titleDataJson.toString();
    }
    
    private boolean feed_publishTemplatizedActionInternal(Long actor, String titleTemp, String titleData, String bodyTemp, String bodyData, String bodyGeneral, Collection<? extends IPair<? extends Object, URL>> images, Collection<Long> targetIds, Long pageId) throws FacebookException, IOException {
        if ((targetIds != null) && (! targetIds.isEmpty())) {
            return this.templatizedFeedHandler(actor, FacebookMethod.FEED_PUBLISH_TEMPLATIZED_ACTION, titleTemp, titleData, bodyTemp, bodyData, bodyGeneral, images, delimit(targetIds).toString(), pageId);
        }
        else {
            return this.templatizedFeedHandler(actor, FacebookMethod.FEED_PUBLISH_TEMPLATIZED_ACTION, titleTemp, titleData, bodyTemp, bodyData, bodyGeneral, images, null, pageId);
        }
    }
    
    /** 
     * @deprecated provided for legacy support only.  Use the version that returns a List<String> instead.
     */
    public Document marketplace_getListings(Collection<Long> listingIds, Collection<Long> userIds) throws FacebookException, IOException {
        ArrayList<Pair<String, CharSequence>> params =
            new ArrayList<Pair<String, CharSequence>>(FacebookMethod.MARKETPLACE_GET_LISTINGS.numParams());
        if (null != listingIds && !listingIds.isEmpty()) {
            params.add(new Pair<String, CharSequence>("listing_ids", delimit(listingIds)));
        }
        if (null != userIds && !userIds.isEmpty()) {
            params.add(new Pair<String, CharSequence>("uids", delimit(userIds)));
        }

        assert !params.isEmpty() : "Either listingIds or userIds should be provided";
        return this.callMethod(FacebookMethod.MARKETPLACE_GET_LISTINGS, params);
    }
    
    /* (non-Javadoc)
     * @see com.facebook.api.IFacebookRestClient#marketplace_getSubCategories(java.lang.CharSequence)
     */
    public Document marketplace_getSubCategories(CharSequence category) throws FacebookException, IOException {
        if (category != null) {
            return this.callMethod(FacebookMethod.MARKET_GET_SUBCATEGORIES, new Pair<String, CharSequence>("category", category));
        }
        return this.callMethod(FacebookMethod.MARKET_GET_SUBCATEGORIES);
    }
    
    /* (non-Javadoc)
     * @see com.facebook.api.IFacebookRestClient#marketplace_removeListing(java.lang.Long)
     */
    public boolean marketplace_removeListing(Long listingId) throws FacebookException, IOException {
        return this.marketplace_removeListing(listingId, MarketListingStatus.DEFAULT);
    }
    
    /**
     * @deprecated provided for legacy support only.  Use marketplace_removeListing(Long, MarketListingStatus) instead.
     */
    public boolean marketplace_removeListing(Long listingId, CharSequence status) throws FacebookException, IOException {
        return this.marketplace_removeListing(listingId);
    }
    
    /** 
     * @deprecated provided for legacy support only.  Use the version that returns a List<Listing> instead.
     */
    public Document marketplace_search(CharSequence category, CharSequence subCategory, CharSequence query) throws FacebookException, IOException {
        if ("".equals(query)) {
            query = null;
        }
        if ((subCategory != null) && (category == null)) {
            throw new FacebookException(ErrorCode.GEN_INVALID_PARAMETER, "You cannot search by subcategory without also specifying a category!");
        }

        Collection<Pair<String, CharSequence>> params = new ArrayList<Pair<String, CharSequence>>();
        if (category != null) {
            params.add(new Pair<String, CharSequence>("category", category));
        }
        if (subCategory != null) {
            params.add(new Pair<String, CharSequence>("subcategory", subCategory));
        }
        if (query != null) {
            params.add(new Pair<String, CharSequence>("query", query));
        }

        return this.callMethod(FacebookMethod.MARKET_SEARCH, params);
    }
    
    /**
     * @deprecated provided for legacy support only.  Use users_hasAppPermission(Permission) instead.
     */
    public boolean users_hasAppPermission(CharSequence permission) throws FacebookException, IOException {
        this.callMethod(FacebookMethod.USERS_HAS_PERMISSION, new Pair<String, CharSequence>("ext_perm", permission));
        return this.rawResponse.contains(">1<");  //a code of '1' is sent back to indicate that the user has the request permission
    }
    
    /* (non-Javadoc)
     * @see com.facebook.api.IFacebookRestClient#users_setStatus(java.lang.String)
     */
    public boolean users_setStatus(String status) throws FacebookException, IOException {
        return this.users_setStatus(status, false);
    }
    
    /**
     * Used to retrieve photo objects using the search parameters (one or more of the
     * parameters must be provided).
     *
     * @param albumId retrieve from photos from this album (optional)
     * @param photoIds retrieve from this list of photos (optional)
     * @return an T of photo objects.
     * @see #photos_get(Integer, Long, Collection)
     * @see <a href="http://wiki.developers.facebook.com/index.php/Photos.get">
     *      Developers Wiki: Photos.get</a> 
     */
    public Document photos_getByAlbum(Long albumId, Collection<Long> photoIds)
      throws FacebookException, IOException {
      return photos_get(null /*subjId*/, albumId, photoIds);
    }
    
    /**
     * Used to retrieve photo objects using the search parameters (one or more of the
     * parameters must be provided).
     *
     * @param albumId retrieve from photos from this album (optional)
     * @return an T of photo objects.
     * @see #photos_get(Integer, Long, Collection)
     * @see <a href="http://wiki.developers.facebook.com/index.php/Photos.get">
     *      Developers Wiki: Photos.get</a> 
     */
    public Document photos_getByAlbum(Long albumId)
      throws FacebookException, IOException {
      return photos_get(null /*subjId*/, albumId, null /*photoIds*/);
    }
    
    /**
     * Sends a message via SMS to the user identified by <code>userId</code> in response 
     * to a user query associated with <code>mobileSessionId</code>.
     *
     * @param userId a user ID
     * @param response the message to be sent via SMS
     * @param mobileSessionId the mobile session
     * @throws FacebookException in case of error
     * @throws IOException
     * @see FacebookExtendedPerm#SMS
     * @see <a href="http://wiki.developers.facebook.com/index.php/Mobile#Application_generated_messages">
     * Developers Wiki: Mobile: Application Generated Messages</a>
     * @see <a href="http://wiki.developers.facebook.com/index.php/Mobile#Workflow">
     * Developers Wiki: Mobile: Workflow</a>
     */
    public void sms_sendResponse(Integer userId, CharSequence response, Integer mobileSessionId)
      throws FacebookException, IOException {
      this.callMethod(FacebookMethod.SMS_SEND_MESSAGE,
                      new Pair<String, CharSequence>("uid", userId.toString()),
                      new Pair<String, CharSequence>("message", response),
                      new Pair<String, CharSequence>("session_id", mobileSessionId.toString()));
    }

    /**
     * Sends a message via SMS to the user identified by <code>userId</code>.
     * The SMS extended permission is required for success.
     *
     * @param userId a user ID
     * @param message the message to be sent via SMS
     * @throws FacebookException in case of error
     * @throws IOException
     * @see FacebookExtendedPerm#SMS
     * @see <a href="http://wiki.developers.facebook.com/index.php/Mobile#Application_generated_messages">
     * Developers Wiki: Mobile: Application Generated Messages</a>
     * @see <a href="http://wiki.developers.facebook.com/index.php/Mobile#Workflow">
     * Developers Wiki: Mobile: Workflow</a>
     */
    public void sms_sendMessage(Long userId, CharSequence message)
      throws FacebookException, IOException {
      this.callMethod(FacebookMethod.SMS_SEND_MESSAGE,
                      new Pair<String, CharSequence>("uid", userId.toString()),
                      new Pair<String, CharSequence>("message", message),
                      new Pair<String, CharSequence>("req_session", "0"));
    }

    /**
     * Sends a message via SMS to the user identified by <code>userId</code>, with
     * the expectation that the user will reply. The SMS extended permission is required for success.
     * The returned mobile session ID can be stored and used in {@link #sms_sendResponse} when
     * the user replies.
     *
     * @param userId a user ID
     * @param message the message to be sent via SMS
     * @return a mobile session ID (can be used in {@link #sms_sendResponse})
     * @throws FacebookException in case of error, e.g. SMS is not enabled
     * @throws IOException
     * @see FacebookExtendedPerm#SMS
     * @see <a href="http://wiki.developers.facebook.com/index.php/Mobile#Application_generated_messages">
     *      Developers Wiki: Mobile: Application Generated Messages</a>
     * @see <a href="http://wiki.developers.facebook.com/index.php/Mobile#Workflow">
     *      Developers Wiki: Mobile: Workflow</a>
     */
    public int sms_sendMessageWithSession(Long userId, CharSequence message)
      throws FacebookException, IOException {
      return extractInt(this.callMethod(FacebookMethod.SMS_SEND_MESSAGE,
                                 new Pair<String, CharSequence>("uid", userId.toString()),
                                 new Pair<String, CharSequence>("message", message),
                                 new Pair<String, CharSequence>("req_session", "1")));
    }
    
    /**
     * Extracts an Integer from a document that consists of an Integer only.
     * @param doc
     * @return the Integer
     */
    protected int extractInt(Document doc) {
      return Integer.parseInt(doc.getFirstChild().getTextContent());
    }
    
    /**
     * Retrieves the requested profile fields for the Facebook Pages with the given 
     * <code>pageIds</code>. Can be called for pages that have added the application 
     * without establishing a session.
     * @param pageIds the page IDs
     * @param fields a set of page profile fields
     * @return a T consisting of a list of pages, with each page element
     *     containing the requested fields.
     * @see <a href="http://wiki.developers.facebook.com/index.php/Pages.getInfo">
     *      Developers Wiki: Pages.getInfo</a>
     */
    public Document pages_getInfo(Collection<Long> pageIds, EnumSet<PageProfileField> fields)
      throws FacebookException, IOException {
      if (pageIds == null || pageIds.isEmpty()) {
        throw new IllegalArgumentException("pageIds cannot be empty or null");
      }
      if (fields == null || fields.isEmpty()) {
        throw new IllegalArgumentException("fields cannot be empty or null");
      }
      IFacebookMethod method =
        null == this._sessionKey ? FacebookMethod.PAGES_GET_INFO_NO_SESSION : FacebookMethod.PAGES_GET_INFO;
      return this.callMethod(method,
                             new Pair<String, CharSequence>("page_ids", delimit(pageIds)),
                             new Pair<String, CharSequence>("fields", delimit(fields)));
    }
    
    /**
     * Retrieves the requested profile fields for the Facebook Pages with the given 
     * <code>pageIds</code>. Can be called for pages that have added the application 
     * without establishing a session.
     * @param pageIds the page IDs
     * @param fields a set of page profile fields
     * @return a T consisting of a list of pages, with each page element
     *     containing the requested fields.
     * @see <a href="http://wiki.developers.facebook.com/index.php/Pages.getInfo">
     *      Developers Wiki: Pages.getInfo</a>
     */
    public Document pages_getInfo(Collection<Long> pageIds, Set<CharSequence> fields)
      throws FacebookException, IOException {
      if (pageIds == null || pageIds.isEmpty()) {
        throw new IllegalArgumentException("pageIds cannot be empty or null");
      }
      if (fields == null || fields.isEmpty()) {
        throw new IllegalArgumentException("fields cannot be empty or null");
      }
      IFacebookMethod method =
        null == this._sessionKey ? FacebookMethod.PAGES_GET_INFO_NO_SESSION : FacebookMethod.PAGES_GET_INFO;
      return this.callMethod(method,
                             new Pair<String, CharSequence>("page_ids", delimit(pageIds)),
                             new Pair<String, CharSequence>("fields", delimit(fields)));
    }
    
    /**
     * Retrieves the requested profile fields for the Facebook Pages of the user with the given 
     * <code>userId</code>.
     * @param userId the ID of a user about whose pages to fetch info (defaulted to the logged-in user)
     * @param fields a set of PageProfileFields
     * @return a T consisting of a list of pages, with each page element
     *     containing the requested fields.
     * @see <a href="http://wiki.developers.facebook.com/index.php/Pages.getInfo">
     *      Developers Wiki: Pages.getInfo</a>
     */
    public Document pages_getInfo(Long userId, EnumSet<PageProfileField> fields)
      throws FacebookException, IOException {
      if (fields == null || fields.isEmpty()) {
        throw new IllegalArgumentException("fields cannot be empty or null");
      }
      if (userId == null) {
        userId = this._userId;
      }
      return this.callMethod(FacebookMethod.PAGES_GET_INFO,
                             new Pair<String, CharSequence>("uid",    userId.toString()),
                             new Pair<String, CharSequence>("fields", delimit(fields)));
    }

    /**
     * Retrieves the requested profile fields for the Facebook Pages of the user with the given
     * <code>userId</code>.
     * @param userId the ID of a user about whose pages to fetch info (defaulted to the logged-in user)
     * @param fields a set of page profile fields
     * @return a T consisting of a list of pages, with each page element
     *     containing the requested fields.
     * @see <a href="http://wiki.developers.facebook.com/index.php/Pages.getInfo">
     *      Developers Wiki: Pages.getInfo</a>
     */
    public Document pages_getInfo(Long userId, Set<CharSequence> fields)
      throws FacebookException, IOException {
      if (fields == null || fields.isEmpty()) {
        throw new IllegalArgumentException("fields cannot be empty or null");
      }
      if (userId == null) {
        userId = this._userId;
      }
      return this.callMethod(FacebookMethod.PAGES_GET_INFO,
                             new Pair<String, CharSequence>("uid",    userId.toString()),
                             new Pair<String, CharSequence>("fields", delimit(fields)));
    }

    /**
     * Checks whether a page has added the application
     * @param pageId the ID of the page
     * @return true if the page has added the application
     * @see <a href="http://wiki.developers.facebook.com/index.php/Pages.isAppAdded">
     *      Developers Wiki: Pages.isAppAdded</a>
     */
    public boolean pages_isAppAdded(Long pageId)
      throws FacebookException, IOException {
      return extractBoolean(this.callMethod(FacebookMethod.PAGES_IS_APP_ADDED,
                                            new Pair<String,CharSequence>("page_id", pageId.toString())));
    }
    
    /**
     * Checks whether a user is a fan of the page with the given <code>pageId</code>.
     * @param pageId the ID of the page
     * @param userId the ID of the user (defaults to the logged-in user if null)
     * @return true if the user is a fan of the page
     * @see <a href="http://wiki.developers.facebook.com/index.php/Pages.isFan">
     *      Developers Wiki: Pages.isFan</a>
     */
    public boolean pages_isFan(Long pageId, Long userId)
      throws FacebookException, IOException {
      return extractBoolean(this.callMethod(FacebookMethod.PAGES_IS_FAN,
                                            new Pair<String,CharSequence>("page_id", pageId.toString()),
                                            new Pair<String,CharSequence>("uid", userId.toString())));
    }
    
    /**
     * Checks whether the logged-in user is a fan of the page with the given <code>pageId</code>.
     * @param pageId the ID of the page
     * @return true if the logged-in user is a fan of the page
     * @see <a href="http://wiki.developers.facebook.com/index.php/Pages.isFan">
     *      Developers Wiki: Pages.isFan</a>
     */
    public boolean pages_isFan(Long pageId)
      throws FacebookException, IOException {
      return extractBoolean(this.callMethod(FacebookMethod.PAGES_IS_FAN,
                                            new Pair<String,CharSequence>("page_id", pageId.toString())));
    }

    /**
     * Checks whether the logged-in user for this session is an admin of the page
     * with the given <code>pageId</code>.
     * @param pageId the ID of the page
     * @return true if the logged-in user is an admin
     * @see <a href="http://wiki.developers.facebook.com/index.php/Pages.isAdmin">
     *      Developers Wiki: Pages.isAdmin</a>
     */
    public boolean pages_isAdmin(Long pageId)
      throws FacebookException, IOException {
      return extractBoolean(this.callMethod(FacebookMethod.PAGES_IS_ADMIN,
                                            new Pair<String, CharSequence>("page_id",
                                                                           pageId.toString())));
    }

    /**
     * @deprecated use the version that treats actorId as a Long.  UID's *are not ever to be* expressed as Integers.
     */
    public boolean feed_publishTemplatizedAction(Integer actorId, CharSequence titleTemplate, Map<String,CharSequence> titleData, CharSequence bodyTemplate, Map<String,CharSequence> bodyData, CharSequence bodyGeneral, Collection<Long> targetIds, Collection<? extends IPair<? extends Object,URL>> images) throws FacebookException, IOException {
        return this.feed_publishTemplatizedAction((long)(actorId.intValue()), titleTemplate, titleData, bodyTemplate, bodyData, bodyGeneral, targetIds, images);
    }

    public void notifications_send(Collection<Long> recipientIds, CharSequence notification) throws FacebookException, IOException {
        if (null == notification || "".equals(notification)) {
            throw new FacebookException(ErrorCode.GEN_INVALID_PARAMETER, "You cannot send an empty notification!");
        }
        if ((recipientIds != null) && (! recipientIds.isEmpty())) {
            this.callMethod(FacebookMethod.NOTIFICATIONS_SEND,
                    new Pair<String, CharSequence>("to_ids", delimit(recipientIds)),
                    new Pair<String, CharSequence>("notification", notification));
        }
        else {
            this.callMethod(FacebookMethod.NOTIFICATIONS_SEND,
                    new Pair<String, CharSequence>("notification", notification));
        }
    }
    
    private Document notifications_sendEmail(CharSequence recipients, CharSequence subject, CharSequence email, CharSequence fbml) throws FacebookException, IOException {
        if (null == recipients || "".equals(recipients)) {
            //we throw an exception here because returning a sensible result (like an empty list) is problematic due to the use of Document as the return type
            throw new FacebookException(ErrorCode.GEN_INVALID_PARAMETER, "You must specify at least one recipient when sending an email!");
        }
        if ((null == email || "".equals(email)) && (null == fbml || "".equals(fbml))){
            throw new FacebookException(ErrorCode.GEN_INVALID_PARAMETER, "You cannot send an empty email!");
        }
        Document d;
        String paramName = "text";
        String paramValue;
        if ((email == null) || ("".equals(email.toString()))) {
            paramValue = fbml.toString();
            paramName = "fbml";
        }
        else {
            paramValue = email.toString();
        }
        
        //session is only required to send email from a desktop app
        FacebookMethod method = this.isDesktop() ? FacebookMethod.NOTIFICATIONS_SEND_EMAIL_SESSION : FacebookMethod.NOTIFICATIONS_SEND_EMAIL;
        if ((subject != null) && (! "".equals(subject))) {
            d = this.callMethod(method,
                          new Pair<String, CharSequence>("recipients", recipients),
                          new Pair<String, CharSequence>("subject", subject),
                          new Pair<String, CharSequence>(paramName, paramValue));
        }
        else {
            d = this.callMethod(method,
                    new Pair<String, CharSequence>("recipients", recipients),
                    new Pair<String, CharSequence>(paramName, paramValue));
        }
        
        return d;
    }

    public Document notifications_sendEmail(Collection<Long> recipients, CharSequence subject, CharSequence email, CharSequence fbml) throws FacebookException, IOException {
        return this.notifications_sendEmail(delimit(recipients), subject, email, fbml);
    }

    public Document notifications_sendEmailToCurrentUser(String subject, String email, String fbml) throws FacebookException, IOException {
        Long currentUser = this.users_getLoggedInUser();
        return this.notifications_sendEmail(currentUser.toString(), subject, email, fbml);
    }

    public Document notifications_sendFbmlEmail(Collection<Long> recipients, String subject, String fbml) throws FacebookException, IOException {
        return this.notifications_sendEmail(delimit(recipients), subject, null, fbml);
    }

    public Document notifications_sendFbmlEmailToCurrentUser(String subject, String fbml) throws FacebookException, IOException {
        Long currentUser = this.users_getLoggedInUser();
        return this.notifications_sendEmail(currentUser.toString(), subject, null, fbml);
    }

    public Document notifications_sendTextEmail(Collection<Long> recipients, String subject, String email) throws FacebookException, IOException {
        return this.notifications_sendEmail(delimit(recipients), subject, email, null);
    }

    public Document notifications_sendTextEmailToCurrentUser(String subject, String email) throws FacebookException, IOException {
        Long currentUser = this.users_getLoggedInUser();
        return this.notifications_sendEmail(currentUser.toString(), subject, email, null);
    }

    public boolean users_setStatus(String newStatus, boolean clear, boolean statusIncludesVerb) throws FacebookException, IOException {
        Collection<Pair<String, CharSequence>> params = new ArrayList<Pair<String, CharSequence>>();

        if (newStatus != null) {
            params.add(new Pair<String, CharSequence>("status", newStatus));
        }
        if (clear) {
            params.add(new Pair<String, CharSequence>("clear", "true"));
        }
        if (statusIncludesVerb) {
            params.add(new Pair<String, CharSequence>("status_includes_verb", "true"));
        }

        this.callMethod(FacebookMethod.USERS_SET_STATUS, params);

        return this.rawResponse.contains(">1<"); //a code of '1' is sent back to indicate that the request was successful, any other response indicates error
    }
    
    /**
     * Send a notification message to the logged-in user.
     *
     * @param notification the FBML to be displayed on the notifications page; only a stripped-down 
     *    set of FBML tags that result in text and links is allowed
     * @return a URL, possibly null, to which the user should be redirected to finalize
     * the sending of the email
     * @see <a href="http://wiki.developers.facebook.com/index.php/Notifications.sendEmail">
     *      Developers Wiki: notifications.send</a>
     */
    public void notifications_send(CharSequence notification)
      throws FacebookException, IOException {
        Long currentUser = this.users_getLoggedInUser();
        Collection<Long> coll = new ArrayList<Long>();
        coll.add(currentUser);
        notifications_send(coll, notification);
    }

    /**
     * Sends a notification email to the specified users, who must have added your application.
     * You can send five (5) emails to a user per day. Requires a session key for desktop applications, which may only 
     * send email to the person whose session it is. This method does not require a session for Web applications. 
     * Either <code>fbml</code> or <code>text</code> must be specified.
     * 
     * @param recipientIds up to 100 user ids to which the message is to be sent
     * @param subject the subject of the notification email (optional)
     * @param fbml markup to be sent to the specified users via email; only a stripped-down set of FBML tags
     *    that result in text, links and linebreaks is allowed
     * @param text the plain text to send to the specified users via email
     * @return a comma-separated list of the IDs of the users to whom the email was successfully sent
     * @see <a href="http://wiki.developers.facebook.com/index.php/Notifications.send">
     *      Developers Wiki: notifications.sendEmail</a>
     *      
     *  @deprecated provided for legacy support only, please use one of the alternate notifications_sendEmail calls.
     */
    public String notifications_sendEmailStr(Collection<Long> recipientIds, CharSequence subject, CharSequence fbml, CharSequence text)
      throws FacebookException, IOException {
      if (null == recipientIds || recipientIds.isEmpty()) {
        throw new IllegalArgumentException("List of email recipients cannot be empty");
      }
      boolean hasText = null != text && (0 != text.length());
      boolean hasFbml = null != fbml && (0 != fbml.length());
      if (!hasText && !hasFbml) {
        throw new IllegalArgumentException("Text and/or fbml must not be empty");
      }
      ArrayList<Pair<String, CharSequence>> args = new ArrayList<Pair<String, CharSequence>>(4);
      args.add(new Pair<String, CharSequence>("recipients", delimit(recipientIds)));
      args.add(new Pair<String, CharSequence>("subject", subject));
      if (hasText) {
        args.add(new Pair<String, CharSequence>("text", text));
      }
      if (hasFbml) {
        args.add(new Pair<String, CharSequence>("fbml", fbml));
      }
      // this method requires a session only if we're dealing with a desktop app
      Document result = this.callMethod(this.isDesktop() ? FacebookMethod.NOTIFICATIONS_SEND_EMAIL_SESSION
                   : FacebookMethod.NOTIFICATIONS_SEND_EMAIL, args);
      return extractString(result);
    }

    /**
     * Sends a notification email to the specified users, who must have added your application.
     * You can send five (5) emails to a user per day. Requires a session key for desktop applications, which may only
     * send email to the person whose session it is. This method does not require a session for Web applications.
     *
     * @param recipientIds up to 100 user ids to which the message is to be sent
     * @param subject the subject of the notification email (optional)
     * @param fbml markup to be sent to the specified users via email; only a stripped-down set of FBML
     *    that allows only tags that result in text, links and linebreaks is allowed
     * @return a comma-separated list of the IDs of the users to whom the email was successfully sent
     * @see <a href="http://wiki.developers.facebook.com/index.php/Notifications.send">
     *      Developers Wiki: notifications.sendEmail</a>
     *      
     *  @deprecated provided for legacy support only, please use one of the alternate notifications_sendEmail calls.
     */
    public String notifications_sendEmail(Collection<Long> recipientIds, CharSequence subject, CharSequence fbml)
      throws FacebookException, IOException {
      return notifications_sendEmailStr(recipientIds, subject, fbml, /*text*/null);
    }

    /**
     * Sends a notification email to the specified users, who must have added your application.
     * You can send five (5) emails to a user per day. Requires a session key for desktop applications, which may only
     * send email to the person whose session it is. This method does not require a session for Web applications.
     *
     * @param recipientIds up to 100 user ids to which the message is to be sent
     * @param subject the subject of the notification email (optional)
     * @param text the plain text to send to the specified users via email
     * @return a comma-separated list of the IDs of the users to whom the email was successfully sent
     * @see <a href="http://wiki.developers.facebook.com/index.php/Notifications.sendEmail">
     *      Developers Wiki: notifications.sendEmail</a>
     *      
     *  @deprecated provided for legacy support only, please use one of the alternate notifications_sendEmail calls.
     */
    public String notifications_sendEmailPlain(Collection<Long> recipientIds, CharSequence subject, CharSequence text)
      throws FacebookException, IOException {
      return notifications_sendEmailStr(recipientIds, subject, /*fbml*/null, text);
    }
    
    /**
     * Extracts a String from a T consisting entirely of a String.
     * @return the String
     */
    public String extractString(Document d) {
        return d.getFirstChild().getTextContent();
    }

    public boolean admin_setAppProperties(Map<ApplicationProperty,String> properties) throws FacebookException, IOException {
        if ((properties == null) || (properties.isEmpty())) {
            //nothing to do
            return true;
        }
        
        //Facebook is nonspecific about how they want the parameters encoded in JSON, so we make two attempts
        JSONObject encoding1 = new JSONObject();
        JSONArray encoding2 = new JSONArray();
        for (ApplicationProperty property : properties.keySet()) {
            JSONObject temp = new JSONObject();
            if (property.getType().equals("string")) {
                //simple case, just treat it as a literal string
                try {
                    encoding1.put(property.getName(), properties.get(property));
                    temp.put(property.getName(), properties.get(property));
                    encoding2.put(temp);
                }
                catch (JSONException ignored) {}
            }
            else {
                //we need to parse a boolean value
                String val = properties.get(property);
                if ((val == null) || (val.equals("")) || (val.equalsIgnoreCase("false")) || (val.equals("0"))) {
                    //false
                    val = "0";
                }
                else {
                    //true
                    val = "1";
                }
                try {
                    encoding1.put(property.getName(), val);
                    temp.put(property.getName(), val);
                    encoding2.put(temp);
                }
                catch (JSONException ignored) {}
            }
        }
        
        //now we've built our JSON-encoded parameter, so attempt to set the properties
        try {
            //first assume that Facebook is sensible enough to be able to undestand an associative array
            Document d = this.callMethod(FacebookMethod.ADMIN_SET_APP_PROPERTIES,
                    new Pair<String, CharSequence>("properties", encoding1.toString()));
            return extractBoolean(d);
        }
        catch (FacebookException e) {
            //if that didn't work, try the more convoluted encoding (which matches what they send back in response to admin_getAppProperties calls)
            Document d = this.callMethod(FacebookMethod.ADMIN_SET_APP_PROPERTIES,
                    new Pair<String, CharSequence>("properties", encoding2.toString()));
            return extractBoolean(d);
        }
    }

    /**
     * @deprecated use admin_getAppPropertiesMap() instead
     */
    public JSONObject admin_getAppProperties(Collection<ApplicationProperty> properties) throws FacebookException, IOException {
        String json = this.admin_getAppPropertiesAsString(properties);
        try {
            if (json.matches("\\{.*\\}")) {
                return new JSONObject(json);
            }
            else {
                JSONArray temp = new JSONArray(json);
                JSONObject result = new JSONObject();
                for (int count = 0; count < temp.length(); count++) {
                    JSONObject obj = (JSONObject)temp.get(count);
                    Iterator it = obj.keys();
                    while (it.hasNext()) {
                        String next = (String)it.next();
                        result.put(next, obj.get(next));
                    }
                }
                return result;
            }
        }
        catch (Exception e) {
            //response failed to parse
            throw new FacebookException(ErrorCode.GEN_SERVICE_ERROR, "Failed to parse server response:  " + json);
        }
    }
    
    public Map<ApplicationProperty, String> admin_getAppPropertiesMap(Collection<ApplicationProperty> properties) throws FacebookException, IOException {
        Map<ApplicationProperty, String> result = new LinkedHashMap<ApplicationProperty, String>();
        String json = this.admin_getAppPropertiesAsString(properties);
        if (json.matches("\\{.*\\}")) {
            json = json.substring(1, json.lastIndexOf("}"));
        }
        else {
            json = json.substring(1, json.lastIndexOf("]"));
        }
        String[] parts = json.split("\\,");
        for (String part : parts) {
            parseFragment(part, result);
        }
        
        return result;
    }
    
    private void parseFragment(String fragment, Map<ApplicationProperty, String> result) {
        if (fragment.startsWith("{")) {
            fragment = fragment.substring(1, fragment.lastIndexOf("}"));
        }
        String keyString = fragment.substring(1);
        keyString = keyString.substring(0, keyString.indexOf('"'));
        ApplicationProperty key = ApplicationProperty.getPropertyForString(keyString);
        String value = fragment.substring(fragment.indexOf(":") + 1).replaceAll("\\\\", "");  //strip escape characters
        if (key.getType().equals("string")) {
            result.put(key, value.substring(1, value.lastIndexOf('"')));
        }
        else {
            if (value.equals("1")) {
                result.put(key, "true");
            }
            else {
                result.put(key, "false");
            }
        }
    }

    public String admin_getAppPropertiesAsString(Collection<ApplicationProperty> properties) throws FacebookException, IOException {
        JSONArray props = new JSONArray();
        for (ApplicationProperty property : properties) {
            props.put(property.getName());
        }
        Document d = this.callMethod(FacebookMethod.ADMIN_GET_APP_PROPERTIES,
                new Pair<String, CharSequence>("properties", props.toString()));
        return extractString(d);
    }

    public Document data_getCookies() throws FacebookException, IOException {
        return this.data_getCookies(this.users_getLoggedInUser(), null);
    }

    public Document data_getCookies(Long userId) throws FacebookException, IOException {
        return this.data_getCookies(userId, null);
    }

    public Document data_getCookies(String name) throws FacebookException, IOException {
        return this.data_getCookies(this.users_getLoggedInUser(), name);
    }

    public Document data_getCookies(Long userId, CharSequence name) throws FacebookException, IOException {
        ArrayList<Pair<String, CharSequence>> args = new ArrayList<Pair<String, CharSequence>>();
        args.add(new Pair<String, CharSequence>("uid", Long.toString(userId)));
        if ((name != null) && (! "".equals(name))) {
            args.add(new Pair<String, CharSequence>("name", name));
        }
        
        return this.callMethod(FacebookMethod.DATA_GET_COOKIES, args);
    }

    public boolean data_setCookie(String name, String value) throws FacebookException, IOException {
        return this.data_setCookie(this.users_getLoggedInUser(), name, value, null, null);
    }

    public boolean data_setCookie(String name, String value, String path) throws FacebookException, IOException {
        return this.data_setCookie(this.users_getLoggedInUser(), name, value, null, path);
    }

    public boolean data_setCookie(Long userId, CharSequence name, CharSequence value) throws FacebookException, IOException {
        return this.data_setCookie(userId, name, value, null, null);
    }

    public boolean data_setCookie(Long userId, CharSequence name, CharSequence value, CharSequence path) throws FacebookException, IOException {
        return this.data_setCookie(userId, name, value, null, path);
    }

    public boolean data_setCookie(String name, String value, Long expires) throws FacebookException, IOException {
        return this.data_setCookie(this.users_getLoggedInUser(), name, value, expires, null);
    }

    public boolean data_setCookie(String name, String value, Long expires, String path) throws FacebookException, IOException {
        return this.data_setCookie(this.users_getLoggedInUser(), name, value, expires, path);
    }

    public boolean data_setCookie(Long userId, CharSequence name, CharSequence value, Long expires) throws FacebookException, IOException {
        return this.data_setCookie(userId, name, value, expires, null);
    }

    public boolean data_setCookie(Long userId, CharSequence name, CharSequence value, Long expires, CharSequence path) throws FacebookException, IOException {
        if ((name == null) || ("".equals(name))) {
            throw new FacebookException(ErrorCode.GEN_INVALID_PARAMETER, "The cookie name cannot be null or empty!");
        }
        if (value == null) {
            value = "";
        }
        
        Document doc;
        ArrayList<Pair<String, CharSequence>> args = new ArrayList<Pair<String, CharSequence>>();
        args.add(new Pair<String, CharSequence>("uid", Long.toString(userId)));
        args.add(new Pair<String, CharSequence>("name", name));
        args.add(new Pair<String, CharSequence>("value", value));
        if ((expires != null) && (expires > 0)) {
            args.add(new Pair<String, CharSequence>("expires", expires.toString()));
        }
        if ((path != null) && (! "".equals(path))) {
            args.add(new Pair<String, CharSequence>("path", path));
        }
        doc = this.callMethod(FacebookMethod.DATA_SET_COOKIE, args);
        
        return extractBoolean(doc);
    }

    public boolean feed_publishTemplatizedAction(CharSequence titleTemplate) throws FacebookException, IOException {
        return this.feed_publishTemplatizedAction(titleTemplate, null);
    }

    public boolean feed_publishTemplatizedAction(CharSequence titleTemplate, Long pageActorId) throws FacebookException, IOException {
        return this.feed_publishTemplatizedAction(titleTemplate, null, null, null, null, null, null, pageActorId);
    }

    public boolean feed_publishTemplatizedAction(CharSequence titleTemplate, Map<String,CharSequence> titleData, CharSequence bodyTemplate, Map<String,CharSequence> bodyData, CharSequence bodyGeneral, Collection<Long> targetIds, Collection<? extends IPair<? extends Object, URL>> images, Long pageActorId) throws FacebookException, IOException {
        return this.feed_publishTemplatizedActionInternal(null, titleTemplate == null ? null : titleTemplate.toString(), 
                mapToJsonString(titleData), bodyTemplate == null ? null : bodyTemplate.toString(), mapToJsonString(bodyData), bodyGeneral == null ? null : bodyGeneral.toString(), images, targetIds, pageActorId);
    }

    public boolean profile_setFBML(CharSequence profileFbmlMarkup, CharSequence profileActionFbmlMarkup) throws FacebookException, IOException {
        return this.profile_setFBML(this.users_getLoggedInUser(), profileFbmlMarkup == null ? null : profileFbmlMarkup.toString(), profileActionFbmlMarkup == null ? null : profileActionFbmlMarkup.toString(), null);
    }

    public boolean profile_setFBML(CharSequence profileFbmlMarkup, CharSequence profileActionFbmlMarkup, Long profileId) throws FacebookException, IOException {
        return this.profile_setFBML(profileId, profileFbmlMarkup == null ? null : profileFbmlMarkup.toString(), profileActionFbmlMarkup == null ? null : profileActionFbmlMarkup.toString(), null);
    }

    public boolean profile_setFBML(CharSequence profileFbmlMarkup, CharSequence profileActionFbmlMarkup, CharSequence mobileFbmlMarkup) throws FacebookException, IOException {
        return this.profile_setFBML(this.users_getLoggedInUser(), profileFbmlMarkup == null ? null : profileFbmlMarkup.toString(), profileActionFbmlMarkup == null ? null : profileActionFbmlMarkup.toString(), mobileFbmlMarkup == null ? null : mobileFbmlMarkup.toString());
    }

    public boolean profile_setFBML(CharSequence profileFbmlMarkup, CharSequence profileActionFbmlMarkup, CharSequence mobileFbmlMarkup, Long profileId) throws FacebookException, IOException {
        return this.profile_setFBML(profileId, profileFbmlMarkup == null ? null : profileFbmlMarkup.toString(), profileActionFbmlMarkup == null ? null : profileActionFbmlMarkup.toString(), mobileFbmlMarkup == null ? null : mobileFbmlMarkup.toString());
    }

    public boolean profile_setMobileFBML(CharSequence fbmlMarkup) throws FacebookException, IOException {
        return this.profile_setFBML(this.users_getLoggedInUser(), null, null, fbmlMarkup == null ? null : fbmlMarkup.toString());
    }

    public boolean profile_setMobileFBML(CharSequence fbmlMarkup, Long profileId) throws FacebookException, IOException {
        return this.profile_setFBML(profileId, null, null, fbmlMarkup == null ? null : fbmlMarkup.toString());
    }

    public boolean profile_setProfileActionFBML(CharSequence fbmlMarkup) throws FacebookException, IOException {
        return this.profile_setFBML(this.users_getLoggedInUser(), null, fbmlMarkup == null ? null : fbmlMarkup.toString(), null);
    }

    public boolean profile_setProfileActionFBML(CharSequence fbmlMarkup, Long profileId) throws FacebookException, IOException {
        return this.profile_setFBML(profileId, null, fbmlMarkup == null ? null : fbmlMarkup.toString(), null);
    }

    public boolean profile_setProfileFBML(CharSequence fbmlMarkup) throws FacebookException, IOException {
        return this.profile_setFBML(this.users_getLoggedInUser(), fbmlMarkup == null ? null : fbmlMarkup.toString(), null, null);
    }

    public boolean profile_setProfileFBML(CharSequence fbmlMarkup, Long profileId) throws FacebookException, IOException {
        return this.profile_setFBML(profileId, fbmlMarkup == null ? null : fbmlMarkup.toString(), null, null);
    }
    
    /**
     * Retrieves the friends of the currently logged in user that are members of the friends list
     * with ID <code>friendListId</code>.
     * 
     * @param friendListId the friend list for which friends should be fetched. if <code>null</code>,
     *            all friends will be retrieved.
     * @return T of friends
     * @see <a href="http://wiki.developers.facebook.com/index.php/Friends.get"> Developers Wiki:
     *      Friends.get</a>
     */
    public Document friends_get(Long friendListId) throws FacebookException, IOException {
        FacebookMethod method = FacebookMethod.FRIENDS_GET;
        Collection<Pair<String,CharSequence>> params = new ArrayList<Pair<String,CharSequence>>(
                method.numParams());
        if (null != friendListId) {
            if (0L >= friendListId) {
                throw new IllegalArgumentException("given invalid friendListId "
                        + friendListId.toString());
            }
            params.add(new Pair<String,CharSequence>("flid", friendListId.toString()));
        }
        return this.callMethod(method, params);
    }

    /**
     * Retrieves the friend lists of the currently logged in user.
     * 
     * @return T of friend lists
     * @see <a href="http://wiki.developers.facebook.com/index.php/Friends.getLists"> Developers
     *      Wiki: Friends.getLists</a>
     */
    public Document friends_getLists() throws FacebookException, IOException {
        return this.callMethod(FacebookMethod.FRIENDS_GET_LISTS);
    }
    
    /**
     * Sets several property values for an application. The properties available are analogous to
     * the ones editable via the Facebook Developer application. A session is not required to use
     * this method.
     * 
     * @param properties an ApplicationPropertySet that is translated into a single JSON String.
     * @return a boolean indicating whether the properties were successfully set
     */
    public boolean admin_setAppProperties(ApplicationPropertySet properties)
            throws FacebookException, IOException {
        if (null == properties || properties.isEmpty()) {
            throw new IllegalArgumentException(
                    "expecting a non-empty set of application properties");
        }
        return extractBoolean(this.callMethod(FacebookMethod.ADMIN_SET_APP_PROPERTIES,
                new Pair<String,CharSequence>("properties", properties.toJsonString())));
    }

    /**
     * Gets property values previously set for an application on either the Facebook Developer
     * application or the with the <code>admin.setAppProperties</code> call. A session is not
     * required to use this method.
     * 
     * @param properties an enumeration of the properties to get
     * @return an ApplicationPropertySet
     * @see ApplicationProperty
     * @see <a href="http://wiki.developers.facebook.com/index.php/Admin.getAppProperties">
     *      Developers Wiki: Admin.getAppProperties</a>
     */
    public ApplicationPropertySet admin_getAppPropertiesAsSet(EnumSet<ApplicationProperty> properties)
            throws FacebookException, IOException {
        String propJson = this.admin_getAppPropertiesAsString(properties);
        return new ApplicationPropertySet(propJson);
    }
}