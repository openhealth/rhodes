package rhomobile;

import java.io.IOException;

import javax.microedition.io.HttpConnection;
import net.rim.device.api.system.ApplicationManager;

//import org.garret.perst.Storage;
//import org.garret.perst.StorageFactory;

import com.rho.*;

import net.rim.device.api.browser.field.*;
import net.rim.device.api.io.http.HttpHeaders;
import net.rim.device.api.system.Application;
import net.rim.device.api.system.KeyListener;
import net.rim.device.api.ui.*;
import net.rim.device.api.ui.component.Status;
import net.rim.device.api.ui.container.MainScreen;
import net.rim.device.api.ui.component.Menu;
import net.rim.device.api.system.Characters;
import net.rim.device.api.system.TrackwheelListener;
import net.rim.device.api.system.SystemListener;

import com.rho.net.INetworkAccess;
import com.rho.net.RhoConnection;
import rhomobile.camera.CameraScreen;
import rhomobile.camera.ImageBrowserScreen;
import com.rho.location.GeoLocation;
import com.rho.sync.SyncEngine;
import com.rho.sync.SyncUtil;
import com.rho.sync.SyncNotifications;

import java.util.Vector;


/**
 *
 */
final public class RhodesApplication extends UiApplication implements RenderingApplication, SystemListener
{
	private static final RhoLogger LOG = RhoLogger.RHO_STRIP_LOG ? new RhoEmptyLogger() : 
		new RhoLogger("RhodesApplication");

	class CKeyListener  implements KeyListener{

		public boolean keyChar(char key, int status, int time) {
	        if( key == Characters.ENTER ) {
	        	openLink();
	        	return true;
	        }
			return false;
		}

		public boolean keyDown(int keycode, int time) {
			int nKey = Keypad.key(keycode);
			if ( nKey == Keypad.KEY_ESCAPE )
			{
				back();
				return true;
			}

			return false;
		}

		public boolean keyRepeat(int keycode, int time) {return false;}
		public boolean keyStatus(int keycode, int time) {return false;}
		public boolean keyUp(int keycode, int time) {return false;}
    };

    class CTrackwheelListener implements TrackwheelListener{

		public boolean trackwheelClick(int status, int time) {
			openLink();
			return true;
		}

		public boolean trackwheelRoll(int amount, int status, int time) {return false;}
		public boolean trackwheelUnclick(int status, int time) {return false;}
    }

    class SyncNotificationsImpl extends SyncNotifications{
    	public void performNotification(String url, String body){

    		HttpHeaders headers = new HttpHeaders();
    		headers.addProperty("Content-Type", "application/x-www-form-urlencoded");
    		postUrl(url, body, headers);

/*    		String curUrl = (String)_history.lastElement();
    		curUrl.replace('\\', '/');
    		if ( curUrl.equalsIgnoreCase(url) )
    			navigateUrl(curUrl);*/

    	}

    }

    String canonicalizeURL( String url ){
		if ( url == null || url.length() == 0 )
			return "";

		url.replace('\\', '/');
		if ( !url.startsWith(_httpRoot) ){
    		if ( url.charAt(0) == '/' )
    			url = _httpRoot.substring(0, _httpRoot.length()-1) + url;
    		else
    			url = _httpRoot + url;
		}

		return url;
    }

    void navigateUrl(String url){
    	PrimaryResourceFetchThread thread = new PrimaryResourceFetchThread(
        		canonicalizeURL(url), null, null, null, this);
        thread.start();                       
    }

    public void postUrl(String url, String body, HttpHeaders headers){
        PrimaryResourceFetchThread thread = new PrimaryResourceFetchThread(
        		canonicalizeURL(url), headers, body.getBytes(), null, this);
        thread.start();                       
    }

    void saveCurrentLocation(String url) {
    	if (RhoConf.getInstance().getBool("KeepTrackOfLastVisitedPage")) {
			RhoConf.getInstance().setString("LastVisitedPage",url);
			RhoConf.getInstance().saveToFile();
			LOG.TRACE("Saved LastVisitedPage: " + url);
		}   	
    }

    boolean restoreLocation() {
    	LOG.TRACE("Restore Location to LastVisitedPage");
    	if (RhoConf.getInstance().getBool("KeepTrackOfLastVisitedPage")) {
			String url = RhoConf.getInstance().getString("LastVisitedPage");
			if (url.length()>0) {
				LOG.TRACE("Navigating to LastVisitedPage: " + url);
				this.navigateUrl(url);
				return true;
			}
		} 
		return false;
    }
   
