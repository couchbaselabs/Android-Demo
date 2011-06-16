package com.arandomurl.couchapp;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import org.json.JSONException;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.ServiceConnection;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.couchbase.libcouch.AndCouch;
import com.couchbase.libcouch.CouchDB;
import com.couchbase.libcouch.ICouchClient;

public class CouchAppActivity extends Activity {

	private final CouchAppActivity self = this;
	protected static final String TAG = "CouchAppActivity";

	private ServiceConnection couchServiceConnection;
	private ProgressDialog installProgress;
	private WebView webView;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		startCouch();
	}

	@Override
	public void onRestart() {
		super.onRestart();
		startCouch();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		try {
			unbindService(couchServiceConnection);
		} catch (IllegalArgumentException e) {
		}
	}

	private final ICouchClient mCallback = new ICouchClient.Stub() {
		@Override
		public void couchStarted(String host, int port) {

			if (installProgress != null) {
				installProgress.dismiss();
			}

			String url = "http://" + host + ":" + Integer.toString(port) + "/";
		    String ip = getLocalIpAddress();
		    String param = (ip == null) ? "" : "?ip=" + ip;

			ensureDesignDoc("mobilefuton", url);
			launchFuton(url + "mobilefuton/_design/mobilefuton/index.html" + param);
		}

		@Override
		public void installing(int completed, int total) {
			ensureProgressDialog();
			installProgress.setTitle("Initialising CouchDB");
			installProgress.setProgress(completed);
			installProgress.setMax(total);
		}

		@Override
		public void downloading(int completed, int total) {
			ensureProgressDialog();
			installProgress.setTitle("Downloading CouchDB");
			installProgress.setProgress(completed);
			installProgress.setMax(total);
		}

		@Override
		public void exit(String error) {
			Log.v(TAG, error);
			couchError();
		}
	};

	private void ensureProgressDialog() {
		installProgress = new ProgressDialog(CouchAppActivity.this);
		installProgress.setTitle(" ");
		installProgress.setCancelable(false);
		installProgress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		installProgress.show();
	}

	private void startCouch() {
		couchServiceConnection = CouchDB.getService(getBaseContext(), "https://github.com/downloads/couchbaselabs/Android-Couchbase/", "release-0.1", mCallback);
	}

	private void couchError() {
		AlertDialog.Builder builder = new AlertDialog.Builder(self);
		builder.setMessage("Error")
				.setPositiveButton("Try Again?",
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
								startCouch();
							}
						})
				.setNegativeButton("Cancel",
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
								self.moveTaskToBack(true);
							}
						});
		AlertDialog alert = builder.create();
		alert.show();
	}

	private void launchFuton(String url) {
		webView = new WebView(CouchAppActivity.this);
		webView.setWebChromeClient(new WebChromeClient());
		webView.setWebViewClient(new CustomWebViewClient());
		webView.getSettings().setJavaScriptEnabled(true);
		webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
		webView.getSettings().setDomStorageEnabled(true);

		webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);

		webView.requestFocus(View.FOCUS_DOWN);
	    webView.setOnTouchListener(new View.OnTouchListener() {
	        @Override
	        public boolean onTouch(View v, MotionEvent event) {
	            switch (event.getAction()) {
	                case MotionEvent.ACTION_DOWN:
	                case MotionEvent.ACTION_UP:
	                    if (!v.hasFocus()) {
	                        v.requestFocus();
	                    }
	                    break;
	            }
	            return false;
	        }
	    });

		setContentView(webView);
		webView.loadUrl(url);
	};

	private class CustomWebViewClient extends WebViewClient {
		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			view.loadUrl(url);
			return true;
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
	    if ((keyCode == KeyEvent.KEYCODE_BACK) && webView.canGoBack()) {
	    	webView.goBack();
	        return true;
	    }
	    return super.onKeyDown(keyCode, event);
	}

	public String getLocalIpAddress() {
		try {
			for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = enumIpAddr.nextElement();
					if (!inetAddress.isLoopbackAddress()) {
						return inetAddress.getHostAddress().toString();
					}
				}
			}
		} catch (SocketException ex) {
			ex.printStackTrace();
		}
		return null;
	}

	/*
	* Will check for the existence of a design doc and if it doesnt exist,
	* upload the json found at dataPath to create it
	*/
	private void ensureDesignDoc(String dbName, String url) {

		try {
			String data = readAsset(getAssets(), dbName + ".json");
			String ddocUrl = url + dbName + "/_design/" + dbName;

			AndCouch req = AndCouch.get(ddocUrl);

			if (req.status == 404) {
				AndCouch.put(url + dbName, null);
				AndCouch.put(ddocUrl, data);
			}

		} catch (IOException e) {
			e.printStackTrace();
			// There is no design doc to load
		} catch (JSONException e) {
			e.printStackTrace();
		}
	};

	public static String readAsset(AssetManager assets, String path) throws IOException {
		InputStream is = assets.open(path);
		int size = is.available();
		byte[] buffer = new byte[size];
		is.read(buffer);
		is.close();
		return new String(buffer);
	}

}