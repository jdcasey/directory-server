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


package org.apache.directory.server.schema.bootstrap.partition;

import junit.framework.TestCase;

/**
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$ $Date$
 */
public class UniqueResourceTest extends TestCase
{

    public void testUniqueResource() throws Exception
    {
        //look for META-INF/LICENSE.txt which should be in at least two jars
        try
        {
            DbFileListing.getUniqueResource( "META-INF/LICENSE", "foo" );
            fail( "There are at least 2 license files on the classpath, this should have failed" );
        } catch ( UniqueResourceException e )
        {
            assertNotNull("There should be at least 2 LICENSE files on the classpath", e.getUrls());
        }

    }
}