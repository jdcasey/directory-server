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
package org.apache.directory.mitosis.service.protocol.codec;


import java.util.HashSet;
import java.util.Set;

import org.apache.directory.mitosis.service.protocol.message.BaseMessage;
import org.apache.directory.mitosis.service.protocol.message.EndLogEntriesMessage;
import org.apache.mina.common.ByteBuffer;


public class EndLogEntriesMessageEncoder extends BaseMessageEncoder
{
    public EndLogEntriesMessageEncoder()
    {
    }


    protected void encodeBody( BaseMessage in, ByteBuffer out )
    {
    }


    public Set<Class<?>> getMessageTypes()
    {
        Set<Class<?>> set = new HashSet<Class<?>>();
        set.add( EndLogEntriesMessage.class );
        return set;
    }
}