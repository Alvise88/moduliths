/*
 * Copyright 2018 the original author or authors.
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
package de.olivergierke.moduliths.model.test;

import de.olivergierke.moduliths.model.JavaPackage;
import de.olivergierke.moduliths.model.Module;
import de.olivergierke.moduliths.model.Modules;
import de.olivergierke.moduliths.model.test.ModuleTest.BootstrapMode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.core.annotation.AnnotatedElementUtils;

import com.tngtech.archunit.thirdparty.com.google.common.base.Supplier;
import com.tngtech.archunit.thirdparty.com.google.common.base.Suppliers;

/**
 * @author Oliver Gierke
 */
@Slf4j
public class ModuleTestExecution implements Iterable<Module> {

	private static Map<Class<?>, ModuleTestExecution> EXECUTIONS = new HashMap<>();

	private final @Getter BootstrapMode bootstrapMode;
	private final @Getter Module module;
	private final @Getter Modules modules;

	private final Supplier<List<JavaPackage>> basePackages;
	private final Supplier<List<Module>> dependencies;

	private ModuleTestExecution(Class<?> type) {

		ModuleTest annotation = AnnotatedElementUtils.findMergedAnnotation(type, ModuleTest.class);
		String packageName = type.getPackage().getName();

		this.modules = Modules.of(new ModulithConfigurationFinder().findFromClass(type));
		this.bootstrapMode = annotation.mode();
		this.module = modules.getModuleByBasePackage(packageName) //
				.orElseThrow(
						() -> new IllegalStateException(String.format("Couldn't find module for package '%s'!", packageName)));

		this.basePackages = Suppliers.memoize(() -> module.getBasePackages(modules, bootstrapMode.getDepth()) //
				.collect(Collectors.toList()));
		this.dependencies = Suppliers.memoize(() -> module.getDependencies(modules, bootstrapMode.getDepth()));

		if (annotation.verifyAutomatically()) {
			verify();
		}
	}

	public static ModuleTestExecution of(Class<?> type) {
		return EXECUTIONS.computeIfAbsent(type, ModuleTestExecution::new);
	}

	/**
	 * Returns all base packages the current execution needs to use for component scanning, auto-configuration etc.
	 * 
	 * @return
	 */
	public Stream<String> getBasePackages() {
		return basePackages.get().stream().map(JavaPackage::getName);
	}

	public boolean includes(String className) {

		boolean result = modules.withinRootPackages(className) //
				|| basePackages.get().stream().anyMatch(it -> it.contains(className));

		if (result) {
			LOG.debug("Including class {}.", className);
		}

		return !result;
	}

	/**
	 * Returns all module dependencies, based on the current {@link BootstrapMode}.
	 * 
	 * @return
	 */
	public List<Module> getDependencies() {
		return dependencies.get();
	}

	/**
	 * Explicitly trigger the module structure verification.
	 */
	public void verify() {
		modules.verify();
	}

	/* 
	 * (non-Javadoc)
	 * @see java.lang.Iterable#iterator()
	 */
	@Override
	public Iterator<Module> iterator() {
		return modules.iterator();
	}
}