    void back(){
    	if ( _history.size() <= 1 )
    		return;

    	int nPos = _history.size()-2;
    	String url = (String)_history.elementAt(nPos);
    	_history.removeElementAt(nPos+1);

    	saveCurrentLocation(url);
    	
    	navigateUrl(url);
    }

    void addToHistory(String strUrl, String refferer ){
    	int nPos = -1;
    	for( int i = _history.size()-1; i >= 0; i-- ){
    		if ( strUrl.equalsIgnoreCase((String)_history.elementAt(i)) ){
    			nPos = i;
    			break;
    		}
    		/*String strUrl1 = strUrl + "/index";
    		if ( strUrl1.equalsIgnoreCase((String)_history.elementAt(i)) ){
    			nPos = i;
    			break;
    		}*/
    		if ( refferer != null && refferer.equalsIgnoreCase((String)_history.elementAt(i)) ){
    			nPos = i;
    			break;
    		}

    	}
    	if ( nPos == -1 ){
    		boolean bReplace = RhoConnection.findIndex(strUrl) != -1;

    		if ( bReplace )
    			_history.setElementAt(strUrl, _history.size()-1 );
    		else
    			_history.addElement(strUrl);
    	}
    	else{
    		_history.setSize(nPos+1);
    		_history.setElementAt(strUrl, _history.size()-1 );
    	}
    	saveCurrentLocation(strUrl);
    }

    void openLink(){
        Menu menu = _mainScreen.getMenu(0);
    	int size = menu.getSize();
    	for(int i=0; i<size; i++)
    	{
    	    MenuItem item = menu.getItem(i);
    	    String label = item.toString();
    	    if(label.equalsIgnoreCase("Get Link")) //TODO: catch by ID?
    	    {
    	    	item.run();
    	    }
    	}
    }

	private static final String REFERER = "referer";

    private RenderingSession _renderingSession;

    private MainScreen _mainScreen = null;

    private HttpConnection  _currentConnection;

    private Vector _history;

    private final String _httpRoot = "http://localhost:8080/";

    private boolean _isFullBrowser = false;
    
    private static PushListeningThread _pushListeningThread = null;
    
    private static RhodesApplication _instance;

    public static RhodesApplication getInstance(){ return _instance; }
    /***************************************************************************
     * Main.
     **************************************************************************/
    public static void main(String[] args)
    {
    	RhoLogger.InitRhoLog();
    	LOG.TRACE("Rhodes MAIN started ***--------------------------***");
    	
    	_pushListeningThread = new PushListeningThread();
    	_pushListeningThread.start();
    	
		_instance = new RhodesApplication();
		_instance.enterEventDispatcher();
				
		_pushListeningThread.stop();
		
        RhoLogger.close();
		LOG.TRACE("Rhodes MAIN exit ***--------------------------***");
    }

    void doClose(){   	
    	LOG.TRACE("Rhodes DO CLOSE ***--------------------------***");
/*    	
		SyncEngine.stop(null);
		GeoLocation.stop();
        RhoRuby.RhoRubyStop();
        
    	try{
    		RhoClassFactory.getNetworkAccess().close();
    	}catch(IOException exc){
    		LOG.ERROR(exc);
    	}
*/
    }

	public void activate() {
    	LOG.TRACE("Rhodes activate ***--------------------------***");
//		SyncEngine.start(null);
    	try{
    		GeoLocation.start();
    	}catch(Exception exc){
    		LOG.ERROR("GeoLocation failed to start", exc);
    	}

		super.activate();
	}

	public void deactivate() {
    	LOG.TRACE("Rhodes deactivate ***--------------------------***");		
//		SyncEngine.stop(null);
		GeoLocation.stop();

		super.deactivate();
	}

    class CMainScreen extends MainScreen{

		private MenuItem homeItem = new MenuItem("Home", 200000, 10) {
			public void run() {
					navigateHome();
				}
			};
		private MenuItem refreshItem = new MenuItem("Refresh", 200000, 10) {
			public void run() {
				refreshCurrentPage();
				}
			};
		private MenuItem syncItem = new MenuItem("Sync", 200000, 10) {
			public void run() {
					SyncEngine.wakeUp();
				}
			};
		private MenuItem optionsItem = new MenuItem("Options", 200000, 10) {
			public void run() {
					String curUrl = RhoRuby.getOptionsPage();
					curUrl = _httpRoot +
						curUrl.substring(curUrl.charAt(0) == '\\' || curUrl.charAt(0) == '/' ? 1 : 0 );

					addToHistory(curUrl, null );
			    	
					navigateUrl(curUrl);
				}
			};
		private MenuItem logItem = new MenuItem("Log", 200000, 10) {
			public void run() {
					LogScreen screen = new LogScreen();
			        //Push this screen to display it to the user.
			        UiApplication.getUiApplication().pushScreen(screen);
				}
			};

