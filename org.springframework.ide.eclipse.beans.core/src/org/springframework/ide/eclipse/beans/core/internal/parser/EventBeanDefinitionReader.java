/*
 * Copyright 2002-2006 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ide.eclipse.beans.core.internal.parser;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.support.BeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.xml.XmlBeanDefinitionParser;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.ide.eclipse.beans.core.BeanDefinitionException;
import org.springframework.ide.eclipse.beans.core.BeansCorePlugin;
import org.springframework.ide.eclipse.core.io.FileResourceLoader;
import org.springframework.ide.eclipse.core.io.xml.LineNumberPreservingDOMParser;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Bean definition reader for XML bean definitions. Delegates the actual XML
 * parsing to <code>EventBeanDefinitionParser</code> an implementation of the
 * <code>XmlBeanDefinitionParser</code> interface.
 * Applied by the <code>EventBeanFactory</code>.
 *
 * <p>This class loads a DOM document and applies the bean definition parser to it.
 * The parser will register each bean definition with the given bean factory,
 * relying on the latter's implementation of the
 * <code>BeanDefinitionRegistry</code> interface.
 *
 * @see EventBeanDefinitionRegistry
 * @see EventBeanDefinitionParser
 * @see org.springframework.beans.factory.xml.XmlBeanDefinitionParser
 * @see org.springframework.beans.factory.support.BeanDefinitionRegistry
 *
 * @author Torsten Juergeleit
 */
public class EventBeanDefinitionReader implements BeanDefinitionReader {

	private static final String WRONG_XERCES_MESSAGE = "Are you using a JRE " +
						 "with an outdated version of the Xerces XML parser? "+
						 "Please check the 'endorsed' folder of your JRE.";
	public static final String DEBUG_OPTION = BeansCorePlugin.PLUGIN_ID +
																"/reader/debug";
	public static boolean DEBUG = BeansCorePlugin.isDebug(DEBUG_OPTION);

	private BeanDefinitionRegistry beanFactory;
	private IBeanDefinitionEvents eventHandler;
	private ResourceLoader resourceLoader;

	public EventBeanDefinitionReader(BeanDefinitionRegistry beanFactory,
									 IBeanDefinitionEvents eventHandler) {
		this.beanFactory = beanFactory;
		this.eventHandler = eventHandler;
		this.resourceLoader = new FileResourceLoader();
	}

	public BeanDefinitionRegistry getBeanFactory() {
		return this.beanFactory;
	}

	/**
	 * Returns null to prevent class loading of bean classes. 
	 */
	public ClassLoader getBeanClassLoader() {
		return null;
	}

	/**
	 * Returns instance of <code>FileResourceLoader</code>.
	 * @see FileResourceLoader 
	 */
	public ResourceLoader getResourceLoader() {
		return this.resourceLoader;
	}

	public int loadBeanDefinitions(Resource[] resources) throws BeansException {
		int counter = 0;
		for (int i = 0; i < resources.length; i++) {
			counter += loadBeanDefinitions(resources[i]);
		}
		return counter;
	}

	public int loadBeanDefinitions(Resource resource) throws BeansException {
		if (DEBUG) {
			System.out.println("Reading config from " + resource);
		}
		InputStream input = null;
		try {
			input = resource.getInputStream();
			InputSource inputSource = new InputSource(input);
			inputSource.setSystemId(resource.getDescription());
			LineNumberPreservingDOMParser parser =
											new LineNumberPreservingDOMParser();
			parser.setFeature("http://xml.org/sax/features/validation", true);
			parser.setFeature(
					 "http://apache.org/xml/features/validation/dynamic", true);
			parser.setEntityResolver(new BeansDtdResolver());
			parser.setErrorHandler(new BeansErrorHandler());
			parser.parse(inputSource);
			return registerBeanDefinitions(parser.getDocument(), resource);
		} catch (NoClassDefFoundError e) {
			throw new BeanDefinitionException(-1, WRONG_XERCES_MESSAGE, e);
		} catch (NoSuchMethodError e) {
			throw new BeanDefinitionException(-1, WRONG_XERCES_MESSAGE, e);
		} catch (ClassCastException e) {
			throw new BeanDefinitionException(-1, WRONG_XERCES_MESSAGE, e);
		} catch (SAXException e) {
			throw new BeanDefinitionException(e);
		} catch (DOMException e) {
			throw new BeanDefinitionException(e);
		} catch (IOException e) {
			throw new BeanDefinitionException(e);
		} catch (BeansException e) {
			throw new BeanDefinitionException(e);
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException e) {
					BeansCorePlugin.log("Could not close InputStream", e);
				}
			}
		}
	}

	public int loadBeanDefinitions(String location)
										  throws BeanDefinitionStoreException {
		ResourceLoader resourceLoader = getResourceLoader();
		if (resourceLoader == null) {
			throw new BeanDefinitionStoreException("Cannot import bean " +
									 "definitions from location [" + location +
									 "]: no ResourceLoader available");
		}
		if (resourceLoader instanceof ResourcePatternResolver) {

			// Resource pattern matching available.
			try {
				Resource[] resources = ((ResourcePatternResolver)
										resourceLoader).getResources(location);
				int loadCount = loadBeanDefinitions(resources);
				return loadCount;
			} catch (IOException e) {
				throw new BeanDefinitionStoreException("Could not resolve " +
					 "bean definition resource pattern [" + location + "]", e);
			}
		} else {
			// Can only load single resources by absolute URL.
			Resource resource = resourceLoader.getResource(location);
			return loadBeanDefinitions(resource);
		}
	}

	/**
	 * Register the bean definitions contained in the given DOM document.
	 * Called by <code>loadBeanDefinitions</code>.
	 * <p>Creates a new instance of the parser class and invokes
	 * <code>registerBeanDefinitions</code> on it.
	 * @param doc the DOM document
	 * @param resource the resource descriptor (for context information)
	 * @return the number of bean definitions found
	 * @throws BeansException in case of parsing errors
	 * @see #loadBeanDefinitions
	 * @see #setParserClass
	 * @see XmlBeanDefinitionParser#registerBeanDefinitions
	 */
	public int registerBeanDefinitions(Document doc, Resource resource)
														throws BeansException {
		EventBeanDefinitionParser parser = new EventBeanDefinitionParser(
															this.eventHandler);
		return parser.registerBeanDefinitions(this, doc, resource);
	}

	/**
	 * Private implementation of SAX ErrorHandler used when validating XML.
	 */
	private static class BeansErrorHandler implements ErrorHandler {

		public void error(SAXParseException e) throws SAXException {
			throw e;
		}

		public void fatalError(SAXParseException e) throws SAXException {
			throw e;
		}

		public void warning(SAXParseException e) throws SAXException {
			// ignore XML parse warnings
		}
	}
}
