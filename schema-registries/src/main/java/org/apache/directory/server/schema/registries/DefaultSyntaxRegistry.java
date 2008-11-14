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

import org.apache.directory.shared.ldap.schema.Syntax;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A SyntaxRegistry service available during server startup when other resources
 * like a syntax backing store is unavailable.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class DefaultSyntaxRegistry implements SyntaxRegistry
{
    /** static class logger */
    private static final Logger LOG = LoggerFactory.getLogger( DefaultSyntaxRegistry.class );
    
    /** Speedup for DEBUG mode */
    private static final boolean IS_DEBUG = LOG.isDebugEnabled();
    
    /** a map of entries using an OID for the key and a Syntax for the value */
    private final Map<String,Syntax> byOid;
    /** the OID oidRegistry this oidRegistry uses to register new syntax OIDs */
    private final OidRegistry oidRegistry;


    // ------------------------------------------------------------------------
    // C O N S T R U C T O R S
    // ------------------------------------------------------------------------

    /**
     * Creates a DefaultSyntaxRegistry.
     *
     * @param registry used by this registry for OID to name resolution of
     * dependencies and to automatically register and unregister it's aliases and OIDs
     */
    public DefaultSyntaxRegistry( OidRegistry registry )
    {
        this.oidRegistry = registry;
        this.byOid = new HashMap<String,Syntax>();
    }


    // ------------------------------------------------------------------------
    // SyntaxRegistry interface methods
    // ------------------------------------------------------------------------

    
    public Syntax lookup( String id ) throws NamingException
    {
        id = oidRegistry.getOid( id );

        if ( byOid.containsKey( id ) )
        {
            Syntax syntax = byOid.get( id );
            
            if ( IS_DEBUG )
            {
                LOG.debug( "looked up using id '" + id + "': " + syntax );
            }
            
            return syntax;
        }

        throw new NamingException( "Unknown syntax OID " + id );
    }


    public void register( Syntax syntax ) throws NamingException
    {
        if ( byOid.containsKey( syntax.getOid() ) )
        {
            throw new NamingException( "syntax w/ OID " + syntax.getOid()
                + " has already been registered!" );
        }

        if ( syntax.getName() != null )
        {
            oidRegistry.register( syntax.getName(), syntax.getOid() );
        }
        else
        {
            oidRegistry.register( syntax.getOid(), syntax.getOid() );
        }

        byOid.put( syntax.getOid(), syntax );
        
        if ( IS_DEBUG )
        {
            LOG.debug( "registered syntax: " + syntax );
        }
    }


    public boolean hasSyntax( String id )
    {
        if ( oidRegistry.hasOid( id ) )
        {
            try
            {
                return byOid.containsKey( oidRegistry.getOid( id ) );
            }
            catch ( NamingException e )
            {
                return false;
            }
        }

        return false;
    }


    public String getSchemaName( String id ) throws NamingException
    {
        if ( ! Character.isDigit( id.charAt( 0 ) ) )
        {
            throw new NamingException( "Looks like the arg is not a numeric OID" );
        }

        id = oidRegistry.getOid( id );
        Syntax syntax = byOid.get( id );
        if ( syntax != null )
        {
            return syntax.getSchema();
        }

        throw new NamingException( "OID " + id + " not found in oid to " + "Syntax map!" );
    }


    public Iterator<Syntax> iterator()
    {
        return byOid.values().iterator();
    }


    public void unregister( String numericOid ) throws NamingException
    {
        if ( ! Character.isDigit( numericOid.charAt( 0 ) ) )
        {
            throw new NamingException( "Looks like the arg is not a numeric OID" );
        }

        byOid.remove( numericOid );
    }
}