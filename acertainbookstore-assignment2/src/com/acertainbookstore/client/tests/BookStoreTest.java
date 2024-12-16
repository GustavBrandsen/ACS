package com.acertainbookstore.client.tests;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import java.util.concurrent.*;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.acertainbookstore.business.Book;
import com.acertainbookstore.business.BookCopy;
import com.acertainbookstore.business.SingleLockConcurrentCertainBookStore;
import com.acertainbookstore.business.ImmutableStockBook;
import com.acertainbookstore.business.StockBook;
import com.acertainbookstore.business.TwoLevelLockingConcurrentCertainBookStore;
import com.acertainbookstore.client.BookStoreHTTPProxy;
import com.acertainbookstore.client.StockManagerHTTPProxy;
import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreConstants;
import com.acertainbookstore.utils.BookStoreException;

/**
 * {@link BookStoreTest} tests the {@link BookStore} interface.
 * 
 * @see BookStore
 */
public class BookStoreTest {

	/** The Constant TEST_ISBN. */
	private static final int TEST_ISBN = 3044560;

	/** The Constant NUM_COPIES. */
	private static final int NUM_COPIES = 5;

	/** The local test. */
	private static boolean localTest = true;

	/** Single lock test */
	private static boolean singleLock = true;

	
	/** The store manager. */
	private static StockManager storeManager;

	/** The client. */
	private static BookStore client;

