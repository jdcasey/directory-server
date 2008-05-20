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
package org.apache.directory.mitosis.common;


import org.apache.directory.mitosis.util.OctetString;


/**
 * A default {@link UUID} implementation.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class DefaultUUID implements UUID
{
    private static final long serialVersionUID = 3256721788405953846L;

    private final String uuid;


    public DefaultUUID( byte[] uuid )
    {
        if ( uuid.length != 16 )
        {
            throw new IllegalArgumentException( "UUID must be 128-bits long." );
        }

        this.uuid = OctetString.toString( uuid );
    }


    public DefaultUUID( String uuid )
    {
        String newUUID = uuid.replaceAll( "[^0-9A-Za-z]", "" );

        if ( newUUID.length() != 32 )
        {
            throw new IllegalArgumentException( "UUID: " + uuid );
        }

        this.uuid = newUUID;
    }


    /**
     *Compute the instance's hash code 
     * @return the instance's hashcode 
     */
    public int hashCode()
    {
        return uuid.hashCode();
    }


    public boolean equals( Object o )
    {
        if ( o == null )
        {
            return false;
        }

        if ( this == o )
        {
            return true;
        }

        if ( !( o instanceof UUID ) )
        {
            return false;
        }

        return uuid.equals( ( ( UUID ) o ).toOctetString() );
    }


    public String toOctetString()
    {
        return uuid;
    }


    public int compareTo( Object o )
    {
        return uuid.compareTo( ( ( UUID ) o ).toOctetString() );
    }
}