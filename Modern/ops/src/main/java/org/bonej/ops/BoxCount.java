
package org.bonej.ops;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Spliterator;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractUnaryFunctionOp;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.roi.labeling.BoundingBox;
import net.imglib2.type.BooleanType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.ValuePair;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * An N-dimensional box counting that can be used to estimate the fractal
 * dimension of an interval
 * <p>
 * The algorithm repeatedly lays a fixed grid on the interval, and counts the
 * number of sections that contain foreground. After each step the grid is made
 * finer by a factor of {@link #scaling}. If the objects in the interval are
 * fractal, the proportion of foreground sections should increase as the grid
 * gets finer.
 * </p>
 * <p>
 * Produces a set of points (log(foreground count), -log(section size)) for
 * curve fitting. The slope of the linear function gives the fractal dimension
 * of the interval.
 * </p>
 *
 * @author Richard Domander
 * @author Per Christian Henden & Jens Bache-Wiig - original ImageJ1 code
 */
@Plugin(type = Op.class)
public class BoxCount<B extends BooleanType<B>> extends
	AbstractUnaryFunctionOp<RandomAccessibleInterval<B>, List<ValuePair<DoubleType, DoubleType>>>
{

	/** Starting size of the grid sections in pixels */
	@Parameter(required = false, persist = false)
	private Integer maxSize = 48;

	/** Minimum size of the grid sections in pixels */
	@Parameter(required = false, persist = false)
	private Integer minSize = 6;

	/** Grid downscaling factor */
	@Parameter(required = false, persist = false)
	private Double scaling = 1.2;

	/**
	 * Number of times the grid is moved in each dimension to find the best fit
	 * <p>
	 * The best fitting grid covers the objects in the interval with the least
	 * amount of sections.
	 * </p>
	 * <p>
	 * NB Additional moves multiply algorithm's time complexity by n^d!
	 * </p>
	 */
	@Parameter(required = false, persist = false)
	private Integer gridMoves = 0;

	/**
	 * Counts the number of foreground sections in the interval repeatedly with
	 * different size sections
	 *
	 * @param input an n-dimensional binary interval
	 * @return A list of (log(foreground count), -log(section size))
	 *         {@link ValuePair} objects for curve fitting
	 */
	@Override
	public List<ValuePair<DoubleType, DoubleType>> calculate(
		final RandomAccessibleInterval<B> input)
	{
		final List<ValuePair<DoubleType, DoubleType>> points = new ArrayList<>();
		final int dimensions = input.numDimensions();
		final long[] sizes = new long[dimensions];
		final int numTranslations = 1 + gridMoves;
		input.dimensions(sizes);
		for (long sectionSize = maxSize; sectionSize >= minSize; sectionSize /=
			scaling)
		{
			final long translationAmount = Math.max(1, sectionSize / numTranslations);
			final Stream<long[]> translations = generateTranslations(numTranslations,
				translationAmount, dimensions - 1, new long[dimensions]);
			final LongStream foregroundCounts = countTranslatedGrids(input,
				translations, sizes, sectionSize);
			final long foreground = foregroundCounts.min().orElse(0);
			final double logCount = Math.log(foreground);
			final double logSize = -Math.log(sectionSize);
			final ValuePair<DoubleType, DoubleType> point = new ValuePair<>(
				new DoubleType(logCount), new DoubleType(logSize));
			points.add(point);
		}
		return points;
	}

	/**
	 * Count foreground sections in all grids created from the translations
	 *
	 * @param input N-dimensional binary interval
	 * @param translations Stream of translation coordinates in n-dimensions
	 * @param sizes Sizes of the interval's dimensions in pixels
	 * @param sectionSize Size of a section in the grids
	 * @return Foreground sections counted in each grid
	 */
	private static <B extends BooleanType<B>> LongStream countTranslatedGrids(
		final RandomAccessibleInterval<B> input, final Stream<long[]> translations,
		final long[] sizes, final long sectionSize)
	{
		final int lastDimension = sizes.length - 1;
		final LongType foreground = new LongType();
		final long[] sectionPosition = new long[sizes.length];
		return translations.mapToLong(gridOffset -> {
			foreground.setZero();
			Arrays.fill(sectionPosition, 0);
			countGrid(input, lastDimension, sizes, gridOffset, sectionPosition,
				sectionSize, foreground);
			return foreground.get();
		});
	}

	/**
	 * Creates a {@link net.imglib2.View} of the given grid section in the
	 * interval
	 * <p>
	 * Fits the view inside the bounds of the interval.
	 * </p>
	 *
	 * @param interval An n-dimensional interval with binary elements
	 * @param sizes Sizes of the interval's dimensions
	 * @param coordinates Starting coordinates of the section
	 * @param sectionSize Size of the section (n * n * ... n)
	 * @return A view of the interval spanning n pixels in each dimension from the
	 *         coordinates. Null if view couldn't be set inside the interval
	 */
	private static <B extends BooleanType<B>> IntervalView<B> sectionView(
		final RandomAccessibleInterval<B> interval, final long[] sizes,
		final long[] coordinates, final long sectionSize)
	{
		final int n = sizes.length;
		final long[] startPosition = IntStream.range(0, n).mapToLong(i -> Math.max(
			0, coordinates[i])).toArray();
		final long[] endPosition = IntStream.range(0, n).mapToLong(i -> Math.min(
			(sizes[i] - 1), (coordinates[i] + sectionSize - 1))).toArray();
		final boolean badBox = IntStream.range(0, n).anyMatch(
			d -> (startPosition[d] >= sizes[d]) || (endPosition[d] < 0) ||
				(endPosition[d] < startPosition[d]));
		if (badBox) {
			return null;
		}
		final BoundingBox box = new BoundingBox(n);
		box.update(startPosition);
		box.update(endPosition);
		return Views.offsetInterval(interval, box);
	}

	/** Checks if the view has any foreground elements */
	private static <B extends BooleanType<B>> boolean hasForeground(
		IntervalView<B> view)
	{
		final Spliterator<B> spliterator = view.spliterator();
		return StreamSupport.stream(spliterator, false).anyMatch(BooleanType::get);
	}

	/**
	 * Recursively counts the number of foreground sections in the grid over the
	 * given interval
	 *
	 * @param interval An n-dimensional interval with binary elements
	 * @param dimension Current dimension processed, start from the last
	 * @param sizes Sizes of the interval's dimensions in pixels
	 * @param translation Translation of grid start in each dimension
	 * @param sectionPosition The accumulated position of the current grid section
	 *          (start from [0, 0, ... 0])
	 * @param sectionSize Size of a grid section (n * n * ... n)
	 * @param foreground Number of foreground sections found so far (start from 0)
	 */
	private static <B extends BooleanType<B>> void countGrid(
		final RandomAccessibleInterval<B> interval, final int dimension,
		final long[] sizes, final long[] translation, final long[] sectionPosition,
		final long sectionSize, final LongType foreground)
	{
		for (int p = 0; p < sizes[dimension]; p += sectionSize) {
			sectionPosition[dimension] = translation[dimension] + p;
			if (dimension == 0) {
				final IntervalView<B> box = sectionView(interval, sizes,
					sectionPosition, sectionSize);
				if (box != null && hasForeground(box)) {
					foreground.inc();
				}
			}
			else {
				countGrid(interval, dimension - 1, sizes, translation, sectionPosition,
					sectionSize, foreground);
			}
		}
	}

	/**
	 * Generates t * 2^n translations in n-dimensions
	 * <p>
	 * The generated arrays are coordinates [0, 0, .. 0], [-i, 0, .. 0], [0, -i,
	 * 0, .. 0], .. [-i, -i, .. -i], [-2i, 0, .. 0] .. [-ti, -ti, .. -ti], where
	 * each array has n elements, i = number of pixels translated, and t = number
	 * of translations. If the translations were positive, a part of the interval
	 * would not get inspected, because it always starts from [0, 0, ... 0]. The
	 * order of arrays in the stream is not guaranteed.
	 * </p>
	 *
	 * @param numTranslations Number of translations (1 produces
	 *          Stream.of(long[]{0, 0, .. 0}))
	 * @param amount Number of pixels shifted in translations
	 * @param dimension Current translation dimension (start from last)
	 * @param translation The accumulated position of the current translation
	 *          (start from {0, 0, .. 0})
	 * @return A stream of coordinates of the translations
	 */
	private static Stream<long[]> generateTranslations(final int numTranslations,
		final long amount, final int dimension, final long[] translation)
	{
		Stream<long[]> stream = Stream.empty();
		for (int t = 0; t < numTranslations; t++) {
			translation[dimension] = -t * amount;
			if (dimension == 0) {
				stream = Stream.concat(stream, Stream.of(translation.clone()));
			}
			else {
				final Stream<long[]> subStream = generateTranslations(numTranslations,
					amount, dimension - 1, translation);
				stream = Stream.concat(stream, subStream);

			}
		}
		return stream;
	}
}
