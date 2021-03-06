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
package org.apache.directory.server.kerberos.shared.messages.components;


import org.apache.directory.server.kerberos.shared.KerberosMessageType;
import org.apache.directory.server.kerberos.shared.messages.Encodable;
import org.apache.directory.server.kerberos.shared.messages.KerberosMessage;
import org.apache.directory.server.kerberos.shared.messages.value.EncryptionKey;
import org.apache.directory.server.kerberos.shared.messages.value.KerberosTime;


/**
 * Encrypted part of the application response.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class EncApRepPart extends KerberosMessage implements Encodable
{
    private KerberosTime clientTime;
    private int cusec;
    private EncryptionKey subSessionKey; //optional
    private Integer sequenceNumber; //optional


    /**
     * Creates a new instance of EncApRepPart.
     *
     * @param clientTime
     * @param cusec
     * @param subSessionKey
     * @param sequenceNumber
     */
    public EncApRepPart( KerberosTime clientTime, int cusec, EncryptionKey subSessionKey, Integer sequenceNumber )
    {
        super( KerberosMessageType.ENC_AP_REP_PART );

        this.clientTime = clientTime;
        this.cusec = cusec;
        this.subSessionKey = subSessionKey;
        this.sequenceNumber = sequenceNumber;
    }


    /**
     * Returns the client {@link KerberosTime}.
     *
     * @return The client {@link KerberosTime}.
     */
    public KerberosTime getClientTime()
    {
        return clientTime;
    }


    /**
     * Returns the client microsecond.
     *
     * @return The client microsecond.
     */
    public int getClientMicroSecond()
    {
        return cusec;
    }


    /**
     * Returns the sequence number.
     *
     * @return The sequence number.
     */
    public Integer getSequenceNumber()
    {
        return sequenceNumber;
    }


    /**
     * Returns the sub-session {@link EncryptionKey}.
     *
     * @return The sub-session {@link EncryptionKey}.
     */
    public EncryptionKey getSubSessionKey()
    {
        return subSessionKey;
    }
}
