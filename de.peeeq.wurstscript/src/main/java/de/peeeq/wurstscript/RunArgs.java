package de.peeeq.wurstscript;

import com.google.common.collect.Lists;
import org.eclipse.jdt.annotation.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class RunArgs {


    private final String[] args;
    private final RunOption optionLua;
    private List<String> files = Lists.newArrayList();
    private @Nullable String mapFile = null;
    private @Nullable String outFile = null;
    private @Nullable String workspaceroot = null;
    private @Nullable String inputmap = null;
    private @Nullable int testTimeout = 20;
    private List<RunOption> options = Lists.newArrayList();
    private List<File> libDirs = Lists.newArrayList();
    private RunOption optionHelp;
    private RunOption optionOpt;
    private RunOption optionInline;
    private RunOption optionLocalOptimizations;
    private RunOption optionRuntests;
    private RunOption optionGui;
    private RunOption optionAbout;
    private RunOption optionHotdoc;
    private RunOption optionShowErrors;
    private RunOption optionRunCompileTimeFunctions;
    private RunOption optionStacktraces;
    private RunOption uncheckedDispatch;
    private RunOption optionNodebug;
    private RunOption optionInjectCompiletimeObjects;
    private RunOption optionExtractImports;
    private RunOption optionStartServer;
    private RunOption optionLanguageServer;
    private RunOption optionNoExtractMapScript;
    private RunOption optionFixInstall;
    private RunOption optionCopyMap;
    private RunOption optionDisablePjass;
    private RunOption optionShowVersion;
    private RunOption optionMeasureTimes;
    private RunOption optionHotStartmap;
    private RunOption optionHotReload;
    private RunOption optionTestTimeout;
    private int functionSplitLimit = 10000;

    private RunOption optionBuild;

    public RunArgs with(String... additionalArgs) {
        return new RunArgs(Stream.concat(Stream.of(args), Stream.of(additionalArgs))
                .toArray(String[]::new));
    }

    private static class RunOption {

        final String name;
        final String descr;
        final @Nullable Consumer<String> argHandler;
        boolean isSet;
        RunOption(String name, String descr) {
            this.name = name;
            this.descr = descr;
            this.argHandler = null;
        }

        RunOption(String name, String descr, Consumer<String> argHandler2) {
            this.name = name;
            this.descr = descr;
            this.argHandler = argHandler2;
        }

    }

    public static RunArgs defaults() {
        return new RunArgs();
    }

    public RunArgs(String... args) {
        this.args = args;
        // interpreter
        optionRuntests = addOption("runtests", "Run all test functions found in the scripts.");
        optionTestTimeout = addOptionWithArg("testTimeout", "Timeout in seconds after which tests will be cancelled and considered failed, if they did not yet succeed.", arg -> testTimeout = Integer.parseInt(arg));
        optionRunCompileTimeFunctions = addOption("runcompiletimefunctions", "Run all compiletime functions found in the scripts.");
        optionInjectCompiletimeObjects = addOption("injectobjects", "Injects the objects generated by compiletime functions into the map.");
        // optimization
        optionOpt = addOption("opt", "Enables identifier name compression and whitespace removal.");
        optionInline = addOption("inline", "Enables function inlining.");
        optionLocalOptimizations = addOption("localOptimizations", "Enables local optimizations (cpu and ram extensive, recommended for release)");
        // debug options
        optionStacktraces = addOption("stacktraces", "Generate stacktrace information in the script (useful for debugging).");
        optionNodebug = addOption("nodebug", "Remove all error messages from the script. (Not recommended)");
        uncheckedDispatch = addOption("uncheckedDispatch", "(dangerous) Removes checks from method-dispatch code. With unchecked dispatch "
                + "some programming errors like null-pointer-dereferences or accessing of destroyed objects can no longer be detected. "
                + "It is strongly recommended to not use this option, but it can give some performance benefits.");
        optionMeasureTimes = addOption("measure", "Measure how long each step of the translation process takes.");
        // tools
        optionAbout = addOption("-about", "Show the 'about' window.");
        optionFixInstall = addOption("-fixInstallation", "Checks your wc3 installation and applies compatibility fixes");
        optionCopyMap = addOption("-copyMap", "copies map");
        optionStartServer = addOption("-startServer", "Starts the compilation server.");
        optionHotdoc = addOption("-hotdoc", "Generate hotdoc html documentation.");
        optionShowErrors = addOption("-showerrors", "(currently not implemented.) Show errors generated by last compile.");
        optionExtractImports = addOptionWithArg("-extractImports", "Extract all files from a map into a folder next to the mapp.", arg -> mapFile = arg);
        optionShowVersion = addOption("-version", "Shows the version of the compiler");

        // other
        optionNoExtractMapScript = addOption("noExtractMapScript", "Do not extract the map script from the map and use the one from the Wurst folder instead.");
        optionGui = addOption("gui", "Show a graphical user interface (progress bar and error window).");
        addOptionWithArg("lib", "The next argument should be a library folder which is lazily added to the build.", arg -> libDirs.add(new File(arg)));
        addOptionWithArg("out", "Outputs the compiled script to this file.", arg -> outFile = arg);

        optionLanguageServer = addOption("languageServer", "Starts a language server which can be used by editors to get services "
                + "like code completion, validations, and find declaration. The communication to the language server is via standard input output.");

        optionHelp = addOption("help", "Prints this help message.");
        optionDisablePjass = addOption("noPJass", "Disables PJass checks for the generated code.");
        optionHotStartmap = addOption("hotstart", "Uses Jass Hot Code Reload (JHCR) to start the map.");
        optionHotReload = addOption("hotreload", "Reloads the mapscript after running the map with Jass Hot Code Reload (JHCR).");

        optionBuild = addOption("build", "Builds an output map from the input map and library directories.");
        addOptionWithArg("workspaceroot", "The next argument should be the root folder of the project to build.", arg -> workspaceroot = arg);
        addOptionWithArg("inputmap", "The next argument should be the input map.", arg -> inputmap = arg);
        optionLua = addOption("lua", "Choose Lua as the compilation target.");

        addOptionWithArg("functionSplitLimit", "The maximum number of operations in a function before it is split by the function splitter (used for compiletime functions)",
            s -> functionSplitLimit = Integer.parseInt(s, 10));

        nextArg:
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("-")) {
                for (RunOption o : options) {
                    if (("-" + o.name).equals(a)) {
                        Consumer<String> argHandler = o.argHandler;
                        if (argHandler != null) {
                            i++;
                            argHandler.accept(args[i]);
                        }
                        o.isSet = true;
                        continue nextArg;
                    } else if ((o.argHandler != null && isDoubleArg(a, o))) {
                        continue nextArg;
                    }
                }
                throw new RuntimeException("Unknown option: " + a);
            } else {
                files.add(a);
                if (a.endsWith(".w3x") || a.endsWith(".w3m")) {
                    mapFile = a;
                }
            }
        }

        if (optionHelp.isSet) {
            printHelpAndExit();
        }
    }

    private boolean isDoubleArg(String arg, RunOption option) {
        return (arg.contains(" ") && ("-" + option.name).equals(arg.substring(0, arg.indexOf(" "))));
    }

    private RunOption addOption(String name, String descr) {
        RunOption opt = new RunOption(name, descr);
        options.add(opt);
        return opt;
    }

    private RunOption addOptionWithArg(String name, String descr, Consumer<String> argHandler) {
        RunOption opt = new RunOption(name, descr, argHandler);
        options.add(opt);
        return opt;
    }

    public RunArgs(List<String> runArgs) {
        this(runArgs.toArray(new String[0]));
    }

    public void printHelpAndExit() {
        System.out.println("Usage: ");
        System.out.println("wurst <options> <files>");
        System.out.println();
        System.out.println("Example: wurst -opt common.j Blizzard.j myMap.w3x");
        System.out.println("Compiles the given map with the two script files and optimizations enabled.");
        System.out.println();
        System.out.println("Options:");
        System.out.println();
        for (RunOption opt : options) {
            System.out.println("-" + opt.name);
            System.out.println("	" + opt.descr);
            System.out.println();
        }
    }

    public List<String> getFiles() {
        return files;
    }

    public boolean isOptimize() {
        return optionOpt.isSet;
    }

    public boolean isGui() {
        return optionGui.isSet;
    }

    public @Nullable String getMapFile() {
        return mapFile;
    }

    public void setMapFile(String file) {
        mapFile = file;
    }

    public @Nullable String getOutFile() {
        return outFile;
    }

    public boolean showAbout() {
        return optionAbout.isSet;
    }

    public boolean isFixInstall() {
        return optionFixInstall.isSet;
    }

    public boolean isStartServer() {
        return optionStartServer.isSet;
    }

    public boolean showLastErrors() {
        return optionShowErrors.isSet;
    }

    public boolean isInline() {
        return optionInline.isSet;
    }

    public boolean runCompiletimeFunctions() {
        return optionRunCompileTimeFunctions.isSet;
    }


    public boolean createHotDoc() {
        return optionHotdoc.isSet;
    }

    public boolean isNullsetting() {
        return true;
    }

    public boolean isLocalOptimizations() {
        return optionLocalOptimizations.isSet;
    }

    public boolean isIncludeStacktraces() {
        return optionStacktraces.isSet;
    }

    public boolean isNoDebugMessages() {
        return optionNodebug.isSet;
    }

    public boolean isInjectObjects() {
        return !isHotReload() && optionInjectCompiletimeObjects.isSet;
    }

    public List<File> getAdditionalLibDirs() {
        return Collections.unmodifiableList(libDirs);
    }

    public void addLibs(Set<String> dependencies) {
        for (String dep : dependencies) {
            libDirs.add(new File(dep));
        }
    }

    public void addLibDirs(Set<File> dependencies) {
        libDirs.addAll(dependencies);
    }

    public boolean showHelp() {
        return optionHelp.isSet;
    }

    public boolean isExtractImports() {
        return optionExtractImports.isSet;
    }

    public boolean isShowVersion() {
        return optionShowVersion.isSet;
    }

    public boolean isUncheckedDispatch() {
        return uncheckedDispatch.isSet;
    }

    public boolean isLanguageServer() {
        return optionLanguageServer.isSet;
    }

    public boolean isNoExtractMapScript() {
        return optionNoExtractMapScript.isSet;
    }

    public boolean isCopyMap() {
        return optionCopyMap.isSet;
    }

    public boolean isDisablePjass() {
        return optionDisablePjass.isSet;
    }

    public boolean isRunTests() {
        return optionRuntests.isSet;
    }

    public int getTestTimeout() {
        return testTimeout;
    }

    public boolean isMeasureTimes() {
        return optionMeasureTimes.isSet;
    }

    public boolean isHotStartmap() {
        return optionHotStartmap.isSet;
    }

    public boolean isHotReload() {
        return optionHotReload.isSet;
    }

    public boolean isBuild() {
        return optionBuild.isSet;
    }

    public String getWorkspaceroot() {
        return workspaceroot;
    }

    public String getInputmap() {
        return inputmap;
    }

    public boolean isLua() {
        return optionLua.isSet;
    }


    public int getFunctionSplitLimit() {
        return functionSplitLimit;
    }

}
