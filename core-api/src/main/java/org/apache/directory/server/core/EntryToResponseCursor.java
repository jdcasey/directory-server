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

package org.apache.directory.server.core;


import java.util.Iterator;

import org.apache.directory.ldap.client.api.message.SearchResultEntry;
import org.apache.directory.server.core.entry.ClonedServerEntry;
import org.apache.directory.server.core.filtering.EntryFilteringCursor;
import org.apache.directory.shared.i18n.I18n;
import org.apache.directory.shared.ldap.cursor.ClosureMonitor;
import org.apache.directory.shared.ldap.cursor.Cursor;


/**
 * A cursor to get SearchResponseS after setting the underlying cursor's ServerEntry object in SearchResultEnty object 
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class EntryToResponseCursor<SearchResponse> implements Cursor<SearchResponse>
{

    /** the underlying cursor */
    private EntryFilteringCursor wrapped;


    public EntryToResponseCursor( EntryFilteringCursor wrapped )
    {
        this.wrapped = wrapped;
    }


    public Iterator<SearchResponse> iterator()
    {
        throw new UnsupportedOperationException();
    }


    public void after( SearchResponse resp ) throws Exception
    {
        throw new UnsupportedOperationException();
    }


    public void afterLast() throws Exception
    {
        wrapped.afterLast();
    }


    public boolean available()
    {
        return wrapped.available();
    }


    public void before( SearchResponse resp ) throws Exception
    {
        throw new UnsupportedOperationException();
    }


    public void beforeFirst() throws Exception
    {
        wrapped.beforeFirst();
    }


    public void close() throws Exception
    {
        wrapped.close();
    }


    public void close( Exception e ) throws Exception
    {
        wrapped.close( e );
    }


    public boolean first() throws Exception
    {
        return wrapped.first();
    }


    public SearchResponse get() throws Exception
    {
        ClonedServerEntry entry = wrapped.get();
        SearchResultEntry se = new SearchResultEntry();
        se.setEntry( entry );

        return ( SearchResponse ) se;
    }


    public boolean isClosed() throws Exception
    {
        return wrapped.isClosed();
    }


    public boolean isElementReused()
    {
        return wrapped.isElementReused();
    }


    public boolean last() throws Exception
    {
        return wrapped.last();
    }


    public boolean next() throws Exception
    {
        return wrapped.next();
    }


    public boolean previous() throws Exception
    {
        return wrapped.previous();
    }


    public void setClosureMonitor( ClosureMonitor monitor )
    {
        wrapped.setClosureMonitor( monitor );
    }

    /**
     * {@inheritDoc}
     */
    public boolean isAfterLast() throws Exception
    {
        throw new UnsupportedOperationException( I18n.err( I18n.ERR_02014_UNSUPPORTED_OPERATION, getClass().getName()
            .concat( "." ).concat( "isAfterLast()" ) ) );
    }


    /**
     * {@inheritDoc}
     */
    public boolean isBeforeFirst() throws Exception
    {
        throw new UnsupportedOperationException( I18n.err( I18n.ERR_02014_UNSUPPORTED_OPERATION, getClass().getName()
            .concat( "." ).concat( "isBeforeFirst()" ) ) );
    }


    /**
     * {@inheritDoc}
     */
    public boolean isFirst() throws Exception
    {
        throw new UnsupportedOperationException( I18n.err( I18n.ERR_02014_UNSUPPORTED_OPERATION, getClass().getName()
            .concat( "." ).concat( "isFirst()" ) ) );
    }


    /**
     * {@inheritDoc}
     */
    public boolean isLast() throws Exception
    {
        throw new UnsupportedOperationException( I18n.err( I18n.ERR_02014_UNSUPPORTED_OPERATION, getClass().getName()
            .concat( "." ).concat( "isLast()" ) ) );
    }
}
