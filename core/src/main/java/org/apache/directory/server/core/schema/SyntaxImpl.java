/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */
package org.apache.directory.server.core.schema;


import javax.naming.NamingException;

import org.apache.directory.server.schema.registries.SyntaxCheckerRegistry;
import org.apache.directory.shared.ldap.schema.AbstractSyntax;
import org.apache.directory.shared.ldap.schema.MutableSchemaObject;
import org.apache.directory.shared.ldap.schema.SyntaxChecker;


class SyntaxImpl extends AbstractSyntax implements MutableSchemaObject
{
    private static final long serialVersionUID = 1L;
    private final SyntaxCheckerRegistry registry;


    protected SyntaxImpl( String oid, SyntaxCheckerRegistry registry )
    {
        super( oid );
        this.registry = registry;
    }

    
    public SyntaxChecker getSyntaxChecker() throws NamingException
    {
        return registry.lookup( oid );
    }
    
    
    public void setDescription( String description )
    {
        super.setDescription( description );
    }
    
    
    public void setHumanReadable( boolean humanReadable )
    {
        super.setHumanReadable( humanReadable );
    }
    
    
    public void setSchema( String schema )
    {
        super.setSchema( schema );
    }
    
    
    public void setObsolete( boolean obsolete )
    {
        super.setObsolete( obsolete );
    }
    
    
    public void setNames( String[] names )
    {
        super.setNames( names );
    }
}