package de.peeeq.wurstscript.tests;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

public class ClassesTests extends PscriptTest {
	
	private static final String TEST_DIR = "./testscripts/valid/classes/";

	@Override protected boolean testOptimizer() {
		return false;
	}
	
	@Test
	public void classes1() throws IOException {
		testAssertOkFile(new File(TEST_DIR + "Classes_1.pscript"), true);
	}

	@Test
	public void classes_construct() throws IOException {
		testAssertOkFile(new File(TEST_DIR + "Classes_construct.pscript"), true);
	}

	@Test
	public void classes_lifecycle() throws IOException {
		testAssertOkFile(new File(TEST_DIR + "Classes_lifecycle.pscript"), true);
	}

	@Test
	public void classes_method_implicit() throws IOException {
		testAssertOkFile(new File(TEST_DIR + "Classes_method_implicit.pscript"), true);
	}

	@Test
	public void classes_method() throws IOException {
		testAssertOkFile(new File(TEST_DIR + "Classes_method.pscript"), true);
	}

	
	
}
