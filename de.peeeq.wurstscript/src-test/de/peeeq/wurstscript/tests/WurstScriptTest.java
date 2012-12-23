package de.peeeq.wurstscript.tests;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import junit.framework.Assert;

import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

import de.peeeq.wurstscript.Pjass;
import de.peeeq.wurstscript.Pjass.Result;
import de.peeeq.wurstscript.CompiletimeFunctionRunner;
import de.peeeq.wurstscript.RunArgs;
import de.peeeq.wurstscript.WurstCompilerJassImpl;
import de.peeeq.wurstscript.WurstConfig;
import de.peeeq.wurstscript.ast.CompilationUnit;
import de.peeeq.wurstscript.ast.WPackage;
import de.peeeq.wurstscript.ast.WurstModel;
import de.peeeq.wurstscript.attributes.CompileError;
import de.peeeq.wurstscript.gui.WurstGui;
import de.peeeq.wurstscript.gui.WurstGuiCliImpl;
import de.peeeq.wurstscript.intermediateLang.interpreter.ILInterpreter;
import de.peeeq.wurstscript.jassAst.JassProg;
import de.peeeq.wurstscript.jassIm.ImFunction;
import de.peeeq.wurstscript.jassIm.ImProg;
import de.peeeq.wurstscript.jassIm.ImStmt;
import de.peeeq.wurstscript.jassinterpreter.JassInterpreter;
import de.peeeq.wurstscript.jassinterpreter.TestFailException;
import de.peeeq.wurstscript.jassinterpreter.TestSuccessException;
import de.peeeq.wurstscript.jassoptimizer.JassOptimizer;
import de.peeeq.wurstscript.jassoptimizer.JassOptimizerImpl;
import de.peeeq.wurstscript.jassprinter.JassPrinter;
import de.peeeq.wurstscript.translation.imtranslation.FunctionFlag;
import de.peeeq.wurstscript.utils.FileReading;
import de.peeeq.wurstscript.utils.Pair;
import de.peeeq.wurstscript.utils.Utils;

public class WurstScriptTest {

	private static final String TEST_OUTPUT_PATH = "./test-output/";

	
	protected boolean testOptimizer() {
		return true;
	}
	
	static class CU {
		final public String content;
		final public String name;
		public CU(String name, String content) {
			this.name = name;
			this.content = content;
		}
	}
	
	public CU compilationUnit(String name, String ... input) {
		return new CU(name, Utils.join(input, "\n"));
	}
	
	public void testAssertOk(boolean excuteProg, boolean withStdLib, CU ... units) {
		List<File> inputFiles = Collections.emptyList();
		Map<String, Reader> inputs = Maps.newHashMap();
		for (CU cu : units) {
			inputs.put(cu.name, new StringReader(cu.content));
		}
		String name = Utils.getMethodName(2);
		testScript(inputFiles, inputs, name, excuteProg, withStdLib, false);
	}
	
	public void testAssertErrors(String errorMessage, boolean excuteProg, boolean withStdLib, CU ... units) {
		List<File> inputFiles = Collections.emptyList();
		Map<String, Reader> inputs = Maps.newHashMap();
		for (CU cu : units) {
			inputs.put(cu.name, new StringReader(cu.content));
		}
		String name = Utils.getMethodName(2);
		try {
			testScript(inputFiles, inputs, name, excuteProg, withStdLib, false);
			Assert.assertTrue("No errors were discovered", false);
		} catch (CompileError e) {
			Assert.assertTrue(e.getMessage(), e.getMessage().contains(errorMessage));
		}
	}
	
	
	public void testAssertOkLines(boolean executeProg, String ... input) {
		String prog = Utils.join(input, "\n") + "\n";
		testAssertOk(Utils.getMethodName(1), executeProg, prog);
	}
	
	public void testAssertErrorsLines(boolean executeProg, String errorMessage, String ... input) {
		String prog = Utils.join(input, "\n") + "\n";
		testAssertErrors(Utils.getMethodName(1), executeProg, prog, errorMessage);
	}
	
