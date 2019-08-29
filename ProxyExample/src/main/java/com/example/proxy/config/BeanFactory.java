package com.example.proxy.config;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import com.example.proxy.utils.HttpConnectionHelper;

@Component
public class BeanFactory implements ApplicationContextAware {

	private ApplicationContext applicationContext;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;

	}

	public HttpConnectionHelper getHttpConnectionHelper(String url) {
		HttpConnectionHelper bean = applicationContext.getBean(HttpConnectionHelper.class, url);
		return bean;
	}

}
