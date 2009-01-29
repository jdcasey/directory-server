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


import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


/**
 * 
 * Test for the CSN class
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class CSNTest
{

    @Test
    public void testCSN()
    {
        long ts = System.currentTimeMillis();

        CSN csn = new CSN( Long.toString( ts, 16 ) + ":abcdefghi0123:" + 1 );

        assertEquals( ts, csn.getTimestamp() );
        assertEquals( 1, csn.getOperationSequence() );
        assertEquals( "abcdefghi0123", csn.getReplicaId().toString() );
    }


    @Test
    public void testCSNEmpty()
    {
        try
        {
            new CSN( "" );
            fail();
        }
        catch ( AssertionError ae )
        {
            assertTrue( true );
        }
        catch ( InvalidCSNException ice )
        {
            assertTrue( true );
        }
    }


    @Test
    public void testCSNTSOnly()
    {
        try
        {
            new CSN( "123" );
            fail();
        }
        catch ( AssertionError ae )
        {
            assertTrue( true );
        }
        catch ( InvalidCSNException ice )
        {
            assertTrue( true );
        }
    }


    @Test
    public void testCSNInvalidTS()
    {
        try
        {
            new CSN( "zzz:abc:1" );
            fail();
        }
        catch ( AssertionError ae )
        {
            assertTrue( true );
        }
        catch ( InvalidCSNException ice )
        {
            assertTrue( true );
        }
    }


    @Test
    public void testCSNNoTS()
    {
        try
        {
            new CSN( ":abc:1" );
            fail();
        }
        catch ( AssertionError ae )
        {
            assertTrue( true );
        }
        catch ( InvalidCSNException ice )
        {
            assertTrue( true );
        }
    }


    @Test
    public void testCSNInavlidReplica()
    {
        try
        {
            new CSN( "123:*:1" );
            fail();
        }
        catch ( AssertionError ae )
        {
            assertTrue( true );
        }
        catch ( InvalidCSNException ice )
        {
            assertTrue( true );
        }
    }


    @Test
    public void testCSNNoReplica()
    {
        try
        {
            new CSN( "123::1" );
            fail();
        }
        catch ( AssertionError ae )
        {
            assertTrue( true );
        }
        catch ( InvalidCSNException ice )
        {
            assertTrue( true );
        }
    }


    @Test
    public void testCSNInavlidOpSeq()
    {
        try
        {
            new CSN( "123:abc:zzz" );
            fail();
        }
        catch ( AssertionError ae )
        {
            assertTrue( true );
        }
        catch ( InvalidCSNException ice )
        {
            assertTrue( true );
        }
    }


    @Test
    public void testCSNEmptyOpSeq()
    {
        try
        {
            new CSN( "123:abc:" );
            fail();
        }
        catch ( AssertionError ae )
        {
            assertTrue( true );
        }
        catch ( InvalidCSNException ice )
        {
            assertTrue( true );
        }
    }


    @Test
    public void testCSNNoOpSeq()
    {
        try
        {
            new CSN( "123:abc" );
            fail();
        }
        catch ( AssertionError ae )
        {
            assertTrue( true );
        }
        catch ( InvalidCSNException ice )
        {
            assertTrue( true );
        }
    }


    @Test
    public void testCSNToBytes()
    {
        CSN csn = new CSN( "0123456789abcdef:test:5678cdef" );

        byte[] bytes = csn.toBytes();

        assertEquals( 0x01, bytes[0] );
        assertEquals( 0x23, bytes[1] );
        assertEquals( 0x45, bytes[2] );
        assertEquals( 0x67, bytes[3] );
        assertEquals( ( byte ) 0x89, bytes[4] );
        assertEquals( ( byte ) 0xAB, bytes[5] );
        assertEquals( ( byte ) 0xCD, bytes[6] );
        assertEquals( ( byte ) 0xEF, bytes[7] );
        assertEquals( 0x56, bytes[8] );
        assertEquals( 0x78, bytes[9] );
        assertEquals( ( byte ) 0xCD, bytes[10] );
        assertEquals( ( byte ) 0xEF, bytes[11] );

        assertEquals( "test", new String( bytes, 12, bytes.length - 12 ) );

        CSN deserializedCSN = new CSN( bytes );
        assertEquals( csn, deserializedCSN );
    }
}