/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *  
 *    http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License. 
 *  
 */
package org.apache.directory.server.core.invocation;


import java.util.Collections;
import java.util.HashSet;
import java.util.Collection;
import java.util.Set;

import javax.naming.Context;

import org.apache.directory.server.core.partition.PartitionNexus;
import org.apache.directory.server.core.partition.PartitionNexusProxy;


/**
 * Represents a call from JNDI {@link Context} to {@link PartitionNexus}.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class Invocation
{
    private final Context caller;
    private final String name;
    private final Collection<String> bypassed;
    private final PartitionNexusProxy proxy;
    
    private static final Set<String> EMPTY_STRING_SET = new HashSet<String>();


    /**
     * Creates a new instance that represents an invocation without parameters.
     * 
     * @param caller the JNDI {@link Context} that made this invocation
     * @param name the name of the called method
     */
    public Invocation( PartitionNexusProxy proxy, Context caller, String name )
    {
        this( proxy, caller, name, EMPTY_STRING_SET );
    }


    /**
     * Creates a new instance.
     * 
     * @param caller the JNDI {@link Context} that made this invocation
     * @param name the name of the called method
     * @param parameters the array of parameters passed to the called method
     * @param bypassed the set of bypassed Interceptor names
     */
    public Invocation( PartitionNexusProxy proxy, Context caller, String name, Collection<String> bypassed )
    {
        if ( proxy == null )
        {
            throw new NullPointerException( "proxy" );
        }
        
        if ( caller == null )
        {
            throw new NullPointerException( "caller" );
        }
        
        if ( name == null )
        {
            throw new NullPointerException( "name" );
        }

        if ( bypassed == null )
        {
            this.bypassed = EMPTY_STRING_SET;
        }
        else
        {
            this.bypassed = Collections.unmodifiableCollection( bypassed );
        }

        this.proxy = proxy;
        this.caller = caller;
        this.name = name;
    }


    /**
     * Returns the proxy object to the {@link PartitionNexus}.
     */
    public PartitionNexusProxy getProxy()
    {
        return proxy;
    }


    /**
     * Returns the JNDI {@link Context} which made this invocation.
     */
    public Context getCaller()
    {
        return caller;
    }


    /**
     * Returns the name of the called method.
     */
    public String getName()
    {
        return name;
    }


    /**
     * Checks to see if an interceptor is bypassed.
     *
     * @param interceptorName the interceptorName of the interceptor to check for bypass
     * @return true if the interceptor should be bypassed, false otherwise
     */
    public boolean isBypassed( String interceptorName )
    {
        return bypassed.contains( interceptorName );
    }


    /**
     * Checks to see if any interceptors are bypassed by this Invocation.
     *
     * @return true if at least one bypass exists
     */
    public boolean hasBypass()
    {
        return !bypassed.isEmpty();
    }
}