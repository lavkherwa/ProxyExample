package com.example.proxy.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.servlet.http.HttpServletResponse;

import org.apache.tomcat.util.http.fileupload.IOUtils;

public class HttpConnectionHelper {

	private HttpURLConnection targetConnection = null;
	
	private URL targetUrl = null;

	private String[] BLOCKED_RESPONSE_HEADERS = { "transfer-encoding" };

	public HttpConnectionHelper(final String url) throws URISyntaxException, MalformedURLException {
		
		URI serviceUrl = new URI(url);
		serviceUrl = serviceUrl.normalize();
		
		this.targetUrl = serviceUrl.toURL();
	}

	public void openConnection(final String requestMethod) throws IOException {
		
		targetConnection = (HttpURLConnection) targetUrl.openConnection();
		
		/* infinite timeout for this connection if provided 0 */
		targetConnection.setConnectTimeout(5000);
		targetConnection.setRequestMethod(requestMethod);
	}

	public HttpURLConnection getConnection() {
		return targetConnection;
	}

	public URL getTargetUrl() {
		return targetUrl;
	}

	public void setRequestHeader(final String headerName, final String headerValue) {
		targetConnection.setRequestProperty(headerName, headerValue);
	}

	public void closeConnection() {
		targetConnection.disconnect();
	}

	public int getResponseCode() throws IOException {
		return targetConnection.getResponseCode();
	}

	public void copyHeadersToResponse(final Map<String, List<String>> sourceHeaders,
			final HttpServletResponse response) {
		List<String> blockedHeaders = Arrays.asList(BLOCKED_RESPONSE_HEADERS);

		for (Map.Entry<String, List<String>> mapEntry : sourceHeaders.entrySet()) {
			String headerName = mapEntry.getKey();
			if (headerName != null && !blockedHeaders.contains(headerName.toLowerCase())) {
				List<String> values = mapEntry.getValue();
				if (values != null) {
					for (String value : values) {
						/*
						 * we always filter the secure header to avoid the cookie from "not" being
						 * included in follow up requests in case of the PROXY is running on HTTP and
						 * not HTTPS
						 */
						if (value != null && "set-cookie".equalsIgnoreCase(headerName)
								&& value.toLowerCase().contains("secure")) {
							String[] cookieValues = value.split(";");
							String newValue = "";
							for (String cookieValue : cookieValues) {
								if (!"secure".equalsIgnoreCase(cookieValue.trim())) {
									if (!newValue.isEmpty()) {
										newValue += "; ";
									}
									newValue += cookieValue;
								}
							}
							value = newValue;
						}
						response.addHeader(headerName, value);
					}
				}
			}
		}
	}

	/*
	 * Pipe the input streams to output streams to redirects the content
	 */
	public void pipe(InputStream inputStream, OutputStream outputStream) throws IOException {
		try {
			if (inputStream != null && outputStream != null) {
				IOUtils.copyLarge(inputStream, outputStream);
				outputStream.flush();
			}
		} finally {
			if (inputStream != null) {
				inputStream.close();
			}
		}

		/*-
		try {
			if (inputStream != null && outputStream != null) {
				byte[] byteSize = new byte[64 * 1024];
				int currentReadBytes;
				while ((currentReadBytes = inputStream.read(byteSize)) != -1) {
					outputStream.write(byteSize, 0, currentReadBytes);
					outputStream.flush();
				}
			}
		} finally {
			if (inputStream != null) {
				inputStream.close();
			}
		}
		*/

	}

	/*
	 * in case of HttpsUrlConnection the PATCH method needs to be overridden on the
	 * delegate which has been introduced by the JDK to provide an additional level
	 * of abstraction between the javax.net.ssl.HttpURLConnection and
	 * com.sun.net.ssl.HttpURLConnection the delegate is the HttpURLConnection which
	 * is just wrapped for HTTPS scenarios
	 */
	public void configurePatchMethodOverDelegate(String requestMethod) throws Exception {
		if (targetConnection instanceof HttpsURLConnection) {
			Field delegateField = targetConnection.getClass().getDeclaredField("delegate");
			delegateField.setAccessible(true);
			targetConnection = (HttpURLConnection) delegateField.get(targetConnection);
		}
		Field methodField = HttpURLConnection.class.getDeclaredField("method");
		methodField.setAccessible(true);
		methodField.set(targetConnection, requestMethod);
	}
}
