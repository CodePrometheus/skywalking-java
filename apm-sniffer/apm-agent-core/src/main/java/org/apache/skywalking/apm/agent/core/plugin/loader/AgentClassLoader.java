/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.agent.core.plugin.loader;

import lombok.RequiredArgsConstructor;
import org.apache.skywalking.apm.agent.core.boot.AgentPackageNotFoundException;
import org.apache.skywalking.apm.agent.core.boot.AgentPackagePath;
import org.apache.skywalking.apm.agent.core.boot.PluginConfig;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.conf.SnifferConfigInitializer;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.PluginBootstrap;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * The <code>AgentClassLoader</code> represents a classloader, which is in charge of finding plugins and interceptors.
 * 负责查找插件和拦截器
 * 自定义的类加载器，用于加载指定目录的 jar 插件（默认加载的插件目录 plugins 和 activations）
 * AgentClassLoader 继承 ClassLoader，重写 findClass 方法
 */
public class AgentClassLoader extends ClassLoader {

    static {
        /**
         * Try to solve the classloader dead lock. See https://github.com/apache/skywalking/pull/2016
         * 开启类加载器并行加载模式
         * 1.6 jvm 是串行加载 将 classLoad 自身作为锁
         * -----
         * 1.7 并行加载，实现原理是将锁的粒度变小
         * {@link ClassLoader#registerAsParallelCapable()}
            Class<? extends ClassLoader> callerClass =
            Reflection.getCallerClass().asSubclass(ClassLoader.class); 进行类转换，将调用方法的类转为 ClassLoader 的子类

            {@link ParallelLoaders#register}
              static boolean register(Class<? extends ClassLoader> c) {
                synchronized (loaderTypes) { // loaderTypes 所有具有并行能力的类加载器
                    if (loaderTypes.contains(c.getSuperclass())) {
                        // 判断是否当前类的父类是否具有并行能力
                        loaderTypes.add(c);
                        return true;
                    } else {
                        return false;
                    }
                 }
              }
               protected Object getClassLoadingLock(String className) {
                 Object lock = this; // 而不是整个类加载器
                    if (parallelLockMap != null) {
                    Object newLock = new Object();
                    lock = parallelLockMap.putIfAbsent(className, newLock); // 将锁的粒度降低到具体加载的某个类上
                    if (lock == null) {
                        lock = newLock;
                    }
                }
                return lock;
               }
         */
        registerAsParallelCapable();
    }

    private static final ILog LOGGER = LogManager.getLogger(AgentClassLoader.class);
    /**
     * The default class loader for the agent.
     */
    private static AgentClassLoader DEFAULT_LOADER;

    private List<File> classpath;
    private List<Jar> allJars;
    private ReentrantLock jarScanLock = new ReentrantLock();

    public static AgentClassLoader getDefault() {
        return DEFAULT_LOADER;
    }

    /**
     * Init the default class loader.
     * 双重检查
     *
     * @throws AgentPackageNotFoundException if agent package is not found.
     */
    public static void initDefaultLoader() throws AgentPackageNotFoundException {
        if (DEFAULT_LOADER == null) {
            synchronized (AgentClassLoader.class) {
                if (DEFAULT_LOADER == null) {
                    // PluginBootstrap 调用该方法的时候，PluginBootstrap 已经被加载了
                    // 将 PluginBootstrap 作为父类加载器
                    DEFAULT_LOADER = new AgentClassLoader(PluginBootstrap.class.getClassLoader());
                }
            }
        }
    }

    public AgentClassLoader(ClassLoader parent) throws AgentPackageNotFoundException {
        super(parent);
        File agentDictionary = AgentPackagePath.getPath(); // 定位 agent.jar
        classpath = new LinkedList<>();
        // Arrays.asList("plugins", "activations");
        // 默认这两个包下的 jar 包被加载
        Config.Plugin.MOUNT.forEach(mountFolder -> classpath.add(new File(agentDictionary, mountFolder)));
    }

