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
package org.apache.directory.shared.ldap.schema.syntaxes;


import javax.naming.NamingException;


/**
 * A SyntaxChecker implementation which accepts all values as valid.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class AcceptAllSyntaxChecker implements SyntaxChecker
{
    /** the OID of the Syntax this checker is associated with */
    private String oid;


    /**
     * Creates a SyntaxChecker which accepts all values.
     * 
     * @param oid the oid of the Syntax this checker is associated with
     */
    public AcceptAllSyntaxChecker( String oid )
    {
        this.oid = oid;
    }

    
    public AcceptAllSyntaxChecker()
    {
    }

    
    public void setSyntaxOid( String oid )
    {
        this.oid = oid;
    }
    
    
    /**
     * @see SyntaxChecker#getSyntaxOid()
     * 
     * @return the OID syntax
     */
    public String getSyntaxOid()
    {
        return oid;
    }


    /**
     * Returns true every time.
     * 
     * @see SyntaxChecker#isValidSyntax(Object)
     * 
     * @param value the value of some attribute with the syntax
     * @return true if the value is in the valid syntax, false otherwise
     */
    public boolean isValidSyntax( Object value )
    {
        return true;
    }


    /**
     * Does nothing but return immediately and no exceptions are ever thrown.
     * 
     * @see SyntaxChecker#assertSyntax(Object)
     * 
     * @param value the value of some attribute with the syntax
     * @throws NamingException if the value does not conform to the attribute syntax.
     */
    public void assertSyntax( Object value ) throws NamingException
    {
    }
}