	/**
	 * Sets the up before class.
	 */
	@BeforeClass
	public static void setUpBeforeClass() {
		try {
			String localTestProperty = System.getProperty(BookStoreConstants.PROPERTY_KEY_LOCAL_TEST);
			localTest = (localTestProperty != null) ? Boolean.parseBoolean(localTestProperty) : localTest;
			
			String singleLockProperty = System.getProperty(BookStoreConstants.PROPERTY_KEY_SINGLE_LOCK);
			singleLock = (singleLockProperty != null) ? Boolean.parseBoolean(singleLockProperty) : singleLock;

			if (localTest) {
				System.out.println("singleLock: " + singleLock);
				if (singleLock) {
					SingleLockConcurrentCertainBookStore store = new SingleLockConcurrentCertainBookStore();
					storeManager = store;
					client = store;
				} else {
					TwoLevelLockingConcurrentCertainBookStore store = new TwoLevelLockingConcurrentCertainBookStore();
					storeManager = store;
					client = store;
				}
			} else {
				storeManager = new StockManagerHTTPProxy("http://localhost:8081/stock");
				client = new BookStoreHTTPProxy("http://localhost:8081");
			}

			storeManager.removeAllBooks();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Helper method to add some books.
	 *
	 * @param isbn
	 *            the isbn
	 * @param copies
	 *            the copies
	 * @throws BookStoreException
	 *             the book store exception
	 */
	public void addBooks(int isbn, int copies) throws BookStoreException {
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		StockBook book = new ImmutableStockBook(isbn, "Test of Thrones", "George RR Testin'", (float) 10, copies, 0, 0,
				0, false);
		booksToAdd.add(book);
		storeManager.addBooks(booksToAdd);
	}

	/**
	 * Helper method to get the default book used by initializeBooks.
	 *
	 * @return the default book
	 */
	public StockBook getDefaultBook() {
		return new ImmutableStockBook(TEST_ISBN, "Harry Potter and JUnit", "JK Unit", (float) 10, NUM_COPIES, 0, 0, 0,
				false);
	}

	/**
	 * Method to add a book, executed before every test case is run.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Before
	public void initializeBooks() throws BookStoreException {
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		booksToAdd.add(getDefaultBook());
		storeManager.addBooks(booksToAdd);
	}

	/**
	 * Method to clean up the book store, execute after every test case is run.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@After
	public void cleanupBooks() throws BookStoreException {
		storeManager.removeAllBooks();
	}

	/**
	 * Tests basic buyBook() functionality.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyAllCopiesDefaultBook() throws BookStoreException {
		// Set of books to buy
		Set<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, NUM_COPIES));

		// Try to buy books
		client.buyBooks(booksToBuy);

		List<StockBook> listBooks = storeManager.getBooks();
		assertTrue(listBooks.size() == 1);
		StockBook bookInList = listBooks.get(0);
		StockBook addedBook = getDefaultBook();

		assertTrue(bookInList.getISBN() == addedBook.getISBN() && bookInList.getTitle().equals(addedBook.getTitle())
				&& bookInList.getAuthor().equals(addedBook.getAuthor()) && bookInList.getPrice() == addedBook.getPrice()
				&& bookInList.getNumSaleMisses() == addedBook.getNumSaleMisses()
				&& bookInList.getAverageRating() == addedBook.getAverageRating()
				&& bookInList.getNumTimesRated() == addedBook.getNumTimesRated()
				&& bookInList.getTotalRating() == addedBook.getTotalRating()
				&& bookInList.isEditorPick() == addedBook.isEditorPick());
	}

	/**
	 * Tests that books with invalid ISBNs cannot be bought.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyInvalidISBN() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy a book with invalid ISBN.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, 1)); // valid
		booksToBuy.add(new BookCopy(-1, 1)); // invalid

		// Try to buy the books.
		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();

		// Check pre and post state are same.
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that books can only be bought if they are in the book store.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyNonExistingISBN() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy a book with ISBN which does not exist.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, 1)); // valid
		booksToBuy.add(new BookCopy(100000, 10)); // invalid

		// Try to buy the books.
		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();

		// Check pre and post state are same.
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that you can't buy more books than there are copies.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyTooManyBooks() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy more copies than there are in store.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, NUM_COPIES + 1));

		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that you can't buy a negative number of books.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyNegativeNumberOfBookCopies() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy a negative number of copies.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, -1));

		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that all books can be retrieved.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testGetBooks() throws BookStoreException {
		Set<StockBook> booksAdded = new HashSet<StockBook>();
		booksAdded.add(getDefaultBook());

		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 1, "The Art of Computer Programming", "Donald Knuth",
				(float) 300, NUM_COPIES, 0, 0, 0, false));
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 2, "The C Programming Language",
				"Dennis Ritchie and Brian Kerninghan", (float) 50, NUM_COPIES, 0, 0, 0, false));

		booksAdded.addAll(booksToAdd);

		storeManager.addBooks(booksToAdd);

		// Get books in store.
		List<StockBook> listBooks = storeManager.getBooks();

		// Make sure the lists equal each other.
		assertTrue(listBooks.containsAll(booksAdded) && listBooks.size() == booksAdded.size());
	}

	/**
	 * Tests that a list of books with a certain feature can be retrieved.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testGetCertainBooks() throws BookStoreException {
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 1, "The Art of Computer Programming", "Donald Knuth",
				(float) 300, NUM_COPIES, 0, 0, 0, false));
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 2, "The C Programming Language",
				"Dennis Ritchie and Brian Kerninghan", (float) 50, NUM_COPIES, 0, 0, 0, false));

		storeManager.addBooks(booksToAdd);

		// Get a list of ISBNs to retrieved.
		Set<Integer> isbnList = new HashSet<Integer>();
		isbnList.add(TEST_ISBN + 1);
		isbnList.add(TEST_ISBN + 2);

		// Get books with that ISBN.
		List<Book> books = client.getBooks(isbnList);

		// Make sure the lists equal each other
		assertTrue(books.containsAll(booksToAdd) && books.size() == booksToAdd.size());
	}

	/**
	 * Tests that books cannot be retrieved if ISBN is invalid.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testGetInvalidIsbn() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Make an invalid ISBN.
		HashSet<Integer> isbnList = new HashSet<Integer>();
		isbnList.add(TEST_ISBN); // valid
		isbnList.add(-1); // invalid

		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, -1));

		try {
			client.getBooks(isbnList);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Test 1 test case described in Assignment 2.
	 * Tests that operations that perform conflicting writes to S are atomic.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testCase1() throws BookStoreException, InterruptedException {
		int initialStock = 100; // Set to a sufficient value
		Set<StockBook> initialBooks = new HashSet<>();
		initialBooks.add(new ImmutableStockBook(TEST_ISBN+1, "Test of Thrones", "George RR Testin'", (float) 10, initialStock, 0, 0,0, false));
		storeManager.addBooks(initialBooks);

		Thread bookStoreThread = new Thread(() -> {
            try {
                Set<BookCopy> booksToBuy = new HashSet<>();
                booksToBuy.add(new BookCopy(TEST_ISBN+1, NUM_COPIES));
                client.buyBooks(booksToBuy);
            } catch (BookStoreException ex) {
                ;
            }
        });

		Thread storeManagerThread = new Thread(() -> {
            try {
                Set<BookCopy> booksToCopy = new HashSet<>();
                booksToCopy.add(new BookCopy(TEST_ISBN+1, NUM_COPIES));
                storeManager.addCopies(booksToCopy);
            } catch (BookStoreException ex) {
                ;
            }
        });

		bookStoreThread.start();
		storeManagerThread.start();

		bookStoreThread.join();
		storeManagerThread.join();

		List<StockBook> finalBooks = storeManager.getBooks();
		for (StockBook book : finalBooks) {
			if (book.getISBN() == TEST_ISBN+1) {
				assertEquals(initialStock, book.getNumCopies());
			}
		}
	}

	/**
	 * Test 2 test case described in Assignment 2.
	 * Tests that snapshots returned by getBooks are consistent.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testCase2() throws BookStoreException, InterruptedException {
		int TEST_ISBN1 = TEST_ISBN+2;
		int TEST_ISBN2 = TEST_ISBN+3;
		int INITIAL_STOCK = 10;
		int NUM_ITERATIONS = 1000;
		int NUM_COPIES_TO_BUY = 5;

		try {
			Set<StockBook> initialBooks = new HashSet<>();
			initialBooks.add(new ImmutableStockBook(TEST_ISBN1, "Test of Thrones", "George RR Testin'", 10.0f, INITIAL_STOCK, 0, 0, 0, false));
			initialBooks.add(new ImmutableStockBook(TEST_ISBN2, "Test of Thrones", "George RR Testin'", 12.0f, INITIAL_STOCK, 0, 0, 0, false));
			storeManager.addBooks(initialBooks);
		} catch (BookStoreException ex) {
			;
		}

		// Create threads
		Thread client1 = new Thread(() -> {
			try {
				Set<BookCopy> booksToBuy = new HashSet<>();
				booksToBuy.add(new BookCopy(TEST_ISBN1, NUM_COPIES_TO_BUY));
				booksToBuy.add(new BookCopy(TEST_ISBN2, NUM_COPIES_TO_BUY));

				while (!Thread.currentThread().isInterrupted()) {
					client.buyBooks(booksToBuy);
					storeManager.addCopies(booksToBuy);
				}
			} catch (BookStoreException ex) {
				;
			}
		});

		Thread client2 = new Thread(() -> {
			try {
				for (int i = 0; i < NUM_ITERATIONS; i++) {
					List<StockBook> booksSnapshot = storeManager.getBooks();

					boolean isConsistent = booksSnapshot.stream().allMatch(book -> {
						int stock = book.getNumCopies();
						return stock == INITIAL_STOCK || stock == INITIAL_STOCK - NUM_COPIES_TO_BUY;
					});

					if (!isConsistent) {
						throw new AssertionError("Inconsistent snapshot observed");
					}
				}
			} catch (BookStoreException ex) {
				;
			}
		});

		client1.start();
		client2.start();

		client2.join();

		client1.interrupt();
		client1.join();
	}

	/**
	 * This tests that it locks whenever a client tries to buy a book, as two clients are trying to buy more than half
	 * the stock each. One of the clients are thus unable to buy their request
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testCase3() throws BookStoreException, InterruptedException {
		int initialStock = 10;
		int NUM_COPIES_TO_BUY = 8;
		Set<StockBook> initialBooks = new HashSet<>();
		initialBooks.add(new ImmutableStockBook(TEST_ISBN+4, "Test of Thrones", "George RR Testin'", (float) 10, initialStock, 0, 0,0, false));
		storeManager.addBooks(initialBooks);

		Thread c1 = new Thread(() -> {
			try {
				Set<BookCopy> booksToBuy = new HashSet<>();
				booksToBuy.add(new BookCopy(TEST_ISBN+4, NUM_COPIES_TO_BUY));
				client.buyBooks(booksToBuy);
			} catch (BookStoreException ex) {
				;
			}
		});

		Thread c2 = new Thread(() -> {
			try {
				Set<BookCopy> booksToBuy = new HashSet<>();
				booksToBuy.add(new BookCopy(TEST_ISBN+4, NUM_COPIES_TO_BUY));
				client.buyBooks(booksToBuy);
			} catch (BookStoreException ex) {
				;
			}
		});

		c1.start();
		c2.start();

		c1.join();
		c2.join();

		List<StockBook> finalBooks = storeManager.getBooks();
		for (StockBook book : finalBooks) {
			if (book.getISBN() == TEST_ISBN+4) {
				assertTrue(book.getNumCopies() >= 0);
			}
		}
	}

	/**
	 * Tests if two clients can copy books at the same time.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testCase4() throws BookStoreException, InterruptedException {
		int initialStock = 10;
		int NUM_COPIES_TO_COPY = 8;
		Set<StockBook> initialBooks = new HashSet<>();
		initialBooks.add(new ImmutableStockBook(TEST_ISBN+5, "Test of Thrones", "George RR Testin'", (float) 10, initialStock, 0, 0,0, false));
		storeManager.addBooks(initialBooks);

		Thread c1 = new Thread(() -> {
			try {
				Set<BookCopy> booksToCopy = new HashSet<>();
				booksToCopy.add(new BookCopy(TEST_ISBN+5, NUM_COPIES_TO_COPY));
				storeManager.addCopies(booksToCopy);
			} catch (BookStoreException ex) {
				;
			}
		});

		Thread c2 = new Thread(() -> {
			try {
				Set<BookCopy> booksToCopy = new HashSet<>();
				booksToCopy.add(new BookCopy(TEST_ISBN+5, NUM_COPIES_TO_COPY));
				storeManager.addCopies(booksToCopy);
			} catch (BookStoreException ex) {
				;
			}
		});

		c1.start();
		c2.start();

		c1.join();
		c2.join();

		List<StockBook> finalBooks = storeManager.getBooks();
		for (StockBook book : finalBooks) {
			if (book.getISBN() == TEST_ISBN+5) {
				assertEquals((initialStock+NUM_COPIES_TO_COPY+NUM_COPIES_TO_COPY), book.getNumCopies());
			}
		}
	}


	/**
	 * Tear down after class.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@AfterClass
	public static void tearDownAfterClass() throws BookStoreException {
		storeManager.removeAllBooks();

		if (!localTest) {
			((BookStoreHTTPProxy) client).stop();
			((StockManagerHTTPProxy) storeManager).stop();
		}
	}
}
