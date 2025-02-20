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

import org.apache.skywalking.apm.agent.core.boot.AgentPackageNotFoundException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The <code>InterceptorInstanceLoader</code> is a classes finder and container.
 * <p>
 * This is a very important class in sky-walking's auto-instrumentation mechanism. If you want to fully understand why
 * need this, and how it works, you need have knowledge about Classloader appointment mechanism.
 * <p>
 */
public class InterceptorInstanceLoader {

    private static ConcurrentHashMap<String, Object> INSTANCE_CACHE = new ConcurrentHashMap<String, Object>();
    private static ReentrantLock INSTANCE_LOAD_LOCK = new ReentrantLock();
    // key: 加载当前插件要拦截的那个类的 类加载器
    // value: 既能加载插件拦截器，又能加载要拦截的那个类的 类加载器
    private static Map<ClassLoader, ClassLoader> EXTEND_PLUGIN_CLASSLOADERS = new HashMap<ClassLoader, ClassLoader>();

    /**
     * Load an instance of interceptor, and keep it singleton. Create {@link AgentClassLoader} for each
     * targetClassLoader, as an extend classloader. It can load interceptor classes from plugins, activations folders.
     * 需要使 加载 interceptorName 的类加载器作为 targetClassLoader 的子类加载器 可以达到互相访问
     * 
     * @param className         the interceptor class, which is expected to be found 插件中拦截器的全类名
     * @param targetClassLoader the class loader for current application context 要想在插件拦截器中 能够访问到被拦截的类，需要是同一个类加载器 或 子类类加载器
     *     targetClassLoader 就是 {@link org.apache.skywalking.apm.agent.SkyWalkingAgent.Transformer#transform} 中的 classLoader
     * @param <T>               expected type
     * @return the type reference.
     */
    public static <T> T load(String className,
        ClassLoader targetClassLoader) throws IllegalAccessException, InstantiationException, ClassNotFoundException, AgentPackageNotFoundException {
        if (targetClassLoader == null) {
            targetClassLoader = InterceptorInstanceLoader.class.getClassLoader();
        }
        String instanceKey = className + "_OF_" + targetClassLoader.getClass()
                                                                   .getName() + "@" + Integer.toHexString(targetClassLoader
            .hashCode());
        Object inst = INSTANCE_CACHE.get(instanceKey);
        if (inst == null) {
            INSTANCE_LOAD_LOCK.lock();
            ClassLoader pluginLoader;
            try {
                pluginLoader = EXTEND_PLUGIN_CLASSLOADERS.get(targetClassLoader);
                if (pluginLoader == null) {
                    /**
                     * 正常来说 {@link org.apache.skywalking.apm.plugin.asf.dubbo.DubboInterceptor} 是由 AgentClassLoader 加载的
                     * 所要拦截的方法是 {@link org.apache.dubbo.monitor.support.MonitorFilter#invoke} 是由 AppClassLoader 加载的
                     * DubboInterceptor 看不到 MonitorFilter
                     * 所以这里使得 pluginLoader 父加载器指向加载 当前拦截到的类 的类加载器，也就是 AppClassLoader
                     * AgentClassLoader -parent-> AppClassLoader
                     * 当前的拦截器就能看到父类的方法
                     * 拦截到的类的类加载器是未知的，所以不能复用
                     * 处理完字节码，还是交给拦截到的类的类加载器去加载
                     */
                    pluginLoader = new AgentClassLoader(targetClassLoader);
                    EXTEND_PLUGIN_CLASSLOADERS.put(targetClassLoader, pluginLoader);
                }
            } finally {
                INSTANCE_LOAD_LOCK.unlock();
            }
            inst = Class.forName(className, true, pluginLoader).newInstance();
            if (inst != null) {
                INSTANCE_CACHE.put(instanceKey, inst);
            }
        }

        return (T) inst;
    }
}
