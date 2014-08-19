package apgas.tests;

import static apgas.Constructs.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import apgas.BadPlaceException;
import apgas.Configuration;
import apgas.GlobalRuntime;
import apgas.MultipleException;
import apgas.Place;
import apgas.util.GlobalRef;

@SuppressWarnings("javadoc")
public class ApgasTest {

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    System.setProperty(Configuration.APGAS_PLACES, "4");
    System.setProperty(Configuration.APGAS_SERIALIZATION_EXCEPTION, "true");
    GlobalRuntime.getRuntime();
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
    GlobalRuntime.getRuntime().shutdown();
  }

  @Test
  public void testHere() {
    assertEquals(here(), place(0));
    assertEquals(here(), new Place(0));
  }

  @Test
  public void testPlaces() {
    assertEquals(places().size(), 4);
    for (int i = 0; i < 4; i++) {
      assertEquals(place(i).id, i);
    }
  }

  @Test
  public void testAsyncFinish() {
    final int a[] = new int[1];
    finish(() -> async(() -> a[0] = 42));
    assertEquals(a[0], 42);
  }

  @Test
  public void testGlobalRef() {
    final int a[] = new int[1];
    final GlobalRef<int[]> _a = new GlobalRef<>(a);
    finish(() -> asyncat(place(1),
        () -> asyncat(_a.home(), () -> _a.get()[0] = 42)));
    assertEquals(a[0], 42);
    _a.free();
  }

  @Test
  public void testPlaceLocalHandle() {
    final GlobalRef<Place> plh = new GlobalRef<>(places(), () -> here());
    for (final Place p : places()) {
      assertEquals(at(p, () -> plh.get()), p);
    }
  }

  @Test(expected = BadPlaceException.class)
  public void testBadPlaceException() {
    place(-1);
  }

  @Test(expected = BadPlaceException.class)
  public void testBadPlaceExceptionAsyncAt() {
    asyncat(new Place(-1), () -> {
    });
  }

  @Test(expected = MultipleException.class)
  public void testMultipleException() {
    finish(() -> {
      throw new RuntimeException();
    });
  }

  @Test(expected = MultipleException.class)
  public void testMultipleExceptionAsync() {
    finish(() -> async(() -> {
      throw new RuntimeException();
    }));
  }

  @Test(expected = MultipleException.class)
  public void testMultipleExceptionAsyncAt() {
    finish(() -> asyncat(place(1), () -> {
      throw new RuntimeException();
    }));
  }

  public int fib(int n) {
    if (n < 2) {
      return n;
    }
    final int a[] = new int[2];
    finish(() -> {
      async(() -> a[0] = fib(n - 2));
      a[1] = fib(n - 1);
    });
    return a[0] + a[1];
  }

  @Test
  public void testFib() {
    assertEquals(fib(10), 55);
  }

  @Test(expected = RuntimeException.class)
  public void testSerializationException() throws Throwable {
    try {
      final Object obj = new Object();
      asyncat(place(1), () -> obj.toString());
    } catch (final MultipleException e) {
      assertEquals(e.getSuppressed().length, 1);
      throw e.getSuppressed()[0];
    }
  }

  static class Foo implements java.io.Serializable {
    private static final long serialVersionUID = -3520177294998943335L;

    private void readObject(ObjectInputStream in) throws IOException,
        ClassNotFoundException {
      throw new NotSerializableException(this.getClass().getCanonicalName());
    }
  }

  @Test(expected = NotSerializableException.class)
  public void testDeserializationException() throws Throwable {
    final Object obj = new Foo();
    try {
      finish(() -> asyncat(place(1), () -> obj.toString()));
    } catch (final MultipleException e) {
      assertEquals(e.getSuppressed().length, 1);
      throw e.getSuppressed()[0];
    }
  }

  static class FooException extends RuntimeException {
    private static final long serialVersionUID = 2990207615401829317L;

    final Object obj = new Object();
  }

  @Test(expected = NotSerializableException.class)
  public void testNotSerializableException() throws Throwable {
    try {
      finish(() -> asyncat(place(1), () -> {
        throw new FooException();
      }));
    } catch (final MultipleException e) {
      assertEquals(e.getSuppressed().length, 1);
      throw e.getSuppressed()[0];
    }
  }
}
