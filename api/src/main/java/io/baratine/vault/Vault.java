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
 * <code>
 * <pre>
 * public interface BookVault implements Vault&lth;IdAsset, Book> {
 *
 * }
 * </pre>
 * </code>
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
 * <code>
 * <pre>
 * public interface BookVault implements Vault&lth;IdAsset, Book>
 * {
 *   public void create(String title, String author, Result&lth;IdAsset> result);
 * }
 *
 * @Asset
 * public class Book
 * {
 *   @Id
 *   private IdAsset id;
 *   private String title, author;
 *
 *   @Modify
 *   public void create(String title, String author, Result&lth;IdAsset> result)
 *   {
 *     this.title = title;
 *     this.author = author;
 *     result.ok(this.id);
 *   }
 * }
 *
 * public class BookStoreClerk {
 *   @Inject @Service BookVault books;
 *
 *   public IdAsset addBook(String title, String author, Result&lth;IdAsset> result)
 *   {
 *     this.books.create(title, author, result.of());
 *   }
 * }
 * </pre>
 * </code>
 *
 * <b>Finding assets</b><br>
 * Vault can be queried for assets using finder methods. Finder are defined with
 * a 'find' prefix and adhere to the following patterns:
 * findByField e.g. findByTitle
 * findByField1AndField2 e.g. findByTitleAndAuthor
 * findByField1OrField2 e.g. findByTitleOrAuthor
 *
 * <code>
 *   <pre>
 *     public void findByTitle(String title, Result&lth;Book> result);
 *   </pre>
 * </code>
 *
 * The type of the return value expected is defined by a Result parameter. Expecting
 * one Book (the first found) is specified with Result&lth;Book>. A list of books
 * can be specified with Result&lth;List&lth;Book>>.
 *
 * Example: BookStore with finders
 * <code>
 * <pre>
 * public interface BookVault implements Vault&lth;IdAsset, Book>
 * {
 *   public void create(String title, String author, Result&lth;IdAsset> result);
 *
 *   //return books written by an author
 *   public void findByAuthor(String author, Result&lth;List&lth;Book>> result);
 *
 *   //return book matching a title
 *   public void findByTitle(String title, Result&lth;Book> result);
 *
 *   //return book matching a title written by an author
 *   public void findByTitleAndAuthor(String title, String author, Result&lth;Book> result);
 * }
 *
 * public class BookStoreClerk {
 *   @Inject @Service BookVault books;
 *
 *   public IdAsset addBook(String title, String author, Result&lth;IdAsset> result)
 *   {
 *     this.books.create(title, author, result.of());
 *   }
 * }
 * </pre>
 * </code>
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
