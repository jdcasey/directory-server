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
package org.apache.directory.server.schema.bootstrap;


import java.util.Comparator;

import javax.naming.NamingException;

import org.apache.directory.server.schema.bootstrap.AbstractBootstrapProducer;
import org.apache.directory.server.schema.bootstrap.ProducerCallback;
import org.apache.directory.server.schema.bootstrap.ProducerTypeEnum;
import org.apache.directory.server.schema.registries.Registries;
import org.apache.directory.shared.ldap.schema.CachingNormalizer;
import org.apache.directory.shared.ldap.schema.ComparableComparator;
import org.apache.directory.shared.ldap.schema.DeepTrimNormalizer;
import org.apache.directory.shared.ldap.schema.NormalizingComparator;


/**
 * A producer of Comparator objects for the nis schema.
 * Modified by hand from generated code
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class NisComparatorProducer extends AbstractBootstrapProducer
{
    public NisComparatorProducer()
    {
        super( ProducerTypeEnum.COMPARATOR_PRODUCER );
    }

    
    public static class DeepTrimCachingNormalizingComparator extends NormalizingComparator
    {
        public DeepTrimCachingNormalizingComparator()        
        {
            super( new CachingNormalizer( new DeepTrimNormalizer() ), new ComparableComparator() );
        }
    }

    
    // ------------------------------------------------------------------------
    // BootstrapProducer Methods
    // ------------------------------------------------------------------------

    /**
     * @see org.apache.directory.server.schema.bootstrap.BootstrapProducer#produce(Registries, ProducerCallback)
     */
    public void produce( Registries registries, ProducerCallback cb ) throws NamingException
    {
        Comparator comparator;

        /* Really an openLDAP matching rule but its used in he nis so its here
         *
         ( 1.3.6.1.4.1.4203.1.2.1 NAME 'caseExactIA5SubstringsMatch'
         SYNTAX 1.3.6.1.4.1.1466.115.121.1.26 )
         */
        comparator = new DeepTrimCachingNormalizingComparator();
        cb.schemaObjectProduced( this, "1.3.6.1.4.1.4203.1.2.1", comparator );
    }
}