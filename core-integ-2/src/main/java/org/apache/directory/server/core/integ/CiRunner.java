/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.directory.server.core.integ;


import static org.apache.directory.server.core.integ.state.TestServiceContext.cleanup;
import static org.apache.directory.server.core.integ.state.TestServiceContext.destroy;
import static org.apache.directory.server.core.integ.state.TestServiceContext.shutdown;
import static org.apache.directory.server.core.integ.state.TestServiceContext.test;

import org.junit.Ignore;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A test runner for ApacheDS Core integration tests.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class CiRunner extends BlockJUnit4ClassRunner
{
    private static final Logger LOG = LoggerFactory.getLogger( CiRunner.class );
    private CiSuite suite;
    private InheritableSettings settings;


    public CiRunner( Class<?> clazz ) throws InitializationError
    {
        super( clazz );
    }


    protected InheritableSettings getSettings()
    {
        if ( settings != null )
        {
            return settings;
        }

        if ( suite == null )
        {
            settings = new InheritableSettings( getDescription(), null );
        }

        return settings;
    }


    @Override
    public void run( final RunNotifier notifier )
    {
        super.run( notifier );
        Level cleanupLevel = getSettings().getCleanupLevel();

        if ( cleanupLevel == Level.CLASS )
        {
            try
            {
                shutdown();
                cleanup();
                destroy();
            }
            catch ( Exception e )
            {
                LOG.error( "Encountered exception while trying to cleanup after test class: "
                    + this.getDescription().getDisplayName(), e );
                notifier.fireTestFailure( new Failure( getDescription(), e ) );
            }
        }
    }


    @Override
    protected void runChild( FrameworkMethod method, RunNotifier notifier )
    {
        LOG.debug( "About to invoke test method {}", method.getName() );

        Description description = describeChild( method );
        if ( method.getAnnotation( Ignore.class ) != null )
        {
            notifier.fireTestIgnored( description );
            return;
        }

        Statement statement = methodBlock( method );
        test( getTestClass(), statement, notifier, new InheritableSettings( description, getSettings() ) );

        Level cleanupLevel = getSettings().getCleanupLevel();

        if ( cleanupLevel == Level.METHOD )
        {
            try
            {
                shutdown();
                cleanup();
                destroy();
            }
            catch ( Exception e )
            {
                LOG.error( "Encountered exception while trying to cleanup after test class: "
                    + this.getDescription().getDisplayName(), e );
                notifier.fireTestFailure( new Failure( getDescription(), e ) );
            }
        }
    }


    public void setSuite( CiSuite suite )
    {
        this.suite = suite;
        this.settings = new InheritableSettings( getDescription(), suite.getSettings() );
    }


    public CiSuite getSuite()
    {
        return suite;
    }
}