    /**
     * Class.forName(name, true, AgentClassLoader.getDefault()) 使用 AgentClassLoader 类加载器时，会调用这个 findClass 方法
     */
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        List<Jar> allJars = getAllJars();
        String path = name.replace('.', '/').concat(".class");
        for (Jar jar : allJars) {
            JarEntry entry = jar.jarFile.getJarEntry(path);
            if (entry == null) {
                continue;
            }
            try {
                URL classFileUrl = new URL("jar:file:" + jar.sourceFile.getAbsolutePath() + "!/" + path);
                byte[] data;
                try (final BufferedInputStream is = new BufferedInputStream(
                        classFileUrl.openStream()); final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    int ch;
                    while ((ch = is.read()) != -1) {
                        baos.write(ch);
                    }
                    // 转成字符数组
                    data = baos.toByteArray();
                }
                return processLoadedClass(defineClass(name, data, 0, data.length));
            } catch (IOException e) {
                LOGGER.error(e, "find class fail.");
            }
        }
        throw new ClassNotFoundException("Can't find " + name);
    }

    @Override
    protected URL findResource(String name) {
        List<Jar> allJars = getAllJars();
        for (Jar jar : allJars) {
            JarEntry entry = jar.jarFile.getJarEntry(name);
            if (entry != null) {
                try {
                    return new URL("jar:file:" + jar.sourceFile.getAbsolutePath() + "!/" + name);
                } catch (MalformedURLException ignored) {
                }
            }
        }
        return null;
    }

    @Override
    protected Enumeration<URL> findResources(String name) throws IOException {
        List<URL> allResources = new LinkedList<>();
        List<Jar> allJars = getAllJars();
        for (Jar jar : allJars) {
            JarEntry entry = jar.jarFile.getJarEntry(name);
            if (entry != null) {
                allResources.add(new URL("jar:file:" + jar.sourceFile.getAbsolutePath() + "!/" + name));
            }
        }

        final Iterator<URL> iterator = allResources.iterator();
        return new Enumeration<URL>() {
            @Override
            public boolean hasMoreElements() {
                return iterator.hasNext();
            }

            @Override
            public URL nextElement() {
                return iterator.next();
            }
        };
    }

    private Class<?> processLoadedClass(Class<?> loadedClass) {
        final PluginConfig pluginConfig = loadedClass.getAnnotation(PluginConfig.class);
        if (pluginConfig != null) {
            // Set up the plugin config when loaded by class loader at the first time.
            // Agent class loader just loaded limited classes in the plugin jar(s), so the cost of this
            // isAssignableFrom would be also very limited.
            // 如果带上了 @PluginConfig 注解，将会去走一遍初始化配置
            SnifferConfigInitializer.initializeConfig(pluginConfig.root());
        }

        return loadedClass;
    }

    private List<Jar> getAllJars() {
        if (allJars == null) {
            jarScanLock.lock();
            try {
                if (allJars == null) {
                    allJars = doGetJars();
                }
            } finally {
                jarScanLock.unlock();
            }
        }

        return allJars;
    }

    private LinkedList<Jar> doGetJars() {
        LinkedList<Jar> jars = new LinkedList<>();
        for (File path : classpath) {
            if (path.exists() && path.isDirectory()) {
                String[] jarFileNames = path.list((dir, name) -> name.endsWith(".jar"));
                for (String fileName : jarFileNames) {
                    try {
                        File file = new File(path, fileName);
                        Jar jar = new Jar(new JarFile(file), file);
                        jars.add(jar);
                        LOGGER.info("{} loaded.", file.toString());
                    } catch (IOException e) {
                        LOGGER.error(e, "{} jar file can't be resolved", fileName);
                    }
                }
            }
        }
        return jars;
    }

    @RequiredArgsConstructor
    private static class Jar {
        private final JarFile jarFile;
        private final File sourceFile;
    }
}