		protected void makeMenu(Menu menu, int instance) {
			// TODO Auto-generated method stub
			
			for(int i=0; i < menu.getSize(); i++)
	    	{
	    	    MenuItem item = menu.getItem(i);
	    	    String label = item.toString();
	    	    if(! ( label.equalsIgnoreCase("Get Link") || label.equalsIgnoreCase("Close") )) //TODO: catch by ID?
	    	    {
	    	    	menu.deleteItem(i);
	    	    	if ( i > 0 )
	    	    		i = i - 1;
	    	    }
	    	}
			
			menu.add(homeItem);
			menu.add(refreshItem);
			menu.add(syncItem);
			menu.add(optionsItem);
			menu.add(logItem);
			
			super.makeMenu(menu, instance);
		}

		public void close() {
			LOG.TRACE("Calling Screen.close");
			Application.getApplication().requestBackground();
		}
		
		public boolean onClose() {
			doClose();
			return super.onClose();
			//System.exit(0);
			//return true;
		}

		public boolean onMenu(int instance) {
			// TODO Auto-generated method stub
			return super.onMenu(instance);
		}

    }

    private void doStartupWork() {
        LOG.TRACE(" STARTING RHODES: ***----------------------------------*** " );
      
    	try {
    		RhoClassFactory.getNetworkAccess().configure();
    	} catch(IOException exc) {
    		LOG.ERROR(exc.getMessage());
    	}
    	
    	SyncUtil.init();
        RhoRuby.RhoRubyStart("");
		SyncEngine.start(null);
   	
    	CKeyListener list = new CKeyListener();
    	CTrackwheelListener wheel = new CTrackwheelListener();
    	this._history = new Vector();

        SyncEngine.setNotificationImpl( new SyncNotificationsImpl() );

        _mainScreen = new CMainScreen();
        _mainScreen.addKeyListener(list);
        _mainScreen.addTrackwheelListener(wheel);

        pushScreen(_mainScreen);
        _renderingSession = RenderingSession.getNewInstance();
        // enable javascript
        _renderingSession.getRenderingOptions().setProperty(RenderingOptions.CORE_OPTIONS_GUID, RenderingOptions.JAVASCRIPT_ENABLED, true);
        _renderingSession.getRenderingOptions().setProperty(RenderingOptions.CORE_OPTIONS_GUID, RenderingOptions.JAVASCRIPT_LOCATION_ENABLED, true);
        _renderingSession.getRenderingOptions().setProperty(RenderingOptions.CORE_OPTIONS_GUID, RenderingOptions.ENABLE_CSS, true);
        
        if ( RhoConf.getInstance().getBool("use_bb_full_browser") )
        {
	        com.rho.Jsr75File.SoftVersion ver = com.rho.Jsr75File.getSoftVersion();
	        if ( ver.nMajor == 4 && ver.nMinor == 6 )
	        {
		        //this is the undocumented option to tell the browser to use the 4.6 Rendering Engine
		        _renderingSession.getRenderingOptions().setProperty(RenderingOptions.CORE_OPTIONS_GUID, 17000, true);
	        	_isFullBrowser = true;
	        }
        }
        
        if(!restoreLocation()) {
        	navigateHome();
        }    
        
        LOG.TRACE("RHODES STARTUP COMPLETED: ***----------------------------------*** " );        
    }
    
    private void invokeStartupWork() {
        // I think this can get called twice
        // 1) Directly from startup, if the app starts while the BB is up - e.g. after download
        // 2) From System Listener - after system restart and when the app is originally installed
        // To make sure we don't actually do the startup stuff twice,
        // we use _mainScreen as a flag
        if ( _mainScreen == null ) {
            LOG.TRACE(" Shedule doStartupWork() ***---------------------------------- " );
            this.invokeLater( new Runnable() { 
                public void run() { 
                    doStartupWork(); 
                }
            } );
        }
    }

    //----------------------------------------------------------------------
    // SystemListener methods

