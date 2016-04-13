/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Baratine is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Baratine is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Baratine; if not, write to the
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.amp.misc;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

import com.caucho.v5.config.ConfigException;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.util.L10N;

/**
 * mail service
 */
public class MailService
{
  private static final L10N L = new L10N(MailService.class);

  private static final Logger log
    = Logger.getLogger(MailService.class.getName());

  private ArrayList<Address> _toList = new ArrayList<Address>();
  private Address []_to;
  
  private ArrayList<Address> _fromList = new ArrayList<Address>();
  private Address []_from;

  private String _subject = "Resin MailService messages";

  private Properties _properties = new Properties();

  private Session _session;
  private Transport _smtp;

  /**
   * Sets the mail session
   */
  public void setSession(Session session)
  {
    _session = session;
  }

  /**
   * Gets the mail session
   */
  public Session getSession()
  {
    return _session;
  }
  
  public void addTo(String address)
  {
    try {
      addTo(new InternetAddress(address));
    } catch (AddressException e) {
      throw ConfigException.wrap(e);
    }
  }

  /**
   * Adds a 'to' value
   */
  public void addTo(Address address)
  {
    _toList.add(address);
  }
  
  public Address[] getToAddresses()
  {
    return _to;
  }
  
  public void addFrom(String address)
  {
    try {
      addFrom(new InternetAddress(address));
    } catch (AddressException e) {
      throw ConfigException.wrap(e);
    }
  }

  /**
   * Adds a 'from' value
   */
  public void addFrom(Address address)
  {
    _fromList.add(address);
  }
  
  public Address[] getFromAddresses()
  {
    return _from;
  }

  /**
   * Sets a property
   */
  public void setProperty(String key, String value)
  {
    _properties.put(key, value);
  }

  /**
   * Sets properties
   */
  public void setProperties(Properties props)
  {
    _properties.putAll(props);
  }

  /**
   * Sets the subject
   */
  public void setSubject(String subject)
  {
    _subject = subject;
  }

  /**
   * Sends to a mailbox
   */
  public void send(String body)
  {
    send(_subject, body);
  }

  /**
   * Sends to a mailbox
   */
  public void send(String subject, String body)
  {
    try {
      MimeMessage msg = new MimeMessage(getSession());

      if(_from.length > 0)
        msg.addFrom(_from);
      msg.addRecipients(RecipientType.TO, _to);
      if(subject != null)
        msg.setSubject(subject);
      msg.setContent(body, "text/plain");

      send(msg);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Sends to a mailbox
   */
  public void sendWithAttachment(String subject, 
                                 String textBody,
                                 String attachmentType,
                                 String attachmentName,
                                 InputStream is)
  {
    try {
      MimeMessage msg = new MimeMessage(getSession());

      if(_from.length > 0)
        msg.addFrom(_from);
      msg.addRecipients(RecipientType.TO, _to);
      if(subject != null)
        msg.setSubject(subject);
      
      // msg.setContent(textBody, "multipart/mime");
      
      MimeMultipart multipart = new MimeMultipart();
      
      MimeBodyPart textBodyPart = new MimeBodyPart();
      textBodyPart.setText(textBody);
      multipart.addBodyPart(textBodyPart);
      
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      TempBuffer tb = TempBuffer.create();
      byte []buffer = tb.buffer();
      int len;
      
      while ((len = is.read(buffer, 0, buffer.length)) >= 0) {
        bos.write(buffer, 0, len);
      }
      bos.close();
      byte []content = bos.toByteArray();
      TempBuffer.free(tb);
      
      DataSource dataSource
        = new ByteArrayDataSource(content, attachmentType);
      
      MimeBodyPart pdfBodyPart = new MimeBodyPart();
      pdfBodyPart.setDataHandler(new DataHandler(dataSource));
      pdfBodyPart.setFileName(attachmentName);
      multipart.addBodyPart(pdfBodyPart);
      
      msg.setContent(multipart);

      send(msg);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Sends to a mailbox
   */
  public void send(Message message)
  {
    Transport smtp = null;

    try {
      smtp = _session.getTransport("smtp");

      smtp.connect();

      smtp.send(message, _to);

      log.fine(this + " sent mail to " + _to[0]);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      try {
        if (smtp != null)
          smtp.close();
      } catch (Exception e) {
        log.log(Level.FINER, e.toString(), e);
      }
    }
  }

  public void init()
  {
    if (_toList.size() == 0)
      throw new ConfigException(L.l("mail service requires at least one 'to' address"));

    _to = new Address[_toList.size()];
    _toList.toArray(_to);
    
    _from = new Address[_fromList.size()];
    _fromList.toArray(_from);

    try {
      if (_session == null) {
        _session = Session.getInstance(_properties);
      }

      Transport smtp = _session.getTransport("smtp");

      smtp.close();
    } catch (Exception e) {
      throw ConfigException.wrap(e);
    }
  }

  public String toString()
  {
    return getClass().getSimpleName() + _toList;
  }
}
