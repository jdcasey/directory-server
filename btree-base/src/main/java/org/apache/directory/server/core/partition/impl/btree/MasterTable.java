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
package org.apache.directory.server.core.partition.impl.btree;


import javax.naming.NamingException;

import org.apache.directory.server.core.entry.ServerEntry;


/**
 * The master table used to store the ServerEntry of entries.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public interface MasterTable extends Table
{
    /** the name of the dbf file for this table */
    String DBF = "master";

    /** the sequence key - stores last sequence value in the admin table */
    String SEQPROP_KEY = "__sequence__";


    /**
     * Gets the ServerEntry from this MasterTable.
     *
     * @param id the Long id of the entry to retrieve.
     * @return the ServerEntry with operational attributes and all.
     * @throws NamingException if there is a read error on the underlying Db.
     */
    ServerEntry get( Object id ) throws NamingException;


    /**
     * Puts the ServerEntry into this master table at an index 
     * specified by id.  Used both to create new entries and update existing 
     * ones.
     *
     * @param entry the ServerEntry w/ operational attributes
     * @param id the Long id of the entry to put
     * @return the newly created ServerEntry
     * @throws NamingException if there is a write error on the underlying Db.
     */
    ServerEntry put( ServerEntry entry, Object id ) throws NamingException;


    /**
     * Deletes a ServerEntry from the master table at an index specified by id.
     *
     * @param id the Long id of the entry to delete
     * @return the deleted ServerEntry
     * @throws NamingException if there is a write error on the underlying Db
     */
    ServerEntry delete( Object id ) throws NamingException;


    /**
     * Get's the current id value from this master database's sequence without
     * affecting the seq.
     *
     * @return the current value.
     * @throws NamingException if the admin table storing sequences cannot be
     * read.
     */
    Object getCurrentId() throws NamingException;


    /**
     * Get's the next value from this SequenceBDb.  This has the side-effect of
     * changing the current sequence values permanently in memory and on disk.
     *
     * @return the current value incremented by one.
     * @throws NamingException if the admin table storing sequences cannot be
     * read and writen to.
     */
    Object getNextId() throws NamingException;


    /**
     * Gets a persistent property stored in the admin table of this MasterTable.
     *
     * @param property the key of the property to get the value of
     * @return the value of the property
     * @throws NamingException when the underlying admin table cannot be read
     */
    String getProperty( String property ) throws NamingException;


    /**
     * Sets a persistent property stored in the admin table of this MasterTable.
     *
     * @param property the key of the property to set the value of
     * @param value the value of the property
     * @throws NamingException when the underlying admin table cannot be writen
     */
    void setProperty( String property, String value ) throws NamingException;
}