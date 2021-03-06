/*******************************************************************************
 * Copyright (c) 2009 Thales Corporate Services SAS                             *
 * Author : Gregory Boissinot                                                   *
 *                                                                              *
 * Permission is hereby granted, free of charge, to any person obtaining a copy *
 * of this software and associated documentation files (the "Software"), to deal*
 * in the Software without restriction, including without limitation the rights *
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell    *
 * copies of the Software, and to permit persons to whom the Software is        *
 * furnished to do so, subject to the following conditions:                     *
 *                                                                              *
 * The above copyright notice and this permission notice shall be included in   *
 * all copies or substantial portions of the Software.                          *
 *                                                                              *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR   *
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,     *
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE  *
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER       *
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,*
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN    *
 * THE SOFTWARE.                                                                *
 *******************************************************************************/

package com.thalesgroup.hudson.plugins.gnat.gnatmake;

import com.thalesgroup.hudson.plugins.gnat.GnatInstallation;
import com.thalesgroup.hudson.plugins.gnat.util.GnatException;
import com.thalesgroup.hudson.plugins.gnat.util.GnatUtil;
import hudson.CopyOnWrite;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.tasks.Builder;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;

/**
 * @author Gregory Boissinot
 */
public class GnatmakeBuilder extends Builder {

    /**
     * Identifies {@link GnatInstallation} to be used.
     */
    private final String gnatName;

    private final String switches;

    private final String fileNames;

    private final String modeSwitches;

    @DataBoundConstructor
    public GnatmakeBuilder(String gnatName, String switches, String fileNames,
                           String modeSwitches) {
        this.gnatName = gnatName;
        this.switches = switches;
        this.fileNames = fileNames;
        this.modeSwitches = modeSwitches;
    }


    @SuppressWarnings("unused")
    public String getSwitches() {
        return switches;
    }

    @SuppressWarnings("unused")
    public String getFileNames() {
        return fileNames;
    }

    @SuppressWarnings("unused")
    public String getModeSwitches() {
        return modeSwitches;
    }

    @SuppressWarnings("unused")
    public String getGnatName() {
        return gnatName;
    }


    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
                           BuildListener listener) throws InterruptedException {
        ArgumentListBuilder args = new ArgumentListBuilder();

        String execPathGnatmake;
        try {
            execPathGnatmake = GnatUtil.getExecutable(DESCRIPTOR.getInstallations(), gnatName, launcher, listener, GnatInstallation.GNAT_TYPE.GNATMAKE);
            args.add(execPathGnatmake);
        } catch (GnatException ge) {
            ge.printStackTrace(listener.fatalError("error"));
            build.setResult(Result.FAILURE);
            return false;
        }

        String normalizedSwitches = switches.replaceAll("[\t\r\n]+", " ");
        String normalizedFileNames = fileNames.replaceAll("[\t\r\n]+", " ");
        String normalizedModeSwitches = modeSwitches.replaceAll("[\t\r\n]+", " ");

        if (normalizedSwitches != null
                && normalizedSwitches.trim().length() != 0) {
            args.addTokenized(normalizedSwitches);
        }

        if (normalizedFileNames == null
                || normalizedFileNames.trim().length() == 0) {
            listener.fatalError("The GNAT file_name field is mandatory.");
            return false;
        }
        args.addTokenized(normalizedFileNames);

        if (normalizedModeSwitches != null
                && normalizedModeSwitches.trim().length() != 0) {
            args.addTokenized(normalizedModeSwitches);
        }

        if (!launcher.isUnix()) {
            // on Windows, executing batch file can't return the correct error
            // code,
            // so we need to wrap it into cmd.exe.
            // double %% is needed because we want ERRORLEVEL to be expanded
            // after
            // batch file executed, not before. This alone shows how broken
            // Windows is...
            args.prepend("cmd.exe", "/C");
            args.add("&&", "exit", "%%ERRORLEVEL%%");
        }

        try {
            int r = launcher.launch().cmds(args).envs(build.getEnvironment(listener))
                    .stdout(listener).pwd(build.getModuleRoot()).join();
            return r == 0;
        } catch (IOException e) {
            Util.displayIOException(e, listener);
            e.printStackTrace(listener.fatalError("command execution failed"));
            return false;
        }
    }

    @Extension
    public static final GnatmakeBuilderDescriptor DESCRIPTOR = new GnatmakeBuilderDescriptor();


    @SuppressWarnings("unused")
    public static final class GnatmakeBuilderDescriptor extends Descriptor<Builder> {

        @CopyOnWrite
        private volatile GnatInstallation[] installations = new GnatInstallation[0];

        private GnatmakeBuilderDescriptor() {
            super(GnatmakeBuilder.class);
            load();
        }

        @Override
        public String getHelpFile() {
            return "/plugin/gnat/gnatmake/help.html";
        }

        public String getDisplayName() {
            return "Invoke gnatmake script";
        }

        public GnatInstallation[] getInstallations() {
            return installations;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) {
            installations = req.bindParametersToList(GnatInstallation.class,
                    "gnat.").toArray(new GnatInstallation[0]);
            save();
            return true;
        }

        /**
         * Checks if the specified Hudson GNATMAKE_HOME is valid.
         *
         * @param value the current gnatmake home
         */
        public FormValidation doCheckGnatmakeHome(@QueryParameter String value) {
            File f = new File(Util.fixNull(value));

            if (!f.isDirectory()) {
                return FormValidation.error(f + " is not a directory");
            }

            if (!new File(f, "bin").exists()
                    && !new File(f, "lib").exists()) {
                return FormValidation.error(f
                        + " doesn't look like a GNAT installation directory");
            }

            return FormValidation.ok();
        }
    }

}
