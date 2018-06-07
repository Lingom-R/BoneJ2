
package org.bonej.wrapperPlugins;

import net.imagej.ImageJ;

/**
 * A main class for quickly testing the wrapper plugins
 *
 * @author Richard Domander
 */
public final class BoneJMain {

	public static void main(final String... args) {
		final ImageJ imageJ = new ImageJ();
		imageJ.launch(args);
	}
}
