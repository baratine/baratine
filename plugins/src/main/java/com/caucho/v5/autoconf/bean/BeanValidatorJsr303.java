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
 * @author Alex Rojkov
 */

package com.caucho.v5.autoconf.bean;

import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;

import com.caucho.v5.beans.BeanValidator;

public class BeanValidatorJsr303 implements BeanValidator
{
  private Logger log
    = Logger.getLogger(com.caucho.v5.beans.BeanValidator.class.getName());

  private final Validator _validator;

  public BeanValidatorJsr303(Validator validator)
  {
    _validator = validator;
  }

  @Override
  public void validate(Object bean)
  {
    final Set<ConstraintViolation<Object>> violations
      = _validator.validate(bean);

    if (violations.size() > 0) {

      IllegalStateException e = new IllegalStateException("invalid bean");

      log.log(Level.FINER, e, () -> loggedErrorMessage(bean, violations));

      throw e;
    }
  }

  private String loggedErrorMessage(Object bean,
                                    Set<ConstraintViolation<Object>> violations)
  {
    StringBuilder builder
      = new StringBuilder("bean validation failed for " + bean);

    for (ConstraintViolation<Object> violation : violations) {
      builder.append("\n\t" + violation);
    }

    return builder.toString();
  }
}
