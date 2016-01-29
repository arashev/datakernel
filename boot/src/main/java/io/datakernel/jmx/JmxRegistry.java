/*
 * Copyright (C) 2015 SoftIndex LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.datakernel.jmx;

import com.google.inject.Key;
import io.datakernel.worker.WorkerPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static io.datakernel.jmx.Utils.isJmxMBean;
import static io.datakernel.util.Preconditions.checkNotNull;
import static java.lang.String.format;
import static java.util.Arrays.asList;

public final class JmxRegistry {
	private static final Logger logger = LoggerFactory.getLogger(JmxRegistry.class);

	private final MBeanServer mbs;
	private final DynamicMBeanFactory mbeanFactory;

	public JmxRegistry(MBeanServer mbs, DynamicMBeanFactory mbeanFactory) {
		this.mbs = mbs;
		this.mbeanFactory = mbeanFactory;
	}

	public void registerSingleton(Key<?> key, Object singletonInstance) {
		checkNotNull(singletonInstance);
		checkNotNull(key);

		if (!isJmxMBean(singletonInstance.getClass())) {
			logger.info(format("Instance with key %s was not registered to jmx, " +
					"because its type is not annotated with @JmxMBean", key.toString()));
			return;
		}

		DynamicMBean mbean;
		try {
			mbean = mbeanFactory.createFor(asList(singletonInstance), true);
		} catch (Exception e) {
			String msg = format("Instance with key %s is annotated with @JmxMBean " +
					"but exception was thrown during attempt to create DynamicMBean", key.toString());
			logger.error(msg, e);
			return;
		}

		String name;
		try {
			name = createNameForKey(key);
		} catch (Exception e) {
			String msg = format("Error during generation name for instance with key %s", key.toString());
			logger.error(msg, e);
			return;
		}

		ObjectName objectName;
		try {
			objectName = new ObjectName(name);
		} catch (MalformedObjectNameException e) {
			String msg = format("Cannot create ObjectName for instance with key %s. " +
					"Proposed String name was \"%s\".", key.toString(), name);
			logger.error(msg, e);
			return;
		}

		try {
			mbs.registerMBean(mbean, objectName);
			logger.info(format("Instance with key %s was successfully registered to jmx " +
					"with ObjectName \"%s\" ", key.toString(), objectName.toString()));
		} catch (NotCompliantMBeanException | InstanceAlreadyExistsException | MBeanRegistrationException e) {
			String msg = format("Cannot register MBean for instance with key %s and ObjectName \"%s\"",
					key.toString(), objectName.toString());
			logger.error(msg, e);
			return;
		}
	}

	public void unregisterSingleton(Key<?> key, Object singletonInstance) {
		checkNotNull(key);

		if (isJmxMBean(singletonInstance.getClass())) {
			try {
				String name = createNameForKey(key);
				ObjectName objectName = new ObjectName(name);
				mbs.unregisterMBean(objectName);
			} catch (Exception e) {
				String msg =
						format("Error during attempt to unregister MBean for instance with key %s.", key.toString());
				logger.error(msg, e);
			}
		}
	}

	public void registerWorkers(Key<?> key, List<?> poolInstances) {
		checkNotNull(poolInstances);
		checkNotNull(key);

		if (poolInstances.size() == 0) {
			logger.info(format("Pool of instances with key %s is empty", key.toString()));
			return;
		}

		if (!allInstancesAreOfSameType(poolInstances)) {
			logger.info(format("Pool of instances with key %s was not registered to jmx " +
					"because their types differ", key.toString()));
			return;
		}

		if (!isJmxMBean(poolInstances.get(0).getClass())) {
			logger.info(format("Pool of instances with key %s was not registered to jmx, " +
					"because instances' type is not annotated with @JmxMBean", key.toString()));
			return;
		}

		String commonName;
		try {
			commonName = createNameForKey(key);
		} catch (Exception e) {
			String msg = format("Error during generation name for pool of instances with key %s", key.toString());
			logger.error(msg, e);
			return;
		}

		// register mbeans for each worker separately
		for (int i = 0; i < poolInstances.size(); i++) {
			registerMBeanForWorker(poolInstances.get(i), i, commonName, key);
		}

		// register aggregated mbean for pool of workers
		DynamicMBean mbean;
		try {
			mbean = mbeanFactory.createFor(poolInstances, true);
		} catch (Exception e) {
			String msg = format("Cannot create DynamicMBean for aggregated MBean of pool of workers with key %s",
					key.toString());
			logger.error(msg, e);
			return;
		}

		ObjectName objectName;
		try {
			objectName = new ObjectName(commonName);
		} catch (MalformedObjectNameException e) {
			String msg = format("Cannot create ObjectName for aggregated MBean of pool of workers with key %s. " +
					"Proposed String name was \"%s\".", key.toString(), commonName);
			logger.error(msg, e);
			return;
		}

		try {
			mbs.registerMBean(mbean, objectName);
			logger.info(format("Pool of instances with key %s was successfully registered to jmx " +
					"with ObjectName \"%s\"", key.toString(), objectName.toString()));
		} catch (NotCompliantMBeanException | InstanceAlreadyExistsException | MBeanRegistrationException e) {
			String msg = format("Cannot register aggregated MBean of pool of workers with key %s " +
					"and ObjectName \"%s\"", key.toString(), objectName.toString());
			logger.error(msg, e);
			return;
		}
	}

	public void unregisterWorkers(Key<?> key, List<?> poolInstances) {
		checkNotNull(key);

		if (poolInstances.size() == 0) {
			return;
		}

		if (!allInstancesAreOfSameType(poolInstances)) {
			return;
		}

		if (!isJmxMBean(poolInstances.get(0).getClass())) {
			return;
		}

		String commonName;
		try {
			commonName = createNameForKey(key);
		} catch (Exception e) {
			String msg = format("Error during generation name for pool of instances with key %s", key.toString());
			logger.error(msg, e);
			return;
		}

		// unregister mbeans for each worker separately
		for (int i = 0; i < poolInstances.size(); i++) {
			try {
				String workerName = createWorkerName(commonName, i);
				mbs.unregisterMBean(new ObjectName(workerName));
			} catch (Exception e) {
				String msg = format("Error during attempt to unregister mbean for worker" +
								" of pool of instances with key %s. Worker id is \"%d\"",
						key.toString(), i);
				logger.error(msg, e);
			}
		}

		// unregister aggregated mbean for pool of workers
		try {
			mbs.unregisterMBean(new ObjectName(commonName));
		} catch (Exception e) {
			String msg = format("Error during attempt to unregister aggregated mbean for pool of instances " +
					"with key %s.", key.toString());
			logger.error(msg, e);
		}
	}

	private boolean allInstancesAreOfSameType(List<?> instances) {
		int last = instances.size() - 1;
		for (int i = 0; i < last; i++) {
			if (!instances.get(i).getClass().equals(instances.get(i + 1).getClass())) {
				return false;
			}
		}
		return true;
	}

	private void registerMBeanForWorker(Object worker, int workerId, String commonName, Key<?> key) {
		String workerName = createWorkerName(commonName, workerId);

		DynamicMBean mbean;
		try {
			mbean = mbeanFactory.createFor(asList(worker), false);
		} catch (Exception e) {
			String msg = format("Cannot create DynamicMBean for worker " +
					"of pool of instances with key %s", key.toString());
			logger.error(msg, e);
			return;
		}

		ObjectName objectName;
		try {
			objectName = new ObjectName(workerName);
			;
		} catch (MalformedObjectNameException e) {
			String msg = format("Cannot create ObjectName for worker of pool of instances with key %s. " +
					"Proposed String name was \"%s\".", key.toString(), workerName);
			logger.error(msg, e);
			return;
		}

		try {
			mbs.registerMBean(mbean, objectName);
		} catch (NotCompliantMBeanException | InstanceAlreadyExistsException | MBeanRegistrationException e) {
			String msg = format("Cannot register MBean for worker of pool of instances with key %s. " +
					"ObjectName for worker is \"%s\"", key.toString(), objectName.toString());
			logger.error(msg, e);
			return;
		}
	}

	private static String createWorkerName(String commonName, int workerId) {
		return commonName + format(",workerId=worker-%d", workerId);
	}

	private static String createNameForKey(Key<?> key) throws Exception {
		Class<?> type = key.getTypeLiteral().getRawType();
		Annotation annotation = key.getAnnotation();
		String domain = type.getPackage().getName();
		String name = domain + ":";
		if (annotation == null) { // without annotation
			name += "type=" + type.getSimpleName();
		} else {
			Class<? extends Annotation> annotationType = annotation.annotationType();
			Method[] annotationElements = filterNonEmptyElements(annotation);
			if (annotationElements.length == 0) { // annotation without elements
				name += "type=" + type.getSimpleName() + ",annotation=" + annotationType.getSimpleName();
			} else if (annotationElements.length == 1 && annotationElements[0].getName().equals("value")) {
				// annotation with single element which has name "value"
				Object value = fetchAnnotationElementValue(annotation, annotationElements[0]);
				name += annotationType.getSimpleName() + "=" + value.toString();
			} else { // annotation with one or more custom elements
				for (Method annotationParameter : annotationElements) {
					Object value = fetchAnnotationElementValue(annotation, annotationParameter);
					String nameKey = annotationParameter.getName();
					String nameValue = value.toString();
					name += nameKey + "=" + nameValue + ",";
				}

				assert name.substring(name.length() - 1).equals(",");

				name = name.substring(0, name.length() - 1);
			}
		}
		return name;
	}

	/**
	 * Returns values if it is not null, otherwise throws exception
	 */
	private static Object fetchAnnotationElementValue(Annotation annotation, Method element)
			throws InvocationTargetException, IllegalAccessException {
		Object value = element.invoke(annotation);
		if (value == null) {
			String errorMsg = "@" + annotation.annotationType().getName() + "." +
					element.getName() + "() returned null";
			throw new NullPointerException(errorMsg);
		}
		return value;
	}

	private static Method[] filterNonEmptyElements(Annotation annotation)
			throws InvocationTargetException, IllegalAccessException {
		List<Method> filtered = new ArrayList<>();
		for (Method method : annotation.annotationType().getDeclaredMethods()) {
			Object elementValue = fetchAnnotationElementValue(annotation, method);
			if (elementValue instanceof String) {
				String stringValue = (String) elementValue;
				if (stringValue.length() == 0) {
					// skip this element, because it is empty string
					continue;
				}
			}
			filtered.add(method);
		}
		return filtered.toArray(new Method[filtered.size()]);
	}
}
