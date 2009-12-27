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
package org.apache.directory.server.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * A annotation used to define a LdapServer configuration. Many elements can be configured :
 * <ul>
 * <li> The server ID (or name)</li>
 * <li> The max time limit</li>
 * <li> the max size limit</li>
 * <li> Should it allow anonymous access</li>
 * <li> The keyStore file</li>
 * <li> The certificate password</li>
 * </ul>
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
@Documented
@Inherited
@Retention ( RetentionPolicy.RUNTIME )
@Target ( { ElementType.METHOD, ElementType.TYPE } )
public @interface LdapServer
{
    /** The instance name */
    String name();
    
    /** The maximum size limit.*/
    int maxSizeLimit() default 1000;
    
    /** The maximum time limit. */
    int maxTimeLimit() default 1000;
    
    /** Tells if anonymous access are allowed or not. */
    boolean allowAnonymousAccess() default false;
    
    /** The external keyStore file to use, default to the empty string */
    String keyStore() default "";
    
    /** The certificate password in base64, default to the empty string */
    String certificatePassword() default "";
}