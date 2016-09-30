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

/**
 * Interface {@code Vault} provides a base interface that all vaults should
 * implement. Vaults host sets of assets. Each vault is capable of hosting
 * a set of assets on one particular type. Vault assets operate in-memory using
 * the vault's inbox.
 * <p>
 * Example: BookVault, which hosts Services of type Book keyed by id of type
 * IdAsset.
 * <blockquote><pre>
 * public interface BookVault implements Vault&lt;IdAsset, Book&gt; {
 * }
 * </pre></blockquote>
 * <p>
 *
 * Vaults provide methods for creating, finding and deleting assets.
 *
 * <b>Creating assets</b><br>
 * Assets can be created explicitly or implicitly. Explicitly created assets
 * require vault and asset provide a matching pair of create methods. Both methods
 * must have the same signature. Note: for the purpose of making sure that signature
 * is the same an interface that defines that signature can be created.
 * Example.
 * <blockquote>
 * <pre>
 * public interface BookVault implements Vault&lt;IdAsset, Book&gt;
 * {
 *   public void create(String title, String author, Result&lt;IdAsset&gt; result);
 *   public void delete(IdAsset id, Result&lt;IdAsset&gt; result);
 * }
 *
 * &#64;Asset
 * public class Book
 * {
 *   &#64;Id
 *   private IdAsset id;
 *   private String title, author;
 *
 *   &#64;Modify
 *   public void create(String title, String author, Result&lt;IdAsset&gt; result)
 *   {
 *     this.title = title;
 *     this.author = author;
 *     result.ok(this.id);
 *   }
 * }
 *
 * public class BookStoreClerk {
 *   &#64;Inject &#64;Service BookVault books;
 *
 *   public IdAsset addBook(String title, String author, Result&lt;IdAsset&gt; result)
 *   {
 *     this.books.create(title, author, result.of());
 *   }
 * }
 * </pre>
 * </blockquote>
 *
 * Methods for creating assets must start with "create" prefix. Delete methods
 * must start with "delete" prefix.
 *
 * <b>Finding assets</b><br>
 * Vault can be queried for assets using finder methods. Finder are defined with
 * a 'find' prefix and adhere to the following patterns:
 * findByField e.g. findByTitle
 * findByField1AndField2 e.g. findByTitleAndAuthor
 * findByField1OrField2 e.g. findByTitleOrAuthor
 *
 * <blockquote>
 *   <pre>
 *     public void findByTitle(String title, Result&lt;Book&gt; result);
 *   </pre>
 * </blockquote>
 *
 * The type of the return value expected is defined by a Result parameter. Expecting
 * one Book (the first found) is specified with Result&lt;Book&gt;. A list of books
 * can be specified with Result&lt;List&lt;Book&gt;&gt;.
 *
 * Example: BookStore with finders
 * <blockquote>
 * <pre>
 * public interface BookVault implements Vault&lt;IdAsset, Book&gt;
 * {
 *   public void create(String title, String author, Result&lt;IdAsset&gt; result);
 *
 *   //return books written by an author
 *   public void findByAuthor(String author, Result&lt;List&lt;Book&gt;&gt; result);
 *
 *   //return book matching a title
 *   public void findByTitle(String title, Result&lt;Book&gt;&gt; result);
 *
 *   //return book matching a title written by an author
 *   public void findByTitleAndAuthor(String title, String author, Result&lt;Book&gt;&gt; result);
 * }
 *
 * public class BookStoreClerk {
 *   &#64;Inject &#64;Service BookVault books;
 *
 *   public IdAsset addBook(String title, String author, Result&lt;IdAsset&gt; result)
 *   {
 *     this.books.create(title, author, result.of());
 *   }
 * }
 * </pre>
 * </blockquote>
 *
 *
 * @param <ID> the type of the id/key
 * @param <T>  the type of the entity
 * @see Asset
 * @see IdAsset
 */
public interface Vault<ID, T>
{
}