	public void testAssertOk(String name, boolean executeProg, String prog) {
		if (name.length() == 0) {
			name = Utils.getMethodName(1);
		}
		testScript(name, new StringReader(prog), this.getClass().getSimpleName() + "_" + name, executeProg, false);
	}

	public void testAssertOkFile(File file, boolean executeProg) throws IOException {
		Reader reader= FileReading.getFileReader(file);
		testScript(Collections.singleton(file), null, file.getName(), executeProg, false, false);
		reader.close();
	}
	
	public void testAssertOkFileWithStdLib(File file, boolean executeProg) throws IOException {
		Reader reader= FileReading.getFileReader(file);
		testScript(file.getAbsolutePath(), reader, file.getName(), executeProg, true);
		reader.close();
	}
	
	public void testAssertOkLinesWithStdLib(boolean executeProg, String ... input) {
		String prog = Utils.join(input, "\n") + "\n";
		String name = Utils.getMethodName(1);
		testScript(name, new StringReader(prog), this.getClass().getSimpleName() + "_" + name, executeProg, true);
	}
	
	public void testAssertErrorFileWithStdLib(File file, String errorMessage, boolean executeProg) throws IOException {
		Reader reader= FileReading.getFileReader(file);
		try { 
			testScript(file.getAbsolutePath(), reader, file.getName(), executeProg, true);
		} catch (CompileError e) {
			Assert.assertTrue(e.toString(), e.getMessage().contains(errorMessage));
		}
		reader.close();
	}

	public void testAssertErrors(String name, boolean executeProg, String prog, String errorMessage) {
		name = Utils.getMethodName(2);
		try {
			testScript(name, new StringReader(prog), this.getClass().getSimpleName() + "_" + name, executeProg, false);
			Assert.assertTrue("No errors were discovered", false);
		} catch (CompileError e) {
			Assert.assertTrue(e.getMessage(), e.getMessage().toLowerCase().contains(errorMessage.toLowerCase()));
		}
		
		
	}

	protected void testScript(String inputName, Reader input, String name, boolean executeProg, boolean withStdLib) {
		Map<String, Reader> inputs = Maps.newHashMap();
		inputs.put(inputName, input);
		testScript(null, inputs, name, executeProg, withStdLib, false);
	}
	
