/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.directory.server.core.cursor;


import java.util.Iterator;


/**
 * An empty Cursor implementation.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class EmptyCursor<E> extends AbstractCursor<E>
{
    public boolean available()
    {
        return false;
    }

    public void before( E element ) throws CursorClosedException
    {
        checkClosed( "before()" );
    }


    public void after( E element ) throws CursorClosedException
    {
        checkClosed( "after()" );
    }


    public void beforeFirst() throws CursorClosedException
    {
        checkClosed( "beforeFirst()" );
    }


    public void afterLast() throws CursorClosedException
    {
        checkClosed( "afterLast()" );
    }


    public boolean first() throws CursorClosedException
    {
        checkClosed( "first()" );
        return false;
    }


    public boolean last() throws CursorClosedException
    {
        checkClosed( "last()" );
        return false;
    }


    public boolean previous() throws CursorClosedException
    {
        checkClosed( "previous()" );
        return false;
    }


    public boolean next() throws CursorClosedException
    {
        checkClosed( "next()" );
        return false;
    }


    public E get() throws Exception
    {
        checkClosed( "get()" );
        throw new InvalidCursorPositionException( "This cursor is empty and cannot return elements!" );
    }


    public boolean isElementReused()
    {
        return false;
    }
}