    public void powerUp() {
        LOG.TRACE(" POWER UP ***----------------------------------*** " );
        invokeStartupWork();
        this.requestBackground();
    }
    public void powerOff() {
        LOG.TRACE(" POWER DOWN ***----------------------------------*** " );
//        _mainScreen = null;
//        doClose();
    }
    public void batteryLow() { }
    public void batteryGood() { }    
    public void batteryStatusChange(int status) { }

    // end of SystemListener methods
    //----------------------------------------------------------------------
    
    private RhodesApplication() {
        LOG.TRACE(" Construct RhodesApplication() ***----------------------------------*** " );
        this.addSystemListener(this);
        if ( ApplicationManager.getApplicationManager().inStartup() ) {
            LOG.TRACE("We are in the phone startup, don't start Rhodes yet, leave it to power up call");
        } else {
            invokeStartupWork();
        }
    }

    public void refreshCurrentPage(){
		navigateUrl(getCurrentPageUrl());
    }

    public String getCurrentPageUrl(){
    	return (String)_history.lastElement();
    }

    void navigateHome(){
        String strStartPage = _httpRoot.substring(0, _httpRoot.length()-1) +
        	RhoRuby.getStartPage();
        _history.removeAllElements();
	    _history.addElement(strStartPage);
	    navigateUrl(strStartPage);
    }

    public void processConnection(HttpConnection connection, Event e) {

        // cancel previous request
        if (_currentConnection != null) {
            try {
                _currentConnection.close();
            } catch (IOException e1) {
            }
        }

        _currentConnection = connection;

        BrowserContent browserContent = null;

        try {
            browserContent = _renderingSession.getBrowserContent(connection, this, e);

            if (browserContent != null) {

                Field field = browserContent.getDisplayableContent();
                if (field != null) {
                    synchronized (Application.getEventLock()) {
                        _mainScreen.deleteAll();
                        _mainScreen.add(field);
                    }
                }

                if ( _isFullBrowser )
                	browserContent.finishLoading();
                else
                {
	                synchronized (getAppEventLock())
	                {
	                	browserContent.finishLoading();
	                }
                }
            }

        } catch (RenderingException re) {

        } finally {
            SecondaryResourceFetchThread.doneAddingImages();
        }

    }

    /**
     * @see net.rim.device.api.browser.RenderingApplication#eventOccurred(net.rim.device.api.browser.Event)
     */
    public Object eventOccurred(Event event) {

        int eventId = event.getUID();

        switch (eventId) {

            case Event.EVENT_URL_REQUESTED : {

                UrlRequestedEvent urlRequestedEvent = (UrlRequestedEvent) event;
                String absoluteUrl = urlRequestedEvent.getURL();
                if ( !absoluteUrl.startsWith(_httpRoot) )
                	absoluteUrl = _httpRoot + absoluteUrl.substring(_httpRoot.length()-5);

                if ( urlRequestedEvent.getPostData() == null ||
                	 urlRequestedEvent.getPostData().length == 0 )
                	addToHistory(absoluteUrl, null );

                PrimaryResourceFetchThread thread = new PrimaryResourceFetchThread(absoluteUrl,
                                                                                   urlRequestedEvent.getHeaders(),
                                                                                   urlRequestedEvent.getPostData(),
                                                                                   event, this);
                thread.start();

                break;

            } case Event.EVENT_BROWSER_CONTENT_CHANGED: {

                // browser field title might have changed update title
                BrowserContentChangedEvent browserContentChangedEvent = (BrowserContentChangedEvent) event;

                if (browserContentChangedEvent.getSource() instanceof BrowserContent) {
                    BrowserContent browserField = (BrowserContent) browserContentChangedEvent.getSource();
                    String newTitle = browserField.getTitle();
                    if (newTitle != null) {
                        synchronized (getAppEventLock())
                        {
                        	_mainScreen.setTitle(newTitle);
                        }
                    }
                }

                break;

            } case Event.EVENT_REDIRECT : {

                    RedirectEvent e = (RedirectEvent) event;
                    String referrer = e.getSourceURL();
                    String absoluteUrl = e.getLocation();

                    switch (e.getType()) {

                        case RedirectEvent.TYPE_SINGLE_FRAME_REDIRECT :
                            // show redirect message
                            Application.getApplication().invokeAndWait(new Runnable() {
                                public void run() {
                                    Status.show("You are being redirected to a different page...");
                                }
                            });

                            break;

                        case RedirectEvent.TYPE_JAVASCRIPT :
                            break;
                        case RedirectEvent.TYPE_META :
                            // MSIE and Mozilla don't send a Referer for META Refresh.
                            referrer = null;
                            break;
                        case RedirectEvent.TYPE_300_REDIRECT :
                            // MSIE, Mozilla, and Opera all send the original
                            // request's Referer as the Referer for the new
                            // request.
                            if ( !absoluteUrl.startsWith(_httpRoot) )
                            	absoluteUrl = _httpRoot + absoluteUrl.substring(_httpRoot.length()-5);

                        	addToHistory(absoluteUrl,referrer);
                            Object eventSource = e.getSource();
                            if (eventSource instanceof HttpConnection) {
                                referrer = ((HttpConnection)eventSource).getRequestProperty(REFERER);
                            }
                            break;

                    }

                    HttpHeaders requestHeaders = new HttpHeaders();
                    requestHeaders.setProperty(REFERER, referrer);
                    PrimaryResourceFetchThread thread = new PrimaryResourceFetchThread(absoluteUrl, requestHeaders,null, event, this);
                    thread.start();
                    break;

            } case Event.EVENT_CLOSE :
                // TODO: close the appication
                break;

            case Event.EVENT_SET_HEADER :        // no cache support
            case Event.EVENT_SET_HTTP_COOKIE :   // no cookie support
            case Event.EVENT_HISTORY :           // no history support
            case Event.EVENT_EXECUTING_SCRIPT :  // no progress bar is supported
            case Event.EVENT_FULL_WINDOW :       // no full window support
            case Event.EVENT_STOP :              // no stop loading support
            default :
        }

        return null;
    }

