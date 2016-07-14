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

package io.baratine.vault;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.baratine.service.Service;

/**
 * Annotation {@code Asset} marks Baratine service as an Asset.
 * <p>
 * Assets can take advantage of automated persistence using {@code Vault}.
 * <p>
 * Asset's field values are stored in the database and loaded into the fields
 * of an asset when Asset is instantiated via one of the {@code Vault} methods or
 * with a lookup via {@code Services} e.g. {@code Services.service(Book.class, "asset-id")}
 * <p>
 * {@code }Lifecycle of an asset is managed by a hosting {@code Vault}. Vault makes sure
 * that fields of the asset are loaded at the right time and that asset goes through a
 * correct lifecycle.
 * <p>
 * Asset's lifecycle starts with Vault allocating an object instance (asset) associated with
 * an id. At this point the asset has its fields set to initial values. Next,
 * Vault loads values from the database and initializes asset's fields with the
 * values loaded from the database.
 * <p>
 * After the fields are loaded method marked with {@code @OnInit} annotation is
 * called ( providing that method is not required ). Asset becomes available to
 * serving requests after @OnInit method finishes.
 * <p>
 * New asset, which has no values in the database, needs to go through creation
 * phase. A specialized 'create' method is responsible for creating an asset.
 * This method will set initial values for asset's fields and make asset available
 * for having its methods called.
 * <p>
 * Create methods must start with 'create' e.g. {@code createBook(title, author)} and
 * be annotated with @Modify.
 * <p>
 * <b>Create</b> method on an Asset must have a corresponding <b>create</b> method
 * in the hosting Vault. <b>Create</b> method is always called on a Vault and must
 * return <b>id</b> of newly created asset.
 * <p>
 * Assets marked with {@code @AutoCreate} annotation go through the <b>create</b>
 * phase implicitly.
 * <p>
 * Lifecycle of an asset can be made accessible internally to the asset using
 * a field of type {@code StateAsset}. This field will be managed internally and
 * it's values are meant for reading only.
 * <code>
 * <pre>
 * @Asset
 * public class Book
 * {
 *   @Id
 *   private IdAsset id;
 *
 *   private String title;
 *   private String author;
 *
 *   @Modify
 *   public void create(String title, String author, Result<IdAsset> result) {
 *     this.title = title;
 *     this.author = author;
 *
 *     result.ok(id);
 *   }
 * }
 * </pre>
 * </code>
 *
 * @see Vault
 * @see Id
 * @see IdAsset
 * @see StateAsset
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Service
public @interface Asset
{
  String value() default "";
}
