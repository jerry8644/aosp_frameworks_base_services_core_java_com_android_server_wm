/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.wm;

import static android.os.Build.IS_USER;

import android.os.RemoteException;
import android.os.ShellCommand;
import android.os.UserHandle;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.android.server.wm.WindowState;
import android.view.Display;

/**
 * ShellCommands for ScrollMonitorService.
 *
 * Use with {@code adb shell cmd powersaving ...}.
 */
public class ScrollMonitorShellCommand extends ShellCommand {

    // Internal service impl -- must perform security checks before touching.
    private final ScrollMonitorService mInternal;

    public ScrollMonitorShellCommand(ScrollMonitorService service) {
        mInternal = service;
    }

    @Override
    public int onCommand(String cmd) {
        if (cmd == null) {
            return handleDefaultCommands(cmd);
        }
        final PrintWriter pw = getOutPrintWriter();
        try {
            switch (cmd) {
                case "test":
                    return runTest(pw);
                case "scalingmode":
                    return runSetScalingMode(pw);
                case "scale":
                    return runSetScale(pw);
                case "outputinfo":
                    return runOutputInfo(pw);
                case "framerate":
                    return runSetFrameRate(pw);
                case "fixedframerate":
                    return runSetFixedFrameRate(pw);
                case "displaymodes":
                    return runDisplayModes(pw);
                case "outputmodel":
                    return runOutputModel(pw);
                default:
                    return handleDefaultCommands(cmd);
            }
        } catch (RemoteException e) {
            pw.println("Remote exception: " + e);
        }
        return -1;
    }

    private int runSetFixedFrameRate(PrintWriter pw) throws RemoteException {
        String arg1 = getNextArg();
        if (arg1.equals("reset")) {
            mInternal.resetModel();
            return 0;
        }
        else {
            try {
                float frameRate = Float.parseFloat(arg1);

                if (mInternal.setFixedFrameRate(frameRate)) {
                    return 0;
                } else {
                    pw.println("invalid args: " + arg1);
                    return -1;
                }
            } catch (NumberFormatException e) {
                pw.println("invalid args: " + arg1);
                return -1;
            }
        }
    }

    private int runOutputModel(PrintWriter pw) throws RemoteException {
        Map<Integer, Float> model = mInternal.getModel();
        if (model != null) {
            for(Map.Entry<Integer, Float> entry : model.entrySet()) {
                pw.println("speed: " + entry.getKey() + " frame rate: " + entry.getValue());
            }
            return 0;
        } else {
            pw.println("no model found!!!");
            return -1;
        }
    }

    private int runSetFrameRate(PrintWriter pw) throws RemoteException {
        String arg1 = getNextArg();
        try {
            float frameRate = Float.parseFloat(arg1);

            if (mInternal.setFrameRate(frameRate)) {
                return 0;
            } else {
                pw.println("invalid args: " + arg1);
                return -1;
            }

        } catch (NumberFormatException e) {
            pw.println("invalid args: " + arg1);
            return -1;
        }
    }

    private int runDisplayModes(PrintWriter pw) throws RemoteException {
        Display.Mode[] modes = mInternal.getDisplayModes();
        WindowState window = mInternal.getFocusedWindow();
        if(modes != null && window != null) {
            pw.println("supported display modes:");
            for(Display.Mode mode : modes) {
                pw.println(mode);
            }
            pw.println("current in-use mode=" + window.mAttrs.preferredDisplayModeId);
            return 0;
        }

        pw.println("no modes found!");
        return -1;
    }

    private int runTest(PrintWriter pw) throws RemoteException {
        String arg = getNextArg();
        if("good".equals(arg)) { 
            pw.println("correct!!!!!!");
            return 0;
        } 

        pw.println("wrong!!!!!!");
        return -1;
    }

    private int runSetScale(PrintWriter pw) throws RemoteException {
        String arg = getNextArg();
        try {
            float scale = Float.parseFloat(arg);
            mInternal.setScaleFactor(scale);
            return 0;
        } catch (NumberFormatException e) {
            pw.println("invalid scale " + arg);
            return -1;
        }
    }

    private int runSetScalingMode(PrintWriter pw) throws RemoteException {
        String arg = getNextArg();
        boolean isResizeEnabled = false;
        if(arg != null) {
            switch (arg) {
                case "on":
                    mInternal.resolutionFR = 0;
                    mInternal.isDesignEnabled = true;
                    mInternal.isResolutionEnable = false;
                    mInternal.isFramerateEnable = false;
                    return 0;
                case "off":
                    mInternal.isDesignEnabled = false;
                    return 0;
                case "framerate":
                    mInternal.resolutionFR = 0;
                    mInternal.isDesignEnabled = true;
                    mInternal.isFramerateEnable = true;
                    mInternal.isResolutionEnable = false;
                    return 0;
                case "resolution":
                    mInternal.resolutionFR = 0;
                    mInternal.isDesignEnabled = true;
                    mInternal.isFramerateEnable = false;
                    mInternal.isResolutionEnable = true;
                    return 0;
                case "re90":
                    mInternal.resolutionFR = 0;
                    mInternal.isDesignEnabled = true;
                    mInternal.isFramerateEnable = false;
                    mInternal.isResolutionEnable = true;
                    return 0;
                case "re60":
                    mInternal.resolutionFR = 1;
                    mInternal.isDesignEnabled = true;
                    mInternal.isFramerateEnable = false;
                    mInternal.isResolutionEnable = true;
                    return 0;
                case "re45":
                    mInternal.resolutionFR = 2;
                    mInternal.isDesignEnabled = true;
                    mInternal.isFramerateEnable = false;
                    mInternal.isResolutionEnable = true;
                    return 0;
                case "re30":
                    mInternal.resolutionFR = 3;
                    mInternal.isDesignEnabled = true;
                    mInternal.isFramerateEnable = false;
                    mInternal.isResolutionEnable = true;
                    return 0;
                case "re22":
                    mInternal.resolutionFR = 4;
                    mInternal.isDesignEnabled = true;
                    mInternal.isFramerateEnable = false;
                    mInternal.isResolutionEnable = true;
                    return 0;
                case "re20":
                    mInternal.resolutionFR = 5;
                    mInternal.isDesignEnabled = true;
                    mInternal.isFramerateEnable = false;
                    mInternal.isResolutionEnable = true;
                    return 0;
                default:
                    pw.println("Bad argument");
                    return -1;
                    
            }
        } else {
            pw.println("Bad argument");
            return -1;
        }
    }

    private int runOutputInfo(PrintWriter pw) throws RemoteException {
        pw.println(mInternal.getServeceInfo());
        return 0;
        
        /*WindowState window = mInternal.getFocusedWindow();
        if(window != null) {
            window.mClient.outputInfo(getOutFileDescriptor());
            return 0;
        } else {
            pw.println("no window found!!!!!!");
            return -1;
        }*/
    }

    

    @Override
    public void onHelp() {
        PrintWriter pw = getOutPrintWriter();
        pw.println("Scroll monitor (window) commands:");
        pw.println("  help");
        pw.println("      Print this help text.");
        pw.println("  test");
        pw.println("      test [good]");
        if (!IS_USER) {
            pw.println("  tracing (start | stop)");
            pw.println("    Start or stop window tracing.");
        }
        FileDescriptor fd = getOutFileDescriptor();
        pw.println(fd);
    }
}