    /**
     * @see net.rim.device.api.browser.RenderingApplication#getAvailableHeight(net.rim.device.api.browser.BrowserContent)
     */
    public int getAvailableHeight(BrowserContent browserField) {
        // field has full screen
        return Graphics.getScreenHeight();
    }

    /**
     * @see net.rim.device.api.browser.RenderingApplication#getAvailableWidth(net.rim.device.api.browser.BrowserContent)
     */
    public int getAvailableWidth(BrowserContent browserField) {
        // field has full screen
        return Graphics.getScreenWidth();
    }

    /**
     * @see net.rim.device.api.browser.RenderingApplication#getHistoryPosition(net.rim.device.api.browser.BrowserContent)
     */
    public int getHistoryPosition(BrowserContent browserField) {
        // no history support
        return 0;
    }

    /**
     * @see net.rim.device.api.browser.RenderingApplication#getHTTPCookie(java.lang.String)
     */
    public String getHTTPCookie(String url) {
        // no cookie support
        return null;
    }

    /**
     * @see net.rim.device.api.browser.RenderingApplication#getResource(net.rim.device.api.browser.RequestedResource,
     *      net.rim.device.api.browser.BrowserContent)
     */
    public HttpConnection getResource( RequestedResource resource, BrowserContent referrer) {

        if (resource == null) {
            return null;
        }

        // check if this is cache-only request
        if (resource.isCacheOnly()) {
            // no cache support
            return null;
        }

        String url = resource.getUrl();

        if (url == null) {
            return null;
        }

        // if referrer is null we must return the connection
        if (referrer == null) {
            HttpConnection connection = Utilities.makeConnection(resource.getUrl(), resource.getRequestHeaders(), null);
            return connection;

        } else {

            // if referrer is provided we can set up the connection on a separate thread
            SecondaryResourceFetchThread.enqueue(resource, referrer);

        }

        return null;
    }

    /**
     * @see net.rim.device.api.browser.RenderingApplication#invokeRunnable(java.lang.Runnable)
     */
    public void invokeRunnable(Runnable runnable) {
        (new Thread(runnable)).start();
    }

}

class PrimaryResourceFetchThread extends Thread {

    private RhodesApplication _application;

    private Event _event;

    private byte[] _postData;

    private HttpHeaders _requestHeaders;

    private String _url;

    public PrimaryResourceFetchThread(String url, HttpHeaders requestHeaders, byte[] postData,
                                  Event event, RhodesApplication application) {

        _url = url;
        _requestHeaders = requestHeaders;
        _postData = postData;
        _application = application;
        _event = event;
    }

    public void run() {
        HttpConnection connection = Utilities.makeConnection(_url, _requestHeaders, _postData);
        _application.processConnection(connection, _event);
    }
}

