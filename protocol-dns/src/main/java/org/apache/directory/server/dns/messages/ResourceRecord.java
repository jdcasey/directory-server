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

package org.apache.directory.server.dns.messages;


/**
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public interface ResourceRecord
{
    /**
     * @return Returns the domainName.
     */
    public String getDomainName();


    /**
     * @return Returns the recordType.
     */
    public RecordType getRecordType();


    /**
     * @return Returns the recordClass.
     */
    public RecordClass getRecordClass();


    /**
     * @return Returns the timeToLive.
     */
    public int getTimeToLive();


    /**
     * @param id 
     * @return Returns the value for an id.
     */
    public String get( String id );
}
