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

package org.apache.directory.server.xdbm.impl.avl;


import org.apache.directory.server.core.partition.impl.btree.LongComparator;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.server.xdbm.ParentIdAndRdn;
import org.apache.directory.server.xdbm.ParentIdAndRdnComparator;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.MatchingRule;
import org.apache.directory.shared.ldap.schema.SchemaManager;


/**
 * A special index which stores RDN objects.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class AvlRdnIndex<E> extends AvlIndex<ParentIdAndRdn<Long>, E>
{

    public AvlRdnIndex()
    {
    }


    public AvlRdnIndex( String attributeId )
    {
        super( attributeId );
    }


    public void init( SchemaManager schemaManager, AttributeType attributeType ) throws Exception
    {
        this.attributeType = attributeType;

        MatchingRule mr = attributeType.getEquality();

        if ( mr == null )
        {
            mr = attributeType.getOrdering();
        }

        if ( mr == null )
        {
            mr = attributeType.getSubstring();
        }

        normalizer = mr.getNormalizer();

        if ( normalizer == null )
        {
            throw new Exception( I18n.err( I18n.ERR_212, attributeType ) );
        }

        ParentIdAndRdnComparator<Long> comp = new ParentIdAndRdnComparator<Long>( mr.getOid() );

        LongComparator.INSTANCE.setSchemaManager( schemaManager );

        /*
         * The forward key/value map stores attribute values to master table 
         * primary keys.  A value for an attribute can occur several times in
         * different entries so the forward map can have more than one value.
         */
        forward = new AvlTable<ParentIdAndRdn<Long>, Long>( attributeType.getName(), comp, LongComparator.INSTANCE,
            false );
        reverse = new AvlTable<Long, ParentIdAndRdn<Long>>( attributeType.getName(), LongComparator.INSTANCE, comp,
            false );
    }


    public void add( ParentIdAndRdn<Long> rdn, Long entryId ) throws Exception
    {
        forward.put( rdn, entryId );
        reverse.put( entryId, rdn );
    }


    public void drop( Long entryId ) throws Exception
    {
        ParentIdAndRdn<Long> rdn = reverse.get( entryId );
        forward.remove( rdn );
        reverse.remove( entryId );
    }


    public void drop( ParentIdAndRdn<Long> rdn, Long id ) throws Exception
    {
        long val = forward.get( rdn );
        if ( val == id )
        {
            forward.remove( rdn );
            reverse.remove( val );
        }
    }


    public ParentIdAndRdn<Long> getNormalized( ParentIdAndRdn<Long> rdn ) throws Exception
    {
        return rdn;
    }
}
