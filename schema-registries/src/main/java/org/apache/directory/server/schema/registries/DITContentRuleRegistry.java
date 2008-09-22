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


import java.util.Iterator;

import javax.naming.NamingException;

import org.apache.directory.shared.ldap.schema.DITContentRule;


/**
 * An DITContentRule registry's service interface.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public interface DITContentRuleRegistry extends SchemaObjectRegistry
{
    /**
     * Registers a DITContentRule with this registry.
     * 
     * @param dITContentRule the DITContentRule to register
     * @throws NamingException if the DITContentRule is already registered or
     * the registration operation is not supported
     */
    void register( DITContentRule dITContentRule ) throws NamingException;


    /**
     * Looks up a DITContentRule by its object identifier or by its name.
     * 
     * @param id the object identifier or name of the DITContentRule
     * @return the DITContentRule instance for the id
     * @throws NamingException if the DITContentRule does not exist
     */
    DITContentRule lookup( String id ) throws NamingException;


    /**
     * Checks to see if a DITContentRule exists.
     * 
     * @param id the object identifier or name of the DITContentRule
     * @return true if a DITContentRule definition exists for the id, false
     * otherwise
     */
    boolean hasDITContentRule( String id );
    
    /**
     * Lists the DITContentRules registered in this registry.
     *
     * @return an Iterator of DITContentRules
     */
    Iterator<DITContentRule> iterator();
}