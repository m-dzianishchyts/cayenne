/*****************************************************************
 *   Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 ****************************************************************/

package org.apache.cayenne.gen;

import org.apache.cayenne.di.Binder;
import org.apache.cayenne.di.Module;
import org.apache.cayenne.di.spi.DefaultScope;
import org.apache.cayenne.gen.mock.CustomPropertyDescriptor;
import org.apache.cayenne.unit.di.UnitTestLifecycleManager;
import org.apache.cayenne.unit.di.server.ServerCaseExtraModules;
import org.apache.cayenne.unit.di.server.ServerCaseLifecycleManager;
import org.apache.cayenne.unit.di.server.ServerCaseProperties;

/**
 * @since 4.2
 */
public class CgenCaseModule implements Module {

    protected DefaultScope testScope;

    public CgenCaseModule(DefaultScope testScope) {
        this.testScope = testScope;
    }
    @Override
    public void configure(Binder binder) {
        binder.bind(UnitTestLifecycleManager.class).toInstance(new ServerCaseLifecycleManager(testScope));
        binder.bind(ServerCaseProperties.class).to(ServerCaseProperties.class).in(testScope);
        binder.bind(ServerCaseExtraModules.class).to(ServerCaseExtraModules.class).in(testScope);

        CgenModule.contributeUserProperties(binder)
                .add(CustomPropertyDescriptor.class);
    }
}