	protected void testScript(Iterable<File> inputFiles, Map<String, Reader> inputs, String name, boolean executeProg, boolean withStdLib, boolean executeTests) {
		if (inputFiles == null) {
			inputFiles = Collections.emptyList();
		}
		if (inputs == null) {
			inputs = Collections.emptyMap();
		}
		
		boolean success = false;
		WurstGui gui = new WurstGuiCliImpl();
		RunArgs runArgs = new RunArgs(new String[] {
				"-inline", "-opt"
			});
		WurstCompilerJassImpl compiler = new WurstCompilerJassImpl(gui, runArgs);
		compiler.getErrorHandler().enableUnitTestMode();
		WurstConfig.get().setSetting("lib", "../Wurstpack/wurstscript/lib/");
		if (withStdLib) {
			compiler.loadFiles(new File("./resources/common.j"), new File("./resources/blizzard.j"));
		}
		for (File input : inputFiles) {
			compiler.loadFiles(input);
		}
		for (Entry<String, Reader> input : inputs.entrySet()) {
			compiler.loadReader(input.getKey(), input.getValue());
		}
		WurstModel model = compiler.parseFiles();
		
		
		if (gui.getErrorCount() > 0) {
			throw gui.getErrorList().get(0);
		}
		
		
		ImProg imProg = compiler.getImProg();
		writeJassImProg(name, gui, imProg);
		if (executeTests) {
			CompiletimeFunctionRunner cfr = new CompiletimeFunctionRunner(imProg, null, gui, FunctionFlag.IS_TEST);
			cfr.run();
			System.out.println("Successfull tests: " + cfr.getSuccessTests().size());
			int failedTestCount = cfr.getFailTests().size();
			System.out.println("Failed tests: " + failedTestCount);
			if (failedTestCount > 0 ) {
				for (Entry<ImFunction, Pair<ImStmt, String>> e : cfr.getFailTests().entrySet()) {
					Assert.assertFalse(Utils.printElementWithSource(e.getKey().attrTrace()) + " " + e.getValue().getB()
							+ "\n" + "at " + Utils.printElementWithSource(e.getValue().getA().attrTrace()), true);
				}
			} else {
				success = true;
			}
		} if (executeProg) {
			
			try {
				// run the interpreter on the intermediate language
				success = false;
				ILInterpreter interpreter = new ILInterpreter(imProg, gui, null);
				interpreter.executeFunction("main");
			} catch (TestFailException e) {
				throw e;
			} catch (TestSuccessException e)  {
				success = true;
			}
		}
		
		
		JassProg prog = compiler.getProg();
		
		
		if (gui.getErrorCount() > 0) {
			throw gui.getErrorList().get(0);
		}
		Assert.assertNotNull(prog);

		File outputFile = writeJassProg(name, gui, prog);


		// run pjass:
		Result pJassResult = Pjass.runPjass(outputFile);
		System.out.println(pJassResult.getMessage());
		if (!pJassResult.isOk() && !pJassResult.getMessage().equals("IO Exception")) {
			throw new Error(pJassResult.getMessage());
		}

		if (executeProg) {
			try {
				// run the interpreter
				success = false;
				JassInterpreter interpreter = new JassInterpreter();
				interpreter.trace(true);
				interpreter.loadProgram(prog);
				interpreter.executeFunction("main");
			} catch (TestFailException e) {
				throw e;
			} catch (TestSuccessException e)  {
				success = true;
			}
		}

		// run the optimizer:
		System.out.println("optimizer1");
		if (testOptimizer()) {
			
//			System.out.println("optimizer2");
//			JassOptimizer optimizer = new JassOptimizerImpl();
//			System.out.println("optimizer3");
//			try {
//				optimizer.optimize(prog);
//				System.out.println("optimizer4");
//			} catch (FileNotFoundException e) {
//				throw new Error(e);
//			}
			
	
			// write optimized file:
			outputFile = writeJassProg(name+"opt", gui, prog);
	
			// test optimized file with pjass:
			pJassResult = Pjass.runPjass(outputFile);
			System.out.println(pJassResult.getMessage());
			if (!pJassResult.isOk() && !pJassResult.getMessage().equals("IO Exception")) {
				throw new Error("Errors in optimized version: " + pJassResult.getMessage());
			}
		
			if (executeProg) {
				try {
					success = false;
					// run the interpreter with the optimized program
					JassInterpreter interpreter = new JassInterpreter();
					interpreter.trace(true);
					interpreter.loadProgram(prog);
					interpreter.executeFunction("main");
				} catch (TestFailException e) {
					throw e;
				} catch (TestSuccessException e)  {
					success = true;
				}
			}
		}
		
		if (executeProg && !success) {
			throw new Error("Succeed function not called");
		}
	}

	/**
	 * writes a jass prog to a file
	 */
	private File writeJassProg(String name, WurstGui gui, JassProg prog) throws Error {
		File outputFile = new File(TEST_OUTPUT_PATH + name + ".j");
		new File(TEST_OUTPUT_PATH).mkdirs();
		try {
			StringBuilder sb = new StringBuilder();
			new JassPrinter(true).printProg(sb, prog);
			
			Files.write(sb.toString(), outputFile, Charsets.UTF_8);
		} catch (IOException e) {
			throw new Error("IOException, could not write jass file "+ outputFile + "\n"  + gui.getErrors());
		}
		return outputFile;
	}

	/**
	 * writes a jass prog to a file
	 */
	private File writeJassImProg(String name, WurstGui gui, ImProg prog) throws Error {
		File outputFile = new File(TEST_OUTPUT_PATH + name + ".jim");
		new File(TEST_OUTPUT_PATH).mkdirs();
		try {
			StringBuilder sb = new StringBuilder();
			prog.print(sb, 0);
			
			Files.write(sb.toString(), outputFile, Charsets.UTF_8);
		} catch (IOException e) {
			throw new Error("IOException, could not write jass file "+ outputFile + "\n"  + gui.getErrors());
		}
		return outputFile;
	}

	



}
