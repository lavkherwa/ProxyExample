package com.example.proxy.config;

import java.net.MalformedURLException;
import java.net.URISyntaxException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import com.example.proxy.utils.HttpConnectionHelper;

@Configuration
public class BeanManager {

	@Bean
	@Scope("prototype")
	public HttpConnectionHelper HttpConnectionHelper(String url) //
			throws MalformedURLException, URISyntaxException {
		HttpConnectionHelper bean = new HttpConnectionHelper(url);
		return bean;
	}

}
