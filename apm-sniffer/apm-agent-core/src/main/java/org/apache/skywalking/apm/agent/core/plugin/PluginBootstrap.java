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

package org.apache.skywalking.apm.agent.core.plugin;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.apm.agent.core.boot.AgentPackageNotFoundException;
import org.apache.skywalking.apm.agent.core.plugin.loader.AgentClassLoader;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;

/**
 * Plugins finder. Use {@link PluginResourcesResolver} to find all plugins, and ask {@link PluginCfg} to load all plugin
 * definitions.
 */
public class PluginBootstrap {
    private static final ILog LOGGER = LogManager.getLogger(PluginBootstrap.class);

    // Preload ThreadLocalRandom in case of intermittent ClassCircularityError since ByteBuddy 1.12.11
    static {
        try {
            Class.forName("java.util.concurrent.ThreadLocalRandom");
        } catch (Exception e) {
            LOGGER.warn(e, "Preload ThreadLocalRandom failure.");
        }
    }

    /**
     * load all plugins.
     *
     * @return plugin definition list.
     */
    public List<AbstractClassEnhancePluginDefine> loadPlugins() throws AgentPackageNotFoundException {
        // 1.初始化 AgentClassLoader
        AgentClassLoader.initDefaultLoader(); // sw 自定义的类加载器

        // 2.使用 AgentClassLoader 读取插件定义文件 skywalking-plugin.def
        PluginResourcesResolver resolver = new PluginResourcesResolver();
        List<URL> resources = resolver.getResources();

        if (resources == null || resources.size() == 0) {
            LOGGER.info("no plugin files (skywalking-plugin.def) found, continue to start application.");
            return new ArrayList<AbstractClassEnhancePluginDefine>();
        }

        for (URL pluginUrl : resources) {
            try {
                // 3.读取插件定义文件 skywalking-plugin.def 内容，封装成 PluginDefine
                PluginCfg.INSTANCE.load(pluginUrl.openStream());
            } catch (Throwable t) {
                LOGGER.error(t, "plugin file [{}] init failure.", pluginUrl);
            }
        }

        List<PluginDefine> pluginClassList = PluginCfg.INSTANCE.getPluginClassList();

        List<AbstractClassEnhancePluginDefine> plugins = new ArrayList<AbstractClassEnhancePluginDefine>();
        // 对全部插件进行迭代
        for (PluginDefine pluginDefine : pluginClassList) {
            try {
                LOGGER.debug("loading plugin class {}.", pluginDefine.getDefineClass());
                /**
                 * 4.使用 AgentClassLoader 加载并实例化插件定义类
                 * JVM底层会调用 ClassLoader.loadClass 方法，根据类加载器的委托机制(即先委托父类加载器加载类，如果父类加载器无法加载，则由子类加载器加载)
                 * ClassLoader 的 loadClass 方法的内部实现会调用子类 AgentClassLoader 的 findClass 方法
                 * 因为 AgentClassLoader 的所有父加载器都不知道怎么去加载这些插件定义类，只有 AgentClassLoader 知道如何去找
                 * {@link AgentClassLoader#findClass(String name)}
                 */
                AbstractClassEnhancePluginDefine plugin = (AbstractClassEnhancePluginDefine) Class.forName(pluginDefine.getDefineClass(), true, AgentClassLoader
                    .getDefault()).newInstance();
                plugin.setPluginName(pluginDefine.getName());
                plugins.add(plugin);
            } catch (Throwable t) {
                LOGGER.error(t, "load plugin [{}] failure.", pluginDefine.getDefineClass());
            }
        }

        // 加载基于 xml 定义的插件
        plugins.addAll(DynamicPluginLoader.INSTANCE.load(AgentClassLoader.getDefault()));

        return plugins;

    }

}
