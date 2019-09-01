package com.example.proxy.example;

import java.io.IOException;
import java.net.ProtocolException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import com.example.proxy.config.BeanFactory;
import com.example.proxy.utils.HttpConnectionHelper;

@RestController
@RequestMapping(path = { Proxy1.PATH_PART_QUERY_TARGET })
public class Proxy1 {

	private Logger logger = LoggerFactory.getLogger(getClass());
	
	static final String PATH_PART_QUERY_TARGET = "/api/proxy/**";
	
	private String[] BLOCKED_REQUEST_HEADERS = { "authorization" }; // "host", "referer"
	

	private final BeanFactory beanFactory;
	
	public Proxy1(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@SuppressWarnings({ "rawtypes" })
	@RequestMapping(//
			method = { //
					RequestMethod.GET, //
					RequestMethod.POST, //
					RequestMethod.PATCH, //
					RequestMethod.PUT, //
					RequestMethod.DELETE })
	protected void service(HttpServletRequest request, HttpServletResponse response) throws Exception {

		HttpConnectionHelper serviceDispatcherHelper;
		String baseUri;

		baseUri = "https://www.google.com";
		logger.info("base URI is: " + baseUri);

		// Sample --> /api/proxy/** will proxy to google.com
		logger.info("Request servlet path is:" + request.getServletPath());

		String requestMethod = request.getMethod();
		String requestURI = request.getRequestURI();
		String matchingPattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
		String pathInfo = requestURI.replace(matchingPattern.substring(0, matchingPattern.lastIndexOf("/")), "");
		String queryString = request.getQueryString();

		logger.info("requested method is: " + requestMethod + " and request URL is: " + request.getRequestURL());

		// Build target URL
		String targetURL = null;
		if (pathInfo.indexOf("/") != -1) {
			// Either use the base URI or convert the pathInfo (containing the full URL)
			if (baseUri != null) {
				targetURL = baseUri;
				targetURL += pathInfo;
			}
			/*- 
			else {
				int indexOfSlash = pathInfo.indexOf("/", 1);
				if (indexOfSlash > 0) {
					targetURL = pathInfo.substring(1, indexOfSlash);
					targetURL += "://";
					targetURL += pathInfo.substring(indexOfSlash + 1);
				} else {
					response.sendError(HttpServletResponse.SC_BAD_REQUEST,
							"The proxy request doesn't match the structure \"protocol/domain/path\"!");
					return;
				}
			}
			*/

			// Add the query string to the target URL
			if (queryString != null && !queryString.isEmpty()) {
				queryString = queryString.replace(" ", "%20");
				targetURL += "?";
				// targetURL += URLEncoder.encode(queryString, "UTF-8");
				targetURL += queryString;
			}
		}

		if (targetURL != null) {
			logger.info("Target URL before is: " + targetURL);
			serviceDispatcherHelper = beanFactory.getHttpConnectionHelper(targetURL);
			logger.info("Target URL after is: " + serviceDispatcherHelper.getTargetUrl().toString());
			try {
				serviceDispatcherHelper.openConnection(requestMethod);
			} catch (ProtocolException pex) {
				try {
					serviceDispatcherHelper.configurePatchMethodOverDelegate(requestMethod);
				} catch (Exception ex) {
					throw new RuntimeException(ex); // NOSONAR
				}
			}

			serviceDispatcherHelper.getConnection().setDoOutput(true);
			serviceDispatcherHelper.getConnection().setDoInput(true);
			serviceDispatcherHelper.getConnection().setUseCaches(false);

			List<String> blockedHeaders = Arrays.asList(BLOCKED_REQUEST_HEADERS);
			for (Enumeration requestHeaderName = request.getHeaderNames(); requestHeaderName.hasMoreElements();) {
				String headerName = requestHeaderName.nextElement().toString();
				if (!blockedHeaders.contains(headerName.toLowerCase())) { // NOSONAR
					serviceDispatcherHelper.getConnection().setRequestProperty(headerName,
							request.getHeader(headerName));
					logger.info("header is: " + headerName + " header value is: " + request.getHeader(headerName));
				}
			}

			try {
				/* Add custom header example */
				serviceDispatcherHelper.getConnection().setRequestProperty(HttpHeaders.AUTHORIZATION,
						"Bearer " + "token value");
			} catch (Exception ex) {
				logger.info(ex.getMessage());
			}

			// Pipe the POST, PUT, PATCH, MERGE and DELETE content
			if ("POST".equals(requestMethod) || "PUT".equals(requestMethod) || "PATCH".equals(requestMethod)
					|| "MERGE".equals(requestMethod)) {
				// serviceDispatcherHelper.getConnection().setChunkedStreamingMode(1024);
				// PATCH/MERGE => PUT (semantic requests - difference is client intent)
				serviceDispatcherHelper.pipe(request.getInputStream(),
						serviceDispatcherHelper.getConnection().getOutputStream());
			} else if ("DELETE".equals(requestMethod) && request.getContentLength() > 0) {
				/*
				 * Special handling for DELETE requests (HttpUrlConnection doesn't support
				 * content) pipe the DELETE content if possible (and only if content is
				 * available)
				 */
				try {
					serviceDispatcherHelper.pipe(request.getInputStream(),
							serviceDispatcherHelper.getConnection().getOutputStream());
				} catch (ProtocolException ex) {
					response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
							"The HttpUrlConnection used by the SimpleProxyServlet doesn't allow to send \n"
									+ "content with the HTTP method DELETE. Due to spec having content for DELETE methods \n"
									+ "is possible but the default implementation of the HttpUrlConnection from SUN doesn't allow this!");
					return;
				}
			}

			// Set response status code
			response.setStatus(serviceDispatcherHelper.getConnection().getResponseCode());

			logger.info("Response code is: " + serviceDispatcherHelper.getConnection().getResponseCode());

			// Forward the response headers
			serviceDispatcherHelper.copyHeadersToResponse(serviceDispatcherHelper.getConnection().getHeaderFields(),
					response);

			try {
				logger.info("piping the response started");
				// Pipe and return the response (either the input or the error stream)
				serviceDispatcherHelper.pipe(serviceDispatcherHelper.getConnection().getInputStream(),
						response.getOutputStream());

			} catch (IOException ex) {
				/*
				 * In case of the input stream is not available we simply forward the error
				 * stream of the connection (which should be the case for 400 requests) => error
				 * stream could also be null
				 */
				if (serviceDispatcherHelper.getConnection().getErrorStream() != null) {
					logger.info("Target error response stream: "
							+ serviceDispatcherHelper.getConnection().getErrorStream().toString());
				} else {
					logger.info("Target error response stream is empty");
					logger.info("Error message is: " + ex.getMessage());
					ex.printStackTrace();
				}
				serviceDispatcherHelper.pipe(serviceDispatcherHelper.getConnection().getErrorStream(),
						response.getOutputStream());
			} finally {
				serviceDispatcherHelper.closeConnection();
			}

			logger.info("process completed");
		} else {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		}
	}
}
