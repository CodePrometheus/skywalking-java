/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.skywalking.apm.plugin.spring.cloud.gateway.v4x.define;

import org.apache.skywalking.apm.agent.core.plugin.WitnessMethod;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;

import java.util.Collections;
import java.util.List;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

/**
 * This abstract class defines the <code>witnessClasses()</code> method,
 * and other plugin define classes need to inherit from this class
 */
public abstract class AbstractGatewayV4EnhancePluginDefine extends ClassInstanceMethodsEnhancePluginDefine {

    /**
     * @since 4.0.0
     */
    @Override
    protected String[] witnessClasses() {
        return new String[]{"org.springframework.cloud.gateway.filter.factory.cache.LocalResponseCacheProperties"};
    }

    @Override
    protected List<WitnessMethod> witnessMethods() {
        return Collections.singletonList(
                new WitnessMethod("org.springframework.cloud.gateway.config.LocalResponseCacheAutoConfiguration",
                        named("responseCacheSizeWeigher").
                                and(returns(named("org.springframework.cloud.gateway.filter.factory.cache.ResponseCacheSizeWeigher")))));
    }
}
