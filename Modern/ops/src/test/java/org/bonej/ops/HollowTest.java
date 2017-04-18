
package org.bonej.ops;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import net.imagej.ImageJ;
import net.imagej.ops.Ops;
import net.imagej.ops.special.function.BinaryFunctionOp;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.hybrid.Hybrids;
import net.imagej.ops.special.hybrid.UnaryHybridCF;
import net.imglib2.FinalDimensions;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.logic.BitType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests for the {@link Hollow} op
 *
 * @author Richard Domander
 */
public class HollowTest {

	private static final ImageJ IMAGE_J = new ImageJ();
	private static BinaryFunctionOp<FinalDimensions, BitType, Img<BitType>> imgCreate;
	private static UnaryHybridCF<RandomAccessibleInterval<BitType>, RandomAccessibleInterval<BitType>> hollow;

	@BeforeClass
	public static void oneTimeSetup() {
		imgCreate = (BinaryFunctionOp) Functions.binary(IMAGE_J.op(),
			Ops.Create.Img.class, Img.class, FinalDimensions.class, new BitType());
		hollow = (UnaryHybridCF) Hybrids.unaryCF(IMAGE_J.op(), Hollow.class,
			RandomAccessibleInterval.class, RandomAccessibleInterval.class);
	}

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
	}

	/** Test basic properties of the op's output */
	@Test
	public void testOutput() throws Exception {
		// SETUP
		final long[] inputDims = { 3, 3, 3 };
		final Img<BitType> img = imgCreate.calculate(new FinalDimensions(
			inputDims));

		// EXECUTE
		final RandomAccessibleInterval<BitType> result = hollow.calculate(img);

		// VERIFY
		assertNotNull(result);
		final long[] outputDims = new long[result.numDimensions()];
		result.dimensions(outputDims);
		assertArrayEquals(inputDims, outputDims);
	}

	/** Test the op with an interval that's full of background elements */
	@Test
	public void testAllBackground() throws Exception {
		// SETUP
		final Img<BitType> img = imgCreate.calculate(new FinalDimensions(3, 3, 3));

		// EXECUTE
		final Img<BitType> result = (Img<BitType>) hollow.calculate(img);

		// VERIFY
		assertEquals("Output should contain no foreground", 0, countForeground(
			result));
	}

	/** Test the op with an interval that's full of foreground elements */
	@Test
	public void testAllForeground() throws Exception {
		// SETUP
		final Img<BitType> img = imgCreate.calculate(new FinalDimensions(3, 3, 3));
		img.forEach(BitType::setOne);

		// EXECUTE
		final Img<BitType> result = (Img<BitType>) hollow.calculate(img);

		// VERIFY
		assertEquals("Output should contain no foreground", 0, countForeground(
			result));
	}

	/** Test the op with a 2x2 square. The square is in the middle of a 4x4 img */
	@Test
	public void testSquare() throws Exception {
		// SETUP
		final Img<BitType> img = imgCreate.calculate(new FinalDimensions(4, 4));
		final IntervalView<BitType> square = Views.offsetInterval(img, new long[] {
			1, 1 }, new long[] { 2, 2 });
		square.cursor().forEachRemaining(BitType::setOne);

		// EXECUTE
		final Img<BitType> result = (Img<BitType>) hollow.calculate(img);

		// VERIFY
		assertEquals("Wrong number of foreground elements in interval", 4,
			countForeground(result));
		final IntervalView<BitType> resultSquare = Views.offsetInterval(result,
			new long[] { 1, 1 }, new long[] { 2, 2 });
		assertTrue("Wrong number of foreground elements in object", allForeground(
			resultSquare));
	}

	/**
	 * Test the op with a 3x3 square with a hole in the middle. The square is in
	 * the middle of a 5x5 img
	 */
	@Test
	public void testHollowSquare() throws Exception {
		// SETUP
		final Img<BitType> img = imgCreate.calculate(new FinalDimensions(5, 5));
		final IntervalView<BitType> square = Views.offsetInterval(img, new long[] {
			1, 1 }, new long[] { 3, 3 });
		square.cursor().forEachRemaining(BitType::setOne);
		final RandomAccess<BitType> access = square.randomAccess();
		access.setPosition(new long[] { 1, 1 });
		access.get().setZero();

		// EXECUTION
		final Img<BitType> result = (Img<BitType>) hollow.calculate(img);

		// VERIFY
		assertEquals("Wrong number of foreground elements in interval", 8,
			countForeground(result));
		final IntervalView<BitType> resultSquare = Views.offsetInterval(result,
			new long[] { 1, 1 }, new long[] { 3, 3 });
		assertEquals("Wrong number of foreground elements in object", 8,
			countForeground(resultSquare));
		assertPositionBackground(result, new long[] { 2, 2 });
	}

	/**
	 * Test the op with a 3x3 square starting from (0,1) in a 5x5 img
	 * 
	 * @see Hollow#excludeEdges
	 * @see #testEdgeSquare()
	 */
	@Test
	public void testEdgeSquare() throws Exception {
		// SETUP
		final Img<BitType> img = imgCreate.calculate(new FinalDimensions(5, 5));
		final IntervalView<BitType> square = Views.offsetInterval(img, new long[] {
			0, 1 }, new long[] { 3, 3 });
		square.cursor().forEachRemaining(BitType::setOne);

		// EXECUTION
		final Img<BitType> result = (Img<BitType>) hollow.calculate(img);

		// VERIFY
		assertEquals("Wrong number of foreground elements in interval", 7,
			countForeground(result));
		final IntervalView<BitType> resultSquare = Views.offsetInterval(result,
			new long[] { 0, 1 }, new long[] { 3, 3 });
		assertEquals("Wrong number of foreground elements in object", 7,
			countForeground(resultSquare));
		assertPositionBackground(result, new long[] { 0, 2 });
		assertPositionBackground(result, new long[] { 1, 2 });
	}

	/**
	 * Test the op with a 3x3 square starting from (0,1) in a 5x5 img without
	 * excluding edges
	 * 
	 * @see Hollow#excludeEdges
	 * @see #testEdgeSquare()
	 */
	@Test
	public void testEdgeSquareExcludeEdgesFalse() throws Exception {
		// SETUP
		final Img<BitType> img = imgCreate.calculate(new FinalDimensions(5, 5));
		final IntervalView<BitType> square = Views.offsetInterval(img, new long[] {
			0, 1 }, new long[] { 3, 3 });
		square.cursor().forEachRemaining(BitType::setOne);

		final Img<BitType> result = (Img<BitType>) IMAGE_J.op().run(Hollow.class,
			Img.class, img, false);

		assertEquals("Wrong number of foreground elements in interval", 8,
			countForeground(result));
		final IntervalView<BitType> resultSquare = Views.offsetInterval(result,
			new long[] { 0, 1 }, new long[] { 3, 3 });
		assertEquals("Wrong number of foreground elements in object", 8,
			countForeground(resultSquare));
		assertPositionBackground(result, new long[] { 1, 2 });
	}

	/**
	 * Test the op with a 3x3x3x3 hypercube. The cube is in the middle of a
	 * 5x5x5x5 img
	 */
	@Test
	public void testHyperCube() throws Exception {
		// SETUP
		final Img<BitType> img = imgCreate.calculate(new FinalDimensions(5, 5, 5,
			5));
		final IntervalView<BitType> hyperCube = Views.offsetInterval(img,
			new long[] { 1, 1, 1, 1 }, new long[] { 3, 3, 3, 3 });
		hyperCube.cursor().forEachRemaining(BitType::setOne);

		// EXECUTE
		final Img<BitType> result = (Img<BitType>) hollow.calculate(img);

		// VERIFY
		assertEquals("Wrong number of foreground elements in interval", 80,
			countForeground(result));
		final IntervalView<BitType> resultHyperCube = Views.offsetInterval(result,
			new long[] { 1, 1, 1, 1 }, new long[] { 3, 3, 3, 3 });
		assertEquals("Wrong number of foreground elements in object", 80,
			countForeground(resultHyperCube));
		assertPositionBackground(result, new long[] { 2, 2, 2, 2 });
	}

	// region -- Helper methods --
	private boolean allForeground(final IterableInterval<BitType> interval) {
		for (final BitType element : interval) {
			if (!element.get()) {
				return false;
			}
		}
		return true;
	}

	private int countForeground(final IterableInterval<BitType> interval) {
		int count = 0;
		for (final BitType element : interval) {
			count = count + element.getInteger();
		}
		return count;
	}

	private void assertPositionBackground(
		final RandomAccessibleInterval<BitType> interval, final long[] position)
	{
		final RandomAccess<BitType> access = interval.randomAccess();
		access.setPosition(position);
		assertFalse("Element should be background", access.get().get());
	}
	// endregion
}
