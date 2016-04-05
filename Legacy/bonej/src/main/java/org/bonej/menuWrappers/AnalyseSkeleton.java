package org.bonej.menuWrappers;

import org.doube.util.ImageCheck;
import org.doube.util.UsageReporter;

import sc.fiji.analyzeSkeleton.AnalyzeSkeleton_;
import ij.IJ;
import ij.ImagePlus;
import ij.plugin.PlugIn;

/**
 * A simple wrapper plugin to add the AnalyzeSkeleton_ plugin under Plugins>BoneJ menu path
 *
 * @author Richard Domander
 */
public class AnalyseSkeleton implements PlugIn {
    private static final AnalyzeSkeleton_ analyser = new AnalyzeSkeleton_();

    @Override
    public void run(String arg) {
        if (arg.equals("about"))
        {
            // show about dialog and exit
            analyser.setup(arg, null);
            return;
        }
        
        if (!ImageCheck.checkEnvironment()) {
            return;
        }

        final ImagePlus image;

        try {
            image = IJ.getImage();
        } catch (RuntimeException e) {
            return;
        }

        analyser.setup(arg, image);
        analyser.run(null);

        UsageReporter.reportEvent(this).send();
    }
}
