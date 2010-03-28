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
package org.apache.directory.server.core.factory;


import java.io.File;
import java.util.Set;

import org.apache.directory.server.core.partition.Partition;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmIndex;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmPartition;
import org.apache.directory.server.xdbm.Index;
import org.apache.directory.shared.ldap.entry.ServerEntry;


/**
 * A factory used to generate {@link JdbmPartition}s.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class JdbmPartitionFactory implements PartitionFactory
{

    /**
     * {@inheritDoc}
     */
    public JdbmPartition createPartition( String id, String suffix, int cacheSize, File workingDirectory )
        throws Exception
    {
        JdbmPartition partition = new JdbmPartition();
        partition.setId( id );
        partition.setSuffix( suffix );
        partition.setCacheSize( 500 );
        partition.setPartitionDir( workingDirectory );
        return partition;
    }


    /**
     * {@inheritDoc}
     */
    public void addIndex( Partition partition, String attributeId, int cacheSize ) throws Exception
    {
        if ( !( partition instanceof JdbmPartition ) )
        {
            throw new IllegalArgumentException( "Partition must be a JdbmPartition" );
        }

        JdbmPartition jdbmPartition = ( JdbmPartition ) partition;
        Set<Index<? extends Object, ServerEntry, Long>> indexedAttributes = jdbmPartition.getIndexedAttributes();

        JdbmIndex<Object, ServerEntry> index = new JdbmIndex<Object, ServerEntry>( attributeId );
        index.setCacheSize( cacheSize );

        indexedAttributes.add( index );
        jdbmPartition.setIndexedAttributes( indexedAttributes );
    }

}