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
package org.apache.directory.server.schema.registries;


import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.naming.NamingException;

import org.apache.directory.shared.ldap.schema.MatchingRuleUse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A plain old java object implementation of an MatchingRuleUseRegistry.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class DefaultMatchingRuleUseRegistry implements MatchingRuleUseRegistry
{
    /** static class logger */
    private static final Logger LOG = LoggerFactory.getLogger( DefaultMatchingRuleUseRegistry.class );
    /** maps a name to an MatchingRuleUse */
    private final Map<String,MatchingRuleUse> byName;


    // ------------------------------------------------------------------------
    // C O N S T R U C T O R S
    // ------------------------------------------------------------------------

    
    /**
     * Creates an empty DefaultMatchingRuleUseRegistry.
     */
    public DefaultMatchingRuleUseRegistry()
    {
        this.byName = new HashMap<String,MatchingRuleUse>();
    }


    // ------------------------------------------------------------------------
    // Service Methods
    // ------------------------------------------------------------------------

    
    public void register( MatchingRuleUse matchingRuleUse ) throws NamingException
    {
        if ( byName.containsKey( matchingRuleUse.getName() ) )
        {
            throw new NamingException( "matchingRuleUse w/ name " + matchingRuleUse.getName()
                + " has already been registered!" );
        }

        byName.put( matchingRuleUse.getName(), matchingRuleUse );
        if ( LOG.isDebugEnabled() )
        {
            LOG.debug( "registed matchingRuleUse: " + matchingRuleUse );
        }
    }


    public MatchingRuleUse lookup( String name ) throws NamingException
    {
        if ( !byName.containsKey( name ) )
        {
            throw new NamingException( "matchingRuleUse w/ name " + name + " not registered!" );
        }

        MatchingRuleUse matchingRuleUse = byName.get( name );
        if ( LOG.isDebugEnabled() )
        {
            LOG.debug( "lookup with name '"+ name + "' of matchingRuleUse: " + matchingRuleUse );
        }
        return matchingRuleUse;
    }


    public boolean hasMatchingRuleUse( String name )
    {
        return byName.containsKey( name );
    }


    public String getSchemaName( String id ) throws NamingException
    {
        MatchingRuleUse mru = byName.get( id );
        if ( mru != null )
        {
            return mru.getSchema();
        }

        throw new NamingException( "Name " + id + " not found in name to " + "MatchingRuleUse map!" );
    }


    public Iterator<MatchingRuleUse> iterator()
    {
        return byName.values().iterator();
    }
    
    
    public void unregister( String name ) throws NamingException
    {
        byName.remove( name );
    }
}