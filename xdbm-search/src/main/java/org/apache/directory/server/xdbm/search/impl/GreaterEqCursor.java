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
package org.apache.directory.server.xdbm.search.impl;


import org.apache.directory.server.xdbm.*;
import org.apache.directory.server.core.cursor.InvalidCursorPositionException;
import org.apache.directory.server.core.entry.ServerEntry;


/**
 * A Cursor over entry candidates matching a GreaterEq assertion filter.  This
 * Cursor operates in two modes.  The first is when an index exists for the
 * attribute the assertion is built on.  The second is when the user index for
 * the assertion attribute does not exist.  Different Cursors are used in each
 * of these cases where the other remains null.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class GreaterEqCursor<V> extends AbstractIndexCursor<V, ServerEntry>
{
    private static final String UNSUPPORTED_MSG =
        "GreaterEqCursors only support positioning by element when a user index exists on the asserted attribute.";

    /** An greater eq evaluator for candidates */
    private final GreaterEqEvaluator greaterEqEvaluator;

    /** Cursor over attribute entry matching filter: set when index present */
    private final IndexCursor<V,ServerEntry> userIdxCursor;

    /** NDN Cursor on all entries in  (set when no index on user attribute) */
    private final IndexCursor<String,ServerEntry> ndnIdxCursor;

    /**
     * Used to store indexEntry from ndnCandidate so it can be saved after
     * call to evaluate() which changes the value so it's not referring to
     * the NDN but to the value of the attribute instead.
     */
    IndexEntry<String, ServerEntry> ndnCandidate;

    /** used in both modes */
    private boolean available = false;


    @SuppressWarnings("unchecked")
    public GreaterEqCursor( Store<ServerEntry> db, GreaterEqEvaluator greaterEqEvaluator ) throws Exception
    {
        this.greaterEqEvaluator = greaterEqEvaluator;

        String attribute = greaterEqEvaluator.getExpression().getAttribute();
        if ( db.hasUserIndexOn( attribute ) )
        {
            userIdxCursor = ( ( Index<V,ServerEntry> ) db.getUserIndex( attribute ) ).forwardCursor();
            ndnIdxCursor = null;
        }
        else
        {
            ndnIdxCursor = db.getNdnIndex().forwardCursor();
            userIdxCursor = null;
        }
    }


    public boolean available()
    {
        return available;
    }


    @SuppressWarnings("unchecked")
    public void beforeValue( Long id, V value ) throws Exception
    {
        checkNotClosed( "beforeValue()" );
        if ( userIdxCursor != null )
        {
            /*
             * First we need to check and make sure this element is within
             * bounds as mandated by the assertion node.  To do so we compare
             * it's value with the value of the node.  If it is smaller or
             * equal to this lower bound then we simply position the
             * userIdxCursor before the first element.  Otherwise we let the
             * underlying userIdx Cursor position the element.
             */
            if ( greaterEqEvaluator.getComparator().compare( value,
                 greaterEqEvaluator.getExpression().getValue().get() ) <= 0 )
            {
                beforeFirst();
                return;
            }

            userIdxCursor.beforeValue( id, value );
            available = false;
        }
        else
        {
            throw new UnsupportedOperationException( UNSUPPORTED_MSG );
        }
    }


    @SuppressWarnings("unchecked")
    public void afterValue( Long id, V value ) throws Exception
    {
        checkNotClosed( "afterValue()" );
        if ( userIdxCursor != null )
        {
            int comparedValue = greaterEqEvaluator.getComparator().compare( value,
                 greaterEqEvaluator.getExpression().getValue().get() );

            /*
             * First we need to check and make sure this element is within
             * bounds as mandated by the assertion node.  To do so we compare
             * it's value with the value of the node.  If it is equal to this
             * lower bound then we simply position the userIdxCursor after
             * this first node.  If it is less than this value then we
             * position the Cursor before the first entry.
             */
            if ( comparedValue == 0 )
            {
                userIdxCursor.afterValue( id, value );
                available = false;
                return;
            }
            else if ( comparedValue < 0 )
            {
                beforeFirst();
                return;
            }

            // Element is in the valid range as specified by assertion
            userIdxCursor.afterValue( id, value );
            available = false;
        }
        else
        {
            throw new UnsupportedOperationException( UNSUPPORTED_MSG );
        }
    }


    @SuppressWarnings("unchecked")
    public void before( IndexEntry<V, ServerEntry> element ) throws Exception
    {
        checkNotClosed( "before()" );
        if ( userIdxCursor != null )
        {
            /*
             * First we need to check and make sure this element is within
             * bounds as mandated by the assertion node.  To do so we compare
             * it's value with the value of the node.  If it is smaller or
             * equal to this lower bound then we simply position the
             * userIdxCursor before the first element.  Otherwise we let the
             * underlying userIdx Cursor position the element.
             */
            if ( greaterEqEvaluator.getComparator().compare( element.getValue(),
                 greaterEqEvaluator.getExpression().getValue().get() ) <= 0 )
            {
                beforeFirst();
                return;
            }

            userIdxCursor.before( element );
            available = false;
        }
        else
        {
            throw new UnsupportedOperationException( UNSUPPORTED_MSG );
        }
    }


    @SuppressWarnings("unchecked")
    public void after( IndexEntry<V, ServerEntry> element ) throws Exception
    {
        checkNotClosed( "after()" );
        if ( userIdxCursor != null )
        {
            int comparedValue = greaterEqEvaluator.getComparator().compare( element.getValue(),
                 greaterEqEvaluator.getExpression().getValue().get() );

            /*
             * First we need to check and make sure this element is within
             * bounds as mandated by the assertion node.  To do so we compare
             * it's value with the value of the node.  If it is equal to this
             * lower bound then we simply position the userIdxCursor after
             * this first node.  If it is less than this value then we
             * position the Cursor before the first entry.
             */
            if ( comparedValue == 0 )
            {
                userIdxCursor.after( element );
                available = false;
                return;
            }
            else if ( comparedValue < 0 )
            {
                beforeFirst();
                return;
            }

            // Element is in the valid range as specified by assertion
            userIdxCursor.after( element );
            available = false;
        }
        else
        {
            throw new UnsupportedOperationException( UNSUPPORTED_MSG );
        }
    }


    @SuppressWarnings("unchecked")
    public void beforeFirst() throws Exception
    {
        checkNotClosed( "beforeFirst()" );
        if ( userIdxCursor != null )
        {
            IndexEntry<V,ServerEntry> advanceTo = new ForwardIndexEntry<V,ServerEntry>();
            advanceTo.setValue( ( V ) greaterEqEvaluator.getExpression().getValue().get() );
            userIdxCursor.before( advanceTo );
        }
        else
        {
            ndnIdxCursor.beforeFirst();
            ndnCandidate = null;
        }

        available = false;
    }


    public void afterLast() throws Exception
    {
        checkNotClosed( "afterLast()" );
        if ( userIdxCursor != null )
        {
            userIdxCursor.afterLast();
        }
        else
        {
            ndnIdxCursor.afterLast();
            ndnCandidate = null;
        }

        available = false;
    }


    public boolean first() throws Exception
    {
        beforeFirst();
        return next();
    }


    public boolean last() throws Exception
    {
        afterLast();
        return previous();
    }


    @SuppressWarnings("unchecked")
    public boolean previous() throws Exception
    {
        checkNotClosed( "previous()" );
        if ( userIdxCursor != null )
        {
            /*
             * We have to check and make sure the previous value complies by
             * being greater than or eq to the expression node's value
             */
            while ( userIdxCursor.previous() )
            {
                checkNotClosed( "previous()" );
                IndexEntry<?,ServerEntry> candidate = userIdxCursor.get();
                if ( greaterEqEvaluator.getComparator().compare( candidate.getValue(), greaterEqEvaluator.getExpression().getValue().get() ) >= 0 )
                {
                    return available = true;
                }
            }

            return available = false;
        }

        while( ndnIdxCursor.previous() )
        {
            checkNotClosed( "previous()" );
            ndnCandidate = ndnIdxCursor.get();
            if ( greaterEqEvaluator.evaluate( ndnCandidate ) )
            {
                 return available = true;
            }
        }

        return available = false;
    }


    public boolean next() throws Exception
    {
        checkNotClosed( "next()" );
        if ( userIdxCursor != null )
        {
            /*
             * No need to do the same check that is done in previous() since
             * values are increasing with calls to next().
             */
            return available = userIdxCursor.next();
        }

        while( ndnIdxCursor.next() )
        {
            checkNotClosed( "next()" );
            ndnCandidate = ndnIdxCursor.get();
            if ( greaterEqEvaluator.evaluate( ndnCandidate ) )
            {
                 return available = true;
            }
        }

        return available = false;
    }


    @SuppressWarnings("unchecked")
    public IndexEntry<V, ServerEntry> get() throws Exception
    {
        checkNotClosed( "get()" );
        if ( userIdxCursor != null )
        {
            if ( available )
            {
                return userIdxCursor.get();
            }

            throw new InvalidCursorPositionException( "Cursor has not been positioned yet." );
        }

        if ( available )
        {
            return ( IndexEntry<V, ServerEntry> ) ndnCandidate;
        }

        throw new InvalidCursorPositionException( "Cursor has not been positioned yet." );
    }


    public boolean isElementReused()
    {
        if ( userIdxCursor != null )
        {
            return userIdxCursor.isElementReused();
        }

        return ndnIdxCursor.isElementReused();
    }


    public void close() throws Exception
    {
        super.close();

        if ( userIdxCursor != null )
        {
            userIdxCursor.close();
        }
        else
        {
            ndnIdxCursor.close();
        }
    